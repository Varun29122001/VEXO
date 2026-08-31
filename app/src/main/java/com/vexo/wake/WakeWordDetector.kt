package com.vexo.wake

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.vexo.models.RemoteModel
import com.vexo.models.WakeWordFiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

private const val TAG = "VexoWakeWord"
private const val SAMPLE_RATE = 16_000
private const val FEATURE_DIM = 80
private const val THREADS = 1

/** 100 ms per read: small enough to stay responsive, large enough to keep wake-ups cheap. */
private const val CHUNK_SAMPLES = SAMPLE_RATE / 10

/** How much recent audio to keep so the wake utterance can be checked against a voice profile. */
private const val RING_SECONDS = 2

/** How often to re-check whether the overlay has finished with the microphone. */
private const val PAUSE_POLL_MILLIS = 250L

/**
 * A fixed-capacity ring of recent audio. Writes overwrite the oldest samples; [snapshot] returns
 * them in chronological order.
 *
 * Pure and allocation-light, so the wrap-around logic is unit testable without an audio device.
 */
internal class AudioRing(private val capacity: Int) {

    private val buffer = FloatArray(capacity)
    private var writeIndex = 0
    private var filled = 0

    val size: Int get() = filled

    fun write(samples: FloatArray, count: Int = samples.size) {
        for (index in 0 until count) {
            buffer[writeIndex] = samples[index]
            writeIndex = (writeIndex + 1) % capacity
            if (filled < capacity) filled++
        }
    }

    fun snapshot(): FloatArray {
        val out = FloatArray(filled)
        // When not yet full the data starts at 0; once wrapped, the oldest sample sits at writeIndex.
        val start = if (filled < capacity) 0 else writeIndex
        for (index in 0 until filled) {
            out[index] = buffer[(start + index) % capacity]
        }
        return out
    }

    fun clear() {
        writeIndex = 0
        filled = 0
    }
}

/**
 * Listens continuously for a wake phrase using sherpa-onnx's streaming keyword spotter.
 *
 * The spotter is a 3.3 M parameter zipformer transducer, small enough to run on one thread. Audio is
 * read from `VOICE_RECOGNITION` at 16 kHz mono and fed straight in; nothing is written to disk and
 * nothing leaves the device. A rolling two-second window is retained purely so that the utterance
 * which triggered a wake-up can be compared against an enrolled voice profile.
 *
 * Not thread safe, and [listen] blocks until its coroutine is cancelled.
 */
class WakeWordDetector private constructor(
    private val spotter: KeywordSpotter,
) {

    private val ring = AudioRing(SAMPLE_RATE * RING_SECONDS)

    /**
     * Reads audio until the calling coroutine is cancelled, invoking [onDetection] with the phrase
     * and the recent audio window each time a wake phrase fires.
     *
     * While [sessionActive] reports true the microphone is released entirely, because the platform
     * recogniser needs it for the request. The spotter itself stays loaded, so resuming costs
     * nothing but re-opening `AudioRecord`.
     *
     * Requires `RECORD_AUDIO`; the caller is responsible for holding it.
     */
    suspend fun listen(
        sessionActive: () -> Boolean,
        onDetection: suspend (phrase: String, audio: FloatArray) -> Unit,
    ) {
        val stream = spotter.createStream()
        val chunk = FloatArray(CHUNK_SAMPLES)
        var recorder: AudioRecord? = null

        fun release() {
            recorder?.let {
                runCatching { it.stop() }
                it.release()
                Log.i(TAG, "Released the microphone")
            }
            recorder = null
        }

        try {
            while (currentCoroutineContext().isActive) {
                if (sessionActive()) {
                    // The overlay owns the microphone; stand down until it is finished.
                    if (recorder != null) {
                        release()
                        spotter.reset(stream)
                        ring.clear()
                    }
                    delay(PAUSE_POLL_MILLIS)
                    continue
                }

                if (recorder == null) {
                    recorder = openRecorder() ?: return
                    Log.i(TAG, "Listening for a wake phrase")
                }

                // Read through a local: `recorder` is mutated by release(), so it cannot smart cast.
                val active = recorder ?: continue
                val read = active.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                ring.write(chunk, read)
                stream.acceptWaveform(chunk.copyOf(read), SAMPLE_RATE)

                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                }

                val keyword = spotter.getResult(stream).keyword
                if (keyword.isNotBlank()) {
                    Log.i(TAG, "Wake phrase detected: $keyword")
                    val audio = ring.snapshot()
                    // Reset before the callback so the phrase cannot immediately re-fire.
                    spotter.reset(stream)
                    ring.clear()
                    // Hand the microphone over before the overlay opens, so the ordering is
                    // deterministic rather than a race with the recogniser.
                    release()
                    onDetection(keyword.trim(), audio)
                }
            }
        } finally {
            release()
            stream.release()
            Log.i(TAG, "Stopped listening")
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferBytes = maxOf(minBuffer, CHUNK_SAMPLES * 4 * 4)

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord did not initialise; wake word is unavailable")
            recorder.release()
            return null
        }
        recorder.startRecording()
        return recorder
    }

    fun close() = spotter.release()

    companion object {

        /**
         * Opens the spotter in [directory], writing the wake phrases to [keywordsFile] first.
         * Throws if the native session cannot be created.
         */
        fun create(
            directory: File,
            keywordsFile: File,
            model: RemoteModel.Archive,
        ): WakeWordDetector {
            keywordsFile.parentFile?.mkdirs()
            keywordsFile.writeText(WakeWords.keywordsFileContent())

            val startedAt = System.nanoTime()
            val spotter = KeywordSpotter(
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(
                        sampleRate = SAMPLE_RATE,
                        featureDim = FEATURE_DIM,
                    ),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(directory, WakeWordFiles.ENCODER).absolutePath,
                            decoder = File(directory, WakeWordFiles.DECODER).absolutePath,
                            joiner = File(directory, WakeWordFiles.JOINER).absolutePath,
                        ),
                        tokens = File(directory, WakeWordFiles.TOKENS).absolutePath,
                        modelType = "zipformer2",
                        numThreads = THREADS,
                        debug = false,
                        provider = "cpu",
                    ),
                    keywordsFile = keywordsFile.absolutePath,
                )
            )
            Log.i(
                TAG,
                "loaded ${model.id} in ${(System.nanoTime() - startedAt) / 1_000_000}ms",
            )
            return WakeWordDetector(spotter)
        }
    }
}

package com.vexo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

private const val TAG = "VexoNeuralTts"
private const val TOKENS_NAME = "tokens.txt"
private const val ESPEAK_DIR_NAME = "espeak-ng-data"
private const val BYTES_PER_FLOAT = 4
private const val MAX_THREADS = 4

/** Matches the rate the platform engine was configured with, so the two paths sound alike. */
private const val SPEECH_RATE = 1.05f

/** Playback buffer, as a fraction of a second of audio. */
private const val BUFFER_SECONDS = 0.25f

/**
 * On-device neural speech synthesis over a piper VITS model, via sherpa-onnx.
 *
 * Synthesis is done in one shot rather than streamed into the player. VEXO's sentences are short —
 * the longest is about two and a half seconds — so the extra wait before audio starts is small,
 * and in exchange the real-time factor is directly measurable and a sentence can never begin
 * playing before it is known to have synthesised.
 *
 * Not thread safe: the underlying `OfflineTts` holds a single native session. Callers must
 * serialise [speak], and must not call it on the main thread.
 */
class NeuralTextToSpeech private constructor(
    private val engine: OfflineTts,
    private val speakers: Int,
    private val audioManager: AudioManager,
) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * The platform TTS engine negotiates audio focus on its own; an `AudioTrack` does not, so
     * without this VEXO would talk over whatever is already playing instead of ducking it.
     */
    private val focus = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    /** Speaks [text] using [speakerId], clamped to what the pack actually contains. */
    fun speak(text: String, speakerId: Int) {
        val speaker = speakerId.coerceIn(0, maxOf(0, speakers - 1))
        val startedAt = System.nanoTime()
        val audio = engine.generate(text = text, sid = speaker, speed = SPEECH_RATE)
        val synthesisMillis = (System.nanoTime() - startedAt) / 1_000_000

        if (audio.samples.isEmpty()) {
            Log.w(TAG, "Model returned no audio for: $text")
            return
        }

        val durationMillis = audio.samples.size * 1000L / audio.sampleRate
        var peak = 0f
        for (sample in audio.samples) {
            val magnitude = if (sample < 0f) -sample else sample
            if (magnitude > peak) peak = magnitude
        }
        Log.i(
            TAG,
            "synthesised ${audio.samples.size} samples " +
                "(${durationMillis}ms audio) in ${synthesisMillis}ms " +
                "rtf=${"%.3f".format(synthesisMillis.toDouble() / durationMillis)} " +
                "peak=${"%.3f".format(peak)} speaker=$speaker for \"$text\"",
        )

        if (peak == 0f) {
            Log.w(TAG, "Synthesised audio is silent")
        }

        play(audio.samples, audio.sampleRate, durationMillis)
    }

    private fun play(samples: FloatArray, sampleRate: Int, durationMillis: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val preferred = (sampleRate * BUFFER_SECONDS).toInt() * BYTES_PER_FLOAT
        val bufferBytes = if (minBuffer > 0) maxOf(minBuffer, preferred) else preferred

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val granted = audioManager.requestAudioFocus(focus) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            // Speaking anyway beats staying silent; the answer is the whole point of the app.
            Log.w(TAG, "Audio focus denied; playing without it")
        }

        try {
            track.play()
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            awaitDrain(track, samples.size, durationMillis)
            track.stop()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Playback failed", error)
        } finally {
            track.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    /**
     * [AudioTrack.write] returns once the samples are buffered, not once they are audible, so
     * releasing immediately would truncate the tail. Wait for the playback head to reach the end,
     * bounded by the clip's own duration so a stalled track cannot hang the caller.
     */
    private fun awaitDrain(track: AudioTrack, totalFrames: Int, durationMillis: Long) {
        val deadline = System.nanoTime() + (durationMillis + 1_000L) * 1_000_000L
        while (track.playbackHeadPosition < totalFrames && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
    }

    fun close() {
        engine.release()
    }

    companion object {

        /**
         * Opens the model in [directory]. Throws if the native session cannot be created, which is
         * the caller's signal to stay on the platform engine.
         */
        fun create(context: Context, directory: File, model: VoiceModel): NeuralTextToSpeech {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(directory, model.weightsFileName).absolutePath,
                        tokens = File(directory, TOKENS_NAME).absolutePath,
                        // piper voices phonemise through espeak-ng, so lexicon stays empty.
                        dataDir = File(directory, ESPEAK_DIR_NAME).absolutePath,
                    ),
                    numThreads = threads,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            )

            val startedAt = System.nanoTime()
            val engine = OfflineTts(config = config)
            val loadMillis = (System.nanoTime() - startedAt) / 1_000_000

            val speakers = engine.numSpeakers()
            Log.i(
                TAG,
                "loaded ${model.id} in ${loadMillis}ms " +
                    "(threads=$threads, speakers=$speakers, sampleRate=${engine.sampleRate()})",
            )
            return NeuralTextToSpeech(
                engine = engine,
                speakers = speakers,
                audioManager = context.getSystemService(AudioManager::class.java),
            )
        }
    }
}

package com.vexo.speaker

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TAG = "VexoVoiceRecorder"
private const val SAMPLE_RATE = 16_000
private const val CHUNK_SAMPLES = SAMPLE_RATE / 10

/**
 * Records short fixed-length clips at 16 kHz mono, for voice enrolment.
 *
 * Separate from [com.vexo.wake.WakeWordDetector] because that one streams indefinitely into the
 * keyword spotter, whereas enrolment wants one bounded clip handed back in full. Both open the
 * microphone, so only one may run at a time — the caller stops the wake service first.
 */
class VoiceRecorder {

    /**
     * Records for [millis] and returns the samples, or null if the microphone was unavailable.
     * Requires `RECORD_AUDIO`; the caller is responsible for holding it.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(millis: Long): FloatArray? = withContext(Dispatchers.IO) {
        val wanted = (SAMPLE_RATE * millis / 1000).toInt()
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
            Log.w(TAG, "AudioRecord did not initialise; is the microphone in use?")
            recorder.release()
            return@withContext null
        }

        val samples = FloatArray(wanted)
        var filled = 0
        try {
            recorder.startRecording()
            while (filled < wanted && currentCoroutineContext().isActive) {
                val read = recorder.read(
                    samples,
                    filled,
                    minOf(CHUNK_SAMPLES, wanted - filled),
                    AudioRecord.READ_BLOCKING,
                )
                if (read <= 0) break
                filled += read
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        if (filled == 0) return@withContext null
        Log.i(TAG, "Recorded $filled samples (${filled * 1000L / SAMPLE_RATE}ms)")
        samples.copyOf(filled)
    }
}

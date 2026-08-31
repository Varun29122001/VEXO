package com.vexo.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** Lowest and highest RMS values reported by the platform recogniser, in dB. */
private const val RMS_FLOOR = -2f
private const val RMS_CEILING = 10f

/**
 * Captures a single spoken utterance. The returned flow emits amplitude updates while the user
 * speaks, then exactly one [SpeechEvent.Transcript] or [SpeechEvent.Failed] before completing.
 */
class SpeechRecognitionManager(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Failed("Speech recognition is unavailable on this device"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rmsdB: Float) {
                val normalised = (rmsdB - RMS_FLOOR) / (RMS_CEILING - RMS_FLOOR)
                trySend(SpeechEvent.AudioLevel(normalised.coerceIn(0f, 1f)))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                if (text == null) {
                    trySend(SpeechEvent.Failed("Didn't catch that"))
                } else {
                    trySend(SpeechEvent.Transcript(text))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(describe(error)))
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
        )

        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a network connection"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy"
        else -> "Speech recognition failed"
    }
}

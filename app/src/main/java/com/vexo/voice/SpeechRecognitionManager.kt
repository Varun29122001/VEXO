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
import java.util.Locale

/** Lowest and highest RMS values reported by the platform recogniser, in dB. */
private const val RMS_FLOOR = -2f
private const val RMS_CEILING = 10f

/** Deliberately generous: the surface animates in while the recogniser is already listening. */
private const val COMPLETE_SILENCE_MILLIS = 2000
private const val POSSIBLY_COMPLETE_SILENCE_MILLIS = 2000
private const val MINIMUM_UTTERANCE_MILLIS = 6000

/**
 * Captures a single spoken utterance. The returned flow emits amplitude updates while the user
 * speaks, then exactly one [SpeechEvent.Transcript] or [SpeechEvent.Failed] before completing.
 *
 * Partial results are retained: platform recognisers frequently report a usable partial and then
 * fail with ERROR_NO_MATCH, so the partial is preferred over discarding the utterance.
 */
class SpeechRecognitionManager(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Failed("Speech recognition is unavailable on this device", -1))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var lastPartial: String? = null

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rmsdB: Float) {
                val normalised = (rmsdB - RMS_FLOOR) / (RMS_CEILING - RMS_FLOOR)
                trySend(SpeechEvent.AudioLevel(normalised.coerceIn(0f, 1f)))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstTranscript()?.let { lastPartial = it }
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstTranscript() ?: lastPartial
                if (text == null) {
                    trySend(SpeechEvent.Failed("I didn't catch that", SpeechRecognizer.ERROR_NO_MATCH))
                } else {
                    trySend(SpeechEvent.Transcript(text))
                }
                close()
            }

            override fun onError(error: Int) {
                val salvaged = lastPartial
                if (salvaged != null) {
                    trySend(SpeechEvent.Transcript(salvaged))
                } else {
                    trySend(SpeechEvent.Failed(describe(error), error))
                }
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(recognitionIntent())

        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            COMPLETE_SILENCE_MILLIS,
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            POSSIBLY_COMPLETE_SILENCE_MILLIS,
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            MINIMUM_UTTERANCE_MILLIS,
        )
    }

    private fun Bundle?.firstTranscript(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull { it.isNotBlank() }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "I need a network connection to understand you"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I don't have microphone access"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy"
        SpeechRecognizer.ERROR_AUDIO -> "I couldn't record any audio"
        SpeechRecognizer.ERROR_CLIENT -> "The recogniser rejected the request"
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            "The speech service is unavailable"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "This language isn't available for speech recognition"
        else -> "Speech recognition failed"
    }
}

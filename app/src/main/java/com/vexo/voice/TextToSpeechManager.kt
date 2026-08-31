package com.vexo.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

private const val UTTERANCE_ID = "vexo"

/**
 * Application-scoped speech output. Initialisation is asynchronous, so an utterance requested
 * before the engine is ready is held and spoken once it initialises.
 *
 * Speech is fire and forget: the engine lives on the application, so it keeps talking after the
 * assistant surface closes.
 */
class TextToSpeechManager(context: Context) {

    @Volatile
    private var ready = false

    @Volatile
    private var pending: String? = null

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status -> handleInit(status) }
    }

    fun speak(text: String) {
        if (ready) say(text) else pending = text
    }

    private fun handleInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine?.language = Locale.getDefault()
            pending?.let { say(it) }
        }
        pending = null
    }

    private fun say(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }
}

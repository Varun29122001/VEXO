package com.vexo.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

private const val UTTERANCE_ID = "vexo"
private const val PITCH = 1.0f
private const val SPEECH_RATE = 1.05f

/**
 * Alias voices named "<locale>-language" delegate to whatever the system default is, which is
 * often a network voice. Excluding them forces selection of a concrete offline voice.
 */
private const val LEGACY_ALIAS_FEATURE = "legacySetLanguageVoice"

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
            engine?.let { tts ->
                tts.language = Locale.getDefault()
                selectVoice(tts)?.let { tts.voice = it }
                tts.setPitch(PITCH)
                tts.setSpeechRate(SPEECH_RATE)
            }
            pending?.let { say(it) }
        }
        pending = null
    }

    /**
     * Picks the best installed offline voice for the current language so VEXO sounds the same on
     * every device and keeps working without a network. Falls back to the engine default.
     */
    private fun selectVoice(tts: TextToSpeech): Voice? {
        val language = Locale.getDefault().language
        return tts.voices
            ?.asSequence()
            ?.filter { it.locale.language == language }
            ?.filterNot { it.isNetworkConnectionRequired }
            ?.filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
            ?.filterNot { LEGACY_ALIAS_FEATURE in it.features }
            ?.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
            ?.firstOrNull()
    }

    private fun say(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }
}

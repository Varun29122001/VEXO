package com.vexo.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "VexoPlatformTts"
private const val UTTERANCE_ID = "vexo"
private const val PITCH = 1.0f
private const val SPEECH_RATE = 1.05f

/**
 * Alias voices named "<locale>-language" delegate to whatever the system default is, which is
 * often a network voice. Excluding them forces selection of a concrete offline voice.
 */
private const val LEGACY_ALIAS_FEATURE = "legacySetLanguageVoice"

/**
 * Speech output through the platform engine. This is VEXO's fallback: it answers until the neural
 * voice pack has been installed, and whenever the neural path is unavailable or fails.
 *
 * Initialisation is asynchronous, so [speak] suspends until the engine is ready rather than
 * dropping or queuing the utterance behind a flag.
 */
class PlatformTextToSpeech(context: Context) {

    /** Completes with whether the engine initialised; never completes exceptionally. */
    private val initialised = CompletableDeferred<Boolean>()

    /** Completion of the utterance currently in flight, if any. */
    private val inFlight = AtomicReference<CompletableDeferred<Unit>?>(null)

    private var engine: TextToSpeech? = null

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = settle()

        @Deprecated("Superseded by onError(String, int), but still abstract.")
        override fun onError(utteranceId: String?) = settle()

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "Utterance failed with code $errorCode")
            settle()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = settle()

        private fun settle() {
            inFlight.getAndSet(null)?.complete(Unit)
        }
    }

    init {
        engine = TextToSpeech(context.applicationContext) { status -> handleInit(status) }
    }

    /**
     * Speaks [text] and suspends until the engine reports the utterance finished, so a caller can
     * keep the process in the foreground for as long as VEXO is talking. Returns immediately if the
     * engine never initialised.
     */
    suspend fun speakAndAwait(text: String) {
        if (!initialised.await()) {
            Log.w(TAG, "Platform engine unavailable; dropping: $text")
            return
        }
        val completion = CompletableDeferred<Unit>()
        // A previous utterance is about to be flushed, so release anyone waiting on it.
        inFlight.getAndSet(completion)?.complete(Unit)
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        completion.await()
    }

    private fun handleInit(status: Int) {
        val ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine?.let { tts ->
                tts.language = Locale.getDefault()
                selectVoice(tts)?.let { tts.voice = it }
                tts.setPitch(PITCH)
                tts.setSpeechRate(SPEECH_RATE)
                tts.setOnUtteranceProgressListener(progress)
            }
        } else {
            Log.w(TAG, "Platform engine failed to initialise (status $status)")
        }
        initialised.complete(ready)
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
}

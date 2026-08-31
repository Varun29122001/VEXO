package com.vexo.voice

import android.content.Context
import android.util.Log
import com.vexo.models.ModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "VexoTts"

/**
 * How long [speak] will wait for the neural engine to finish loading before giving up and
 * answering through the platform engine. Warm-up starts in `Application.onCreate` and overlaps the
 * two-to-six second listening window, so in practice this budget is never spent.
 */
private const val WARM_UP_BUDGET_MILLIS = 1_500L

/**
 * Application-scoped speech output, preferring an on-device neural voice and degrading to the
 * platform engine.
 *
 * The neural pack is ~78 MiB, so it cannot be a precondition for answering: the first run (and any
 * run where the pack is missing, the load fails, or synthesis throws) is answered by
 * [PlatformTextToSpeech] while the download proceeds in the background. From the next launch
 * onwards the neural voice is used.
 *
 * One consequence of moving synthesis in-process: unlike the platform engine, which hands the
 * utterance to a system service that keeps talking after VEXO exits, neural audio is rendered by
 * VEXO's own `AudioTrack`. [speak] therefore returns a [Job] that completes when the audio has
 * finished, and `MainActivity` holds the surface open until then so the process cannot be frozen
 * mid-sentence.
 */
class TextToSpeechManager(
    context: Context,
    private val store: ModelStore,
    private val model: VoiceModel = VoiceModel.LibriTtsR,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val platform = PlatformTextToSpeech(appContext)

    /** Resolves to the neural engine, or null when this launch must use the platform engine. */
    private val warmUp = CompletableDeferred<NeuralTextToSpeech?>()

    /** `OfflineTts` wraps one native session, so utterances are serialised. */
    private val synthesis = Mutex()

    init {
        scope.launch { prepare() }
    }

    /**
     * Speaks [text] and returns the job doing so. The job completes when the audio has finished,
     * which lets the caller keep the surface (and therefore the process) alive until VEXO has
     * stopped talking.
     *
     * The job belongs to an application-scoped scope, so abandoning it does not cancel speech.
     */
    fun speak(text: String): Job = scope.launch {
        val engine = withTimeoutOrNull(WARM_UP_BUDGET_MILLIS) { warmUp.await() }
        if (engine == null) {
            platform.speakAndAwait(text)
            return@launch
        }
        synthesis.withLock {
            runCatching { engine.speak(text) }.getOrElse { error ->
                Log.w(TAG, "Neural synthesis failed; falling back to the platform engine", error)
                platform.speakAndAwait(text)
            }
        }
    }

    private suspend fun prepare() {
        if (store.isInstalled(model.pack)) {
            val engine = runCatching {
                NeuralTextToSpeech.create(appContext, store.dir(model.pack), model)
            }
                .onFailure { Log.w(TAG, "Could not open the neural voice pack", it) }
                .getOrNull()
            warmUp.complete(engine)
            return
        }

        // Nothing to load this time round: answer through the platform engine.
        warmUp.complete(null)

        if (!store.isUnmetered()) {
            Log.i(TAG, "Voice pack absent, and the network is metered or down; not downloading")
            return
        }
        Log.i(TAG, "Downloading voice pack ${model.id} (${model.pack.bytes} bytes)")
        runCatching { store.install(model.pack) { fraction -> reportProgress(fraction) } }
            .onSuccess { Log.i(TAG, "Voice pack ready; the next launch will speak neurally") }
            .onFailure { Log.w(TAG, "Voice pack download failed; staying on the platform engine", it) }
    }

    private var lastLoggedDecile = -1

    private fun reportProgress(fraction: Float) {
        val decile = (fraction * 10).toInt()
        if (decile != lastLoggedDecile) {
            lastLoggedDecile = decile
            Log.i(TAG, "Voice pack download ${decile * 10}%")
        }
    }
}

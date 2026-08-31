package com.vexo.speaker

import android.util.Log
import com.vexo.models.ModelStore
import com.vexo.models.VexoModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "VexoSpeakerGate"

/** Outcome of an enrolment attempt, so the UI can say something useful about a failure. */
sealed interface EnrolResult {
    data class Enrolled(val profile: VoiceProfile) : EnrolResult
    data object ModelUnavailable : EnrolResult
    data object NoUsableAudio : EnrolResult
}

/**
 * Coordinates the speaker embedding model, the stored profile, and verification.
 *
 * Deliberately conservative about saying "not you": [isEnrolledSpeaker] returns null whenever it
 * cannot form an opinion — model missing, no profile, audio too short — and callers treat null as
 * "don't block". A wake word that silently stops working is worse than one that occasionally
 * answers the wrong person, because this is personalisation and not a security boundary.
 */
class SpeakerGate(
    private val modelStore: ModelStore,
    private val profileStore: VoiceProfileStore,
) {

    private val model = VexoModels.SpeakerEmbedding
    private val lock = Mutex()

    @Volatile
    private var verifier: SpeakerVerifier? = null

    fun profile(): VoiceProfile? = profileStore.load()

    fun deleteProfile() = profileStore.delete()

    /** Downloads the embedding model if needed. Returns whether it is now usable. */
    suspend fun prepare(onProgress: (Float) -> Unit = {}): Boolean = lock.withLock {
        if (verifier != null) return@withLock true

        if (!modelStore.isInstalled(model)) {
            if (!modelStore.isUnmetered()) {
                Log.i(TAG, "Speaker model missing and the network is metered")
                return@withLock false
            }
            val installed = runCatching { modelStore.install(model, onProgress) }
                .onFailure { Log.w(TAG, "Speaker model download failed", it) }
            if (installed.isFailure) return@withLock false
        }

        verifier = withContext(Dispatchers.Default) {
            runCatching { SpeakerVerifier.create(modelStore.dir(model), model) }
                .onFailure { Log.w(TAG, "Could not open the speaker model", it) }
                .getOrNull()
        }
        verifier != null
    }

    /** Records [takes] as the enrolled voice, replacing any existing profile. */
    suspend fun enrol(takes: List<FloatArray>): EnrolResult {
        if (!prepare()) return EnrolResult.ModelUnavailable
        val engine = verifier ?: return EnrolResult.ModelUnavailable

        val profile = lock.withLock {
            withContext(Dispatchers.Default) { engine.enrol(takes) }
        } ?: return EnrolResult.NoUsableAudio

        profileStore.save(profile)
        return EnrolResult.Enrolled(profile)
    }

    /**
     * Whether [audio] sounds like the enrolled voice. Null means no opinion — see the class note on
     * why that is treated as "allow".
     */
    suspend fun isEnrolledSpeaker(audio: FloatArray): Boolean? {
        val stored = profileStore.load() ?: return null
        if (!prepare()) return null
        val engine = verifier ?: return null

        val similarity = lock.withLock {
            withContext(Dispatchers.Default) { engine.similarityTo(stored, audio) }
        } ?: return null

        val matches = similarity >= SPEAKER_MATCH_THRESHOLD
        Log.i(
            TAG,
            "speaker similarity=${"%.3f".format(similarity)} " +
                "threshold=$SPEAKER_MATCH_THRESHOLD match=$matches",
        )
        return matches
    }

    /** Similarity of [audio] to the stored profile, for showing a score in settings. */
    suspend fun similarity(audio: FloatArray): Float? {
        val stored = profileStore.load() ?: return null
        if (!prepare()) return null
        val engine = verifier ?: return null
        return lock.withLock {
            withContext(Dispatchers.Default) { engine.similarityTo(stored, audio) }
        }
    }
}

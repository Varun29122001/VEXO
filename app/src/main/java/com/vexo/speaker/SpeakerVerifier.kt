package com.vexo.speaker

import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.vexo.models.RemoteModel
import java.io.File

private const val TAG = "VexoSpeaker"
private const val SAMPLE_RATE = 16_000
private const val THREADS = 2

/**
 * Cosine similarity above which two utterances are treated as the same speaker.
 *
 * Deliberately conservative. CAM++ embeddings for the same speaker typically land well above this;
 * the cost of a false accept here is only that VEXO answers someone else, and the cost of a false
 * reject is that the wake word is ignored, so a middling threshold is the wrong trade in neither
 * direction. This has not been tuned against real recordings — see the README.
 */
const val SPEAKER_MATCH_THRESHOLD = 0.5f

/** Minimum audio needed before an embedding is worth computing. */
private const val MIN_SAMPLES = SAMPLE_RATE / 2

/**
 * Turns 16 kHz mono PCM into speaker embeddings, and compares them against an enrolled profile.
 *
 * Not thread safe: the extractor wraps a single native session, so callers must serialise use.
 */
class SpeakerVerifier private constructor(
    private val extractor: SpeakerEmbeddingExtractor,
) {

    /**
     * Computes an embedding for one utterance, or null if the audio is too short or the extractor
     * declines it. [samples] must be mono 16 kHz floats in `-1..1`.
     */
    fun embed(samples: FloatArray): FloatArray? {
        if (samples.size < MIN_SAMPLES) {
            Log.i(TAG, "Ignoring ${samples.size} samples: below the ${MIN_SAMPLES} minimum")
            return null
        }
        return runCatching {
            val stream = extractor.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.inputFinished()
            if (!extractor.isReady(stream)) {
                Log.w(TAG, "Extractor not ready after ${samples.size} samples")
                stream.release()
                return null
            }
            val embedding = extractor.compute(stream)
            stream.release()
            normalise(embedding)
        }.onFailure { Log.w(TAG, "Embedding failed", it) }.getOrNull()
    }

    /**
     * Builds a profile from several enrolment takes. Returns null if none of them produced an
     * embedding.
     */
    fun enrol(takes: List<FloatArray>): VoiceProfile? {
        val embeddings = takes.mapNotNull { embed(it) }
        if (embeddings.isEmpty()) return null
        return VoiceProfile(averageEmbedding(embeddings), embeddings.size)
    }

    /** Similarity of [samples] to [profile], or null if no embedding could be computed. */
    fun similarityTo(profile: VoiceProfile, samples: FloatArray): Float? {
        val embedding = embed(samples) ?: return null
        return cosineSimilarity(profile.embedding, embedding)
    }

    fun close() = extractor.release()

    companion object {

        /** Throws if the native session cannot be created, which the caller should treat as "off". */
        fun create(directory: File, model: RemoteModel.SingleFile): SpeakerVerifier {
            val startedAt = System.nanoTime()
            val extractor = SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = File(directory, model.fileName).absolutePath,
                    numThreads = THREADS,
                    debug = false,
                    provider = "cpu",
                )
            )
            Log.i(
                TAG,
                "loaded ${model.id} in ${(System.nanoTime() - startedAt) / 1_000_000}ms " +
                    "(dim=${extractor.dim()})",
            )
            return SpeakerVerifier(extractor)
        }
    }
}

package com.vexo.speaker

import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.sqrt

private const val TAG = "VexoVoiceProfile"
private const val FILE_NAME = "voice-profile.bin"
private const val FORMAT_VERSION = 1

/**
 * Averages [embeddings] element-wise and returns a unit-length vector.
 *
 * Enrolment records several utterances; averaging their embeddings before normalising is the
 * standard way to build a speaker centroid that is less sensitive to any single noisy take.
 *
 * Pure, so the maths is unit testable independently of the native extractor.
 */
internal fun averageEmbedding(embeddings: List<FloatArray>): FloatArray {
    require(embeddings.isNotEmpty()) { "Cannot average an empty list of embeddings" }
    val dimension = embeddings.first().size
    require(embeddings.all { it.size == dimension }) { "Embeddings have differing dimensions" }

    val total = FloatArray(dimension)
    for (embedding in embeddings) {
        for (index in 0 until dimension) total[index] += embedding[index]
    }
    for (index in 0 until dimension) total[index] /= embeddings.size
    return normalise(total)
}

/** Scales [embedding] to unit length. A zero vector is returned unchanged. */
internal fun normalise(embedding: FloatArray): FloatArray {
    var sumOfSquares = 0.0
    for (value in embedding) sumOfSquares += value.toDouble() * value
    val magnitude = sqrt(sumOfSquares).toFloat()
    if (magnitude == 0f) return embedding

    val result = FloatArray(embedding.size)
    for (index in embedding.indices) result[index] = embedding[index] / magnitude
    return result
}

/**
 * Cosine similarity of two embeddings, in `-1..1`. Inputs need not be normalised.
 * Returns `0` if either vector is all zeros.
 */
internal fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
    require(first.size == second.size) { "Embeddings have differing dimensions" }
    var dot = 0.0
    var firstMagnitude = 0.0
    var secondMagnitude = 0.0
    for (index in first.indices) {
        dot += first[index].toDouble() * second[index]
        firstMagnitude += first[index].toDouble() * first[index]
        secondMagnitude += second[index].toDouble() * second[index]
    }
    if (firstMagnitude == 0.0 || secondMagnitude == 0.0) return 0f
    return (dot / (sqrt(firstMagnitude) * sqrt(secondMagnitude))).toFloat()
}

/** An enrolled voice: the averaged embedding plus how many takes went into it. */
data class VoiceProfile(
    val embedding: FloatArray,
    val sampleCount: Int,
) {
    // Generated equals/hashCode would compare the array by identity.
    override fun equals(other: Any?): Boolean =
        other is VoiceProfile &&
            sampleCount == other.sampleCount &&
            embedding.contentEquals(other.embedding)

    override fun hashCode(): Int = 31 * embedding.contentHashCode() + sampleCount
}

/**
 * Stores the single enrolled voice profile in `filesDir`.
 *
 * This is biometric-adjacent data, so it stays in app-private storage, is never uploaded, and can be
 * deleted outright from the settings screen. It is not a credential — see the README on why speaker
 * verification is treated as personalisation rather than authentication.
 */
class VoiceProfileStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun exists(): Boolean = file.isFile

    fun load(): VoiceProfile? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val version = input.readInt()
                require(version == FORMAT_VERSION) { "Unsupported profile version $version" }
                val sampleCount = input.readInt()
                val dimension = input.readInt()
                require(dimension in 1..8192) { "Implausible embedding dimension $dimension" }
                val embedding = FloatArray(dimension) { input.readFloat() }
                VoiceProfile(embedding, sampleCount)
            }
        }.onFailure { Log.w(TAG, "Could not read voice profile; ignoring it", it) }
            .getOrNull()
    }

    fun save(profile: VoiceProfile) {
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        DataOutputStream(temporary.outputStream().buffered()).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeInt(profile.sampleCount)
            output.writeInt(profile.embedding.size)
            for (value in profile.embedding) output.writeFloat(value)
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
        Log.i(TAG, "Saved voice profile (${profile.embedding.size}d, ${profile.sampleCount} takes)")
    }

    fun delete() {
        if (file.delete()) Log.i(TAG, "Deleted voice profile")
    }
}

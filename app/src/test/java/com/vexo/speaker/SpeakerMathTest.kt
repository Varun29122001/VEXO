package com.vexo.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SpeakerMathTest {

    @Test
    fun `normalise produces a unit vector`() {
        val unit = normalise(floatArrayOf(3f, 4f))
        assertEquals(0.6f, unit[0], 1e-6f)
        assertEquals(0.8f, unit[1], 1e-6f)
    }

    @Test
    fun `normalise leaves a zero vector alone`() {
        val zero = normalise(floatArrayOf(0f, 0f))
        assertEquals(0f, zero[0], 0f)
        assertEquals(0f, zero[1], 0f)
    }

    @Test
    fun `average of identical embeddings is that embedding normalised`() {
        val single = floatArrayOf(1f, 1f)
        val averaged = averageEmbedding(listOf(single, single, single))
        val expected = 1f / sqrt(2f)
        assertEquals(expected, averaged[0], 1e-6f)
        assertEquals(expected, averaged[1], 1e-6f)
    }

    @Test
    fun `average sits between its inputs`() {
        val averaged = averageEmbedding(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(averaged[0], averaged[1], 1e-6f)
    }

    @Test
    fun `cosine similarity is one for parallel and zero for orthogonal vectors`() {
        assertEquals(1f, cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-6f)
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(-1f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-6f)
    }

    @Test
    fun `cosine similarity of a zero vector is zero rather than undefined`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
    }

    @Test
    fun `a different speaker scores below the match threshold`() {
        // Stand-in for two embeddings pointing in largely different directions.
        val me = normalise(floatArrayOf(1f, 0.1f, 0f))
        val someoneElse = normalise(floatArrayOf(0.1f, 1f, 0f))
        assertTrue(cosineSimilarity(me, someoneElse) < SPEAKER_MATCH_THRESHOLD)
    }

    @Test
    fun `averaging rejects mismatched dimensions`() {
        val error = runCatching {
            averageEmbedding(listOf(floatArrayOf(1f, 2f), floatArrayOf(1f)))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

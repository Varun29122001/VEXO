package com.vexo.wake

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {

    @Test
    fun `ring returns what was written when not yet full`() {
        val ring = AudioRing(8)
        ring.write(floatArrayOf(1f, 2f, 3f))
        assertEquals(3, ring.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), ring.snapshot(), 0f)
    }

    @Test
    fun `ring keeps the most recent samples in order once wrapped`() {
        val ring = AudioRing(4)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f))
        assertEquals(4, ring.size)
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f), ring.snapshot(), 0f)
    }

    @Test
    fun `ring honours a partial write count`() {
        val ring = AudioRing(4)
        ring.write(floatArrayOf(1f, 2f, 9f, 9f), count = 2)
        assertArrayEquals(floatArrayOf(1f, 2f), ring.snapshot(), 0f)
    }

    @Test
    fun `clear empties the ring`() {
        val ring = AudioRing(4)
        ring.write(floatArrayOf(1f, 2f))
        ring.clear()
        assertEquals(0, ring.size)
        assertArrayEquals(floatArrayOf(), ring.snapshot(), 0f)
    }

    @Test
    fun `every wake phrase has tokens and none is blank`() {
        assertEquals(4, WakeWords.phrases.size)
        WakeWords.phrases.forEach { (phrase, tokens) ->
            assertTrue("phrase is blank", phrase.isNotBlank())
            assertTrue("tokens for '$phrase' are blank", tokens.isNotBlank())
            // Sentencepiece marks word starts with U+2581; a tokenisation without it is wrong.
            assertTrue("tokens for '$phrase' lack a word-start marker", tokens.contains('\u2581'))
        }
    }

    @Test
    fun `keywords file has one line per phrase and a trailing newline`() {
        val content = WakeWords.keywordsFileContent()
        assertTrue(content.endsWith("\n"))
        assertEquals(WakeWords.phrases.size, content.trim().lines().size)
    }

    @Test
    fun `every phrase tokenises the vexo stem the same way`() {
        // All four share "VE X O"; if one drifts, the spotter silently stops matching it.
        WakeWords.phrases.forEach { (phrase, tokens) ->
            assertTrue("'$phrase' is missing the vexo stem", tokens.endsWith("VE X O"))
        }
    }
}

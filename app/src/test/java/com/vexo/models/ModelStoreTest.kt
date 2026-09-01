package com.vexo.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelStoreTest {

    @Test
    fun `strips the archives top level directory`() {
        assertEquals(
            "tokens.txt",
            packRelativePath("vits-piper-en_US-libritts_r-medium/tokens.txt"),
        )
        assertEquals(
            "espeak-ng-data/phontab",
            packRelativePath("vits-piper-en_US-libritts_r-medium/espeak-ng-data/phontab"),
        )
    }

    @Test
    fun `skips the top level directory entry itself`() {
        assertNull(packRelativePath("vits-piper-en_US-libritts_r-medium"))
        assertNull(packRelativePath("vits-piper-en_US-libritts_r-medium/"))
    }

    @Test
    fun `rejects traversal outside the pack`() {
        assertNull(packRelativePath("pack/../../../etc/passwd"))
        assertNull(packRelativePath("pack/.."))
        assertNull(packRelativePath("pack/nested/../../escape"))
    }

    @Test
    fun `normalises redundant separators and current directory segments`() {
        assertEquals("a/b", packRelativePath("pack/a//b"))
        assertEquals("a/b", packRelativePath("pack/./a/./b"))
        assertEquals("tokens.txt", packRelativePath("pack\\tokens.txt"))
    }

    @Test
    fun `single file models require only their own file`() {
        assertEquals(
            listOf("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"),
            VexoModels.SpeakerEmbedding.required,
        )
    }
}

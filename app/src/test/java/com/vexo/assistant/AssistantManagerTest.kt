package com.vexo.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantManagerTest {

    private val manager = AssistantManager()

    @Test
    fun `starts idle and silent`() {
        assertEquals(AssistantState.Idle, manager.state.value)
        assertEquals(0f, manager.audioLevel.value, 0f)
    }

    @Test
    fun `transition updates state`() {
        manager.transitionTo(AssistantState.Listening)
        assertEquals(AssistantState.Listening, manager.state.value)
    }

    @Test
    fun `audio level is clamped`() {
        manager.updateAudioLevel(2.5f)
        assertEquals(1f, manager.audioLevel.value, 0f)

        manager.updateAudioLevel(-1f)
        assertEquals(0f, manager.audioLevel.value, 0f)
    }

    @Test
    fun `reset clears state and level`() {
        manager.transitionTo(AssistantState.Executing)
        manager.updateAudioLevel(0.7f)

        manager.reset()

        assertEquals(AssistantState.Idle, manager.state.value)
        assertEquals(0f, manager.audioLevel.value, 0f)
    }
}

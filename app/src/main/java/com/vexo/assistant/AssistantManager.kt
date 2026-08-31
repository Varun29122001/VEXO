package com.vexo.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the assistant lifecycle. Voice, action and UI layers observe
 * [state] and [audioLevel] instead of talking to each other directly.
 */
class AssistantManager {

    private val mutableState = MutableStateFlow(AssistantState.Idle)
    private val mutableAudioLevel = MutableStateFlow(0f)

    val state: StateFlow<AssistantState> = mutableState.asStateFlow()

    /** Normalised 0..1 microphone amplitude, consumed by the assistant surface. */
    val audioLevel: StateFlow<Float> = mutableAudioLevel.asStateFlow()

    fun transitionTo(next: AssistantState) {
        mutableState.value = next
    }

    fun updateAudioLevel(level: Float) {
        mutableAudioLevel.value = level.coerceIn(0f, 1f)
    }

    fun reset() {
        mutableState.value = AssistantState.Idle
        mutableAudioLevel.value = 0f
    }
}

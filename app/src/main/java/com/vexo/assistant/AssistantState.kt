package com.vexo.assistant

/** Lifecycle of a single assistant interaction. */
enum class AssistantState {
    Idle,
    Listening,
    Processing,
    Responding
}

package com.vexo.assistant

/** Lifecycle of a single assistant interaction: capture speech, resolve it, run the action. */
enum class AssistantState {
    Idle,
    Listening,
    Processing,
    Executing
}

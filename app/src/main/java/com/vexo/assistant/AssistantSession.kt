package com.vexo.assistant

import com.vexo.actions.ActionManager
import com.vexo.actions.ActionResult
import com.vexo.actions.CommandParser
import com.vexo.voice.SpeechEvent
import com.vexo.voice.SpeechRecognitionManager

/**
 * Runs one interaction end to end: capture speech, resolve it to a command, execute it.
 * Drives [AssistantManager] so the surface reflects progress.
 */
class AssistantSession(
    private val speech: SpeechRecognitionManager,
    private val actions: ActionManager,
    private val assistant: AssistantManager,
) {

    suspend fun run(): ActionResult {
        assistant.transitionTo(AssistantState.Listening)

        var transcript: String? = null
        var failure: SpeechEvent.Failed? = null

        speech.listen().collect { event ->
            when (event) {
                is SpeechEvent.AudioLevel -> assistant.updateAudioLevel(event.level)
                is SpeechEvent.Transcript -> transcript = event.text
                is SpeechEvent.Failed -> failure = event
            }
        }
        assistant.updateAudioLevel(0f)

        val spoken = transcript ?: return ActionResult.Failed(
            spoken = failure?.reason ?: "I didn't hear anything",
            diagnostic = failure?.let { "${it.reason} (recogniser code ${it.code})" },
        )

        assistant.transitionTo(AssistantState.Processing)
        val command = CommandParser.parse(spoken)

        assistant.transitionTo(AssistantState.Executing)
        return actions.execute(command, heard = spoken)
    }
}

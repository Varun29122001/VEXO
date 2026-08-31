package com.vexo.voice

sealed interface SpeechEvent {

    /** Normalised 0..1 microphone amplitude, used to drive the orb. */
    data class AudioLevel(val level: Float) : SpeechEvent

    data class Transcript(val text: String) : SpeechEvent

    data class Failed(val reason: String, val code: Int) : SpeechEvent
}

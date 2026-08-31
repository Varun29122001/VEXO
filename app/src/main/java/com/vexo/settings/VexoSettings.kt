package com.vexo.settings

import android.content.Context
import com.vexo.voice.VoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "vexo.settings"
private const val KEY_WAKE_WORD = "wake_word_enabled"
private const val KEY_REQUIRE_ENROLLED_VOICE = "require_enrolled_voice"
private const val KEY_SPEAKER_ID = "neural_speaker_id"

/**
 * The handful of things a user can change. Both default to off: VEXO does not start listening in
 * the background, or restrict who it answers, unless asked to.
 */
class VexoSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableWakeWordEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_WAKE_WORD, false)
    )
    private val mutableRequireEnrolledVoice = MutableStateFlow(
        prefs.getBoolean(KEY_REQUIRE_ENROLLED_VOICE, false)
    )
    private val mutableSpeakerId = MutableStateFlow(
        prefs.getInt(KEY_SPEAKER_ID, VoiceOption.default.speakerId)
    )

    /** Whether the always-listening foreground service should run. */
    val wakeWordEnabled: StateFlow<Boolean> = mutableWakeWordEnabled.asStateFlow()

    /** Whether a wake word from an unrecognised voice should be ignored. */
    val requireEnrolledVoice: StateFlow<Boolean> = mutableRequireEnrolledVoice.asStateFlow()

    /** Which speaker of the multi-speaker neural pack to synthesise with. */
    val speakerId: StateFlow<Int> = mutableSpeakerId.asStateFlow()

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()
        mutableWakeWordEnabled.value = enabled
    }

    fun setRequireEnrolledVoice(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_ENROLLED_VOICE, required).apply()
        mutableRequireEnrolledVoice.value = required
    }

    fun setSpeakerId(speakerId: Int) {
        prefs.edit().putInt(KEY_SPEAKER_ID, speakerId).apply()
        mutableSpeakerId.value = speakerId
    }
}

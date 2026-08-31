package com.vexo.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "vexo.settings"
private const val KEY_WAKE_WORD = "wake_word_enabled"
private const val KEY_REQUIRE_ENROLLED_VOICE = "require_enrolled_voice"

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

    /** Whether the always-listening foreground service should run. */
    val wakeWordEnabled: StateFlow<Boolean> = mutableWakeWordEnabled.asStateFlow()

    /** Whether a wake word from an unrecognised voice should be ignored. */
    val requireEnrolledVoice: StateFlow<Boolean> = mutableRequireEnrolledVoice.asStateFlow()

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()
        mutableWakeWordEnabled.value = enabled
    }

    fun setRequireEnrolledVoice(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_ENROLLED_VOICE, required).apply()
        mutableRequireEnrolledVoice.value = required
    }
}

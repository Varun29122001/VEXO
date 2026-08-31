package com.vexo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.vexo.speaker.EnrolResult
import com.vexo.speaker.VoiceRecorder
import com.vexo.voice.VoiceOption
import com.vexo.ui.settings.SettingsActions
import com.vexo.ui.settings.SettingsScreen
import com.vexo.ui.settings.SettingsUiState
import com.vexo.wake.WakeWordService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long each enrolment take records for. */
private const val TAKE_MILLIS = 2_500L

/** How many takes are averaged into a voice profile. */
private const val TAKE_COUNT = 3

/**
 * VEXO's only screen. It is not a launcher activity — the launcher icon opens the assistant — so it
 * is reached from the long-press shortcut on the icon, or by tapping the listening notification.
 */
class SettingsActivity : ComponentActivity() {

    private val vexo by lazy { application as VexoApplication }
    private val recorder = VoiceRecorder()

    private var busy by mutableStateOf(false)
    private var status by mutableStateOf<String?>(null)
    private var permissionEpoch by mutableStateOf(0)

    private val requestMicrophone = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionEpoch++ }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionEpoch++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface {
                    // Reading permissionEpoch here makes permission changes recompose the screen.
                    val epoch = permissionEpoch
                    val wakeWord by vexo.settings.wakeWordEnabled.collectAsState()
                    val requireVoice by vexo.settings.requireEnrolledVoice.collectAsState()
                    val speakerId by vexo.settings.speakerId.collectAsState()
                    val profile = remember(epoch, busy) { vexo.speakerGate.profile() }

                    SettingsScreen(
                        state = SettingsUiState(
                            microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
                            notificationsGranted = granted(Manifest.permission.POST_NOTIFICATIONS),
                            overlayGranted = Settings.canDrawOverlays(this),
                            wakeWordEnabled = wakeWord,
                            requireEnrolledVoice = requireVoice,
                            hasVoiceProfile = profile != null,
                            voiceSampleCount = profile?.sampleCount ?: 0,
                            voiceLabel = VoiceOption.labelFor(speakerId),
                            neuralReady = vexo.textToSpeech.isNeuralReady(),
                            busy = busy,
                            status = status,
                        ),
                        actions = SettingsActions(
                            onRequestMicrophone = {
                                requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestNotifications = {
                                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onRequestOverlay = ::openOverlaySettings,
                            onWakeWordChanged = ::setWakeWord,
                            onRequireEnrolledVoiceChanged = {
                                vexo.settings.setRequireEnrolledVoice(it)
                            },
                            onEnrolVoice = ::enrolVoice,
                            onTestVoice = ::testVoice,
                            onDeleteVoice = ::deleteVoice,
                            onPreviousVoice = { stepVoice(-1) },
                            onNextVoice = { stepVoice(+1) },
                            onPreviewVoice = ::previewVoice,
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Overlay permission is granted in system settings, so re-check on return.
        permissionEpoch++
    }

    private fun setWakeWord(enabled: Boolean) {
        vexo.settings.setWakeWordEnabled(enabled)
        WakeWordService.sync(this, enabled)
        status = if (enabled) {
            "Listening for \"Hey VEXO\". The model downloads on Wi-Fi if it is not already here."
        } else {
            "Wake word off."
        }
    }

    /**
     * Records [TAKE_COUNT] takes and averages them into a voice profile. The wake service is stopped
     * first because only one component can hold the microphone.
     */
    private fun enrolVoice() {
        lifecycleScope.launch {
            val resumeWakeWord = vexo.settings.wakeWordEnabled.value
            if (resumeWakeWord) WakeWordService.stop(this@SettingsActivity)

            busy = true
            try {
                status = "Preparing the speaker model…"
                if (!vexo.speakerGate.prepare()) {
                    status = "Could not get the speaker model. Connect to Wi-Fi and try again."
                    return@launch
                }

                val takes = mutableListOf<FloatArray>()
                repeat(TAKE_COUNT) { index ->
                    status = "Say \"Hey VEXO\" — take ${index + 1} of $TAKE_COUNT…"
                    delay(600)
                    val clip = recorder.record(TAKE_MILLIS)
                    if (clip == null) {
                        status = "The microphone was unavailable. Try again."
                        return@launch
                    }
                    takes += clip
                }

                status = "Building your voice print…"
                when (val result = vexo.speakerGate.enrol(takes)) {
                    is EnrolResult.Enrolled ->
                        status = "Enrolled from ${result.profile.sampleCount} recording(s)."

                    EnrolResult.ModelUnavailable ->
                        status = "The speaker model is not available."

                    EnrolResult.NoUsableAudio ->
                        status = "Could not hear enough speech. Try again somewhere quieter."
                }
            } finally {
                busy = false
                if (resumeWakeWord) WakeWordService.start(this@SettingsActivity)
            }
        }
    }

    private fun testVoice() {
        lifecycleScope.launch {
            val resumeWakeWord = vexo.settings.wakeWordEnabled.value
            if (resumeWakeWord) WakeWordService.stop(this@SettingsActivity)

            busy = true
            try {
                status = "Say \"Hey VEXO\"…"
                delay(600)
                val clip = recorder.record(TAKE_MILLIS)
                if (clip == null) {
                    status = "The microphone was unavailable."
                    return@launch
                }
                val similarity = vexo.speakerGate.similarity(clip)
                status = when {
                    similarity == null -> "Could not compare that recording."
                    similarity >= com.vexo.speaker.SPEAKER_MATCH_THRESHOLD ->
                        "That sounds like you (score ${"%.2f".format(similarity)})."

                    else -> "That does not match your voice print " +
                        "(score ${"%.2f".format(similarity)})."
                }
            } finally {
                busy = false
                if (resumeWakeWord) WakeWordService.start(this@SettingsActivity)
            }
        }
    }

    private fun deleteVoice() {
        vexo.speakerGate.deleteProfile()
        vexo.settings.setRequireEnrolledVoice(false)
        permissionEpoch++
        status = "Voice print deleted."
    }

    /**
     * Moves through [VoiceOption.curated] and saves immediately, so stepping and pressing Preview
     * always audition what is actually stored.
     */
    private fun stepVoice(delta: Int) {
        val options = VoiceOption.curated
        val current = options.indexOfFirst { it.speakerId == vexo.settings.speakerId.value }
        val start = if (current >= 0) current else 0
        val next = options[((start + delta) % options.size + options.size) % options.size]
        vexo.settings.setSpeakerId(next.speakerId)
        status = "Voice set to ${next.label}. Press Preview to hear it."
    }

    private fun previewVoice() {
        val label = VoiceOption.labelFor(vexo.settings.speakerId.value)
        status = "Previewing $label…"
        vexo.textToSpeech.speak("This is how VEXO will sound. Opening Wi-Fi settings.")
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

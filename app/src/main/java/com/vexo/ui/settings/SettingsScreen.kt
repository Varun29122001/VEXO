package com.vexo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Everything the settings screen needs to render. Hoisted so the screen stays a pure function. */
data class SettingsUiState(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val overlayGranted: Boolean,
    val wakeWordEnabled: Boolean,
    val requireEnrolledVoice: Boolean,
    val hasVoiceProfile: Boolean,
    val voiceSampleCount: Int,
    val voiceLabel: String,
    val neuralReady: Boolean,
    val busy: Boolean,
    val status: String?,
)

/**
 * Actions the screen can trigger. Grouped into one object so adding a control does not ripple
 * through the composable's signature.
 */
data class SettingsActions(
    val onRequestMicrophone: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onRequestOverlay: () -> Unit,
    val onWakeWordChanged: (Boolean) -> Unit,
    val onRequireEnrolledVoiceChanged: (Boolean) -> Unit,
    val onEnrolVoice: () -> Unit,
    val onTestVoice: () -> Unit,
    val onDeleteVoice: () -> Unit,
    val onPreviousVoice: () -> Unit,
    val onNextVoice: () -> Unit,
    val onPreviewVoice: () -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("VEXO", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A voice assistant with no home screen. This is the only screen it has.",
            style = MaterialTheme.typography.bodyMedium,
        )

        state.status?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Section("Permissions") {
            PermissionRow(
                label = "Microphone",
                detail = "Needed to hear anything at all.",
                granted = state.microphoneGranted,
                onGrant = actions.onRequestMicrophone,
            )
            PermissionRow(
                label = "Notifications",
                detail = "Shows an ongoing notice while VEXO is listening.",
                granted = state.notificationsGranted,
                onGrant = actions.onRequestNotifications,
            )
            PermissionRow(
                label = "Display over other apps",
                detail = "Lets the wake word open VEXO while another app is in front.",
                granted = state.overlayGranted,
                onGrant = actions.onRequestOverlay,
            )
        }

        Section("Wake word") {
            ToggleRow(
                label = "Listen for \"Hey VEXO\"",
                detail = "Keeps the microphone open in the background. Also responds to " +
                    "\"Hi VEXO\", \"OK VEXO\" and \"Hello VEXO\".",
                checked = state.wakeWordEnabled,
                enabled = state.microphoneGranted && !state.busy,
                onCheckedChange = actions.onWakeWordChanged,
            )
            Text(
                "This costs battery and downloads a 15 MiB model on first use. Audio is matched " +
                    "on the device and discarded; nothing is recorded or uploaded.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Assistant voice") {
            Text(
                "Speaking as ${state.voiceLabel}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onPreviousVoice, enabled = !state.busy) {
                    Text("Previous")
                }
                OutlinedButton(onClick = actions.onNextVoice, enabled = !state.busy) {
                    Text("Next")
                }
                Button(onClick = actions.onPreviewVoice, enabled = !state.busy) {
                    Text("Preview")
                }
            }
            Text(
                if (state.neuralReady) {
                    "The neural voice pack has 904 speakers; these ten are a shortlist. Preview " +
                        "plays a sample and the choice is saved immediately."
                } else {
                    "The neural voice pack is not loaded yet, so a preview will use the system " +
                        "voice and this setting will have no audible effect. It downloads on Wi-Fi " +
                        "and applies from the next launch."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("My voice") {
            Text(
                if (state.hasVoiceProfile) {
                    "Enrolled from ${state.voiceSampleCount} recording(s)."
                } else {
                    "No voice enrolled yet."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = actions.onEnrolVoice,
                    enabled = state.microphoneGranted && !state.busy,
                ) {
                    Text(if (state.hasVoiceProfile) "Re-enrol" else "Enrol my voice")
                }
                if (state.hasVoiceProfile) {
                    OutlinedButton(
                        onClick = actions.onTestVoice,
                        enabled = state.microphoneGranted && !state.busy,
                    ) { Text("Test") }
                    OutlinedButton(onClick = actions.onDeleteVoice, enabled = !state.busy) {
                        Text("Delete")
                    }
                }
            }
            ToggleRow(
                label = "Only wake for my voice",
                detail = "Ignore the wake word when it does not sound like me.",
                checked = state.requireEnrolledVoice,
                enabled = state.hasVoiceProfile && !state.busy,
                onCheckedChange = actions.onRequireEnrolledVoiceChanged,
            )
            Text(
                "This is personalisation, not security. A recording of your voice can defeat it, " +
                    "so it is not a lock. The voice print stays in VEXO's private storage, is " +
                    "never uploaded, and Delete removes it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun PermissionRow(
    label: String,
    detail: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.65f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) {
            Text("Granted", style = MaterialTheme.typography.labelLarge)
        } else {
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

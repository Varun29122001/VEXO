package com.vexo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val CardBackground = Color(0xFF1C1C1E)
private val TextSecondary = Color(0xFF8E8E93)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentGreen = Color(0xFF30D158)
private val AccentRed = Color(0xFFFF453A)
private val DividerColor = Color(0xFF38383A)
private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            "VEXO",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Voice Assistant",
            fontSize = 15.sp,
            color = TextSecondary,
        )

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(
            visible = state.status != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        ) {
            state.status?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .shadow(2.dp, CardShape),
                    shape = CardShape,
                    color = Color(0xFF2C2C2E),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentBlue,
                            )
                        }
                        Text(
                            message,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }

        SectionHeader("PERMISSIONS")
        GroupedCard {
            PermissionItem(
                label = "Microphone",
                subtitle = "Required to hear voice commands",
                granted = state.microphoneGranted,
                onGrant = actions.onRequestMicrophone,
            )
            CardDivider()
            PermissionItem(
                label = "Notifications",
                subtitle = "Shows notice while listening",
                granted = state.notificationsGranted,
                onGrant = actions.onRequestNotifications,
            )
            CardDivider()
            PermissionItem(
                label = "Display over apps",
                subtitle = "Opens VEXO from any screen",
                granted = state.overlayGranted,
                onGrant = actions.onRequestOverlay,
            )
        }

        Spacer(Modifier.height(28.dp))

        SectionHeader("WAKE WORD")
        GroupedCard {
            ToggleItem(
                label = "\"Hey VEXO\"",
                subtitle = "Also responds to Hi, OK, and Hello VEXO",
                checked = state.wakeWordEnabled,
                enabled = state.microphoneGranted && !state.busy,
                onCheckedChange = actions.onWakeWordChanged,
            )
        }
        SectionFooter(
            "Keeps the microphone open in the background. Downloads a 15 MiB model on " +
                "first use. Audio is processed on-device and never uploaded.",
        )

        Spacer(Modifier.height(28.dp))

        SectionHeader("ASSISTANT VOICE")
        GroupedCard {
            VoiceSelector(
                voiceLabel = state.voiceLabel,
                enabled = !state.busy,
                onPrevious = actions.onPreviousVoice,
                onNext = actions.onNextVoice,
                onPreview = actions.onPreviewVoice,
            )
        }
        SectionFooter(
            if (state.neuralReady) "Neural voice pack loaded. 10 curated from 904 speakers."
            else "Neural voice downloads on Wi-Fi. Until then, the system voice is used.",
        )

        Spacer(Modifier.height(28.dp))

        SectionHeader("MY VOICE")
        GroupedCard {
            VoiceProfileContent(state, actions)
        }
        if (state.hasVoiceProfile) {
            Spacer(Modifier.height(12.dp))
            GroupedCard {
                ToggleItem(
                    label = "Only wake for my voice",
                    subtitle = "Ignore wake word from others",
                    checked = state.requireEnrolledVoice,
                    enabled = state.hasVoiceProfile && !state.busy,
                    onCheckedChange = actions.onRequireEnrolledVoiceChanged,
                )
            }
        }
        SectionFooter(
            "Voice print stays on-device and is never uploaded. This is " +
                "personalisation, not security.",
        )

        Spacer(Modifier.height(40.dp))
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 18.sp,
    )
}

@Composable
private fun GroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, CardShape),
        shape = CardShape,
        color = CardBackground,
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = DividerColor,
    )
}

// ── Row types ───────────────────────────────────────────────────────────────────

@Composable
private fun PermissionItem(
    label: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !granted) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "permissionScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (!granted) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onGrant,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        if (granted) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Granted",
                tint = AccentGreen,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text("Grant", fontSize = 16.sp, color = AccentBlue)
        }
    }
}

@Composable
private fun ToggleItem(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val labelAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(200),
        label = "labelAlpha",
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(200),
        label = "subtitleAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                label,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = labelAlpha),
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                color = TextSecondary.copy(alpha = subtitleAlpha),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                checkedBorderColor = AccentGreen,
                uncheckedThumbColor = Color(0xFFB0B0B0),
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedBorderColor = Color(0xFF545458),
            ),
        )
    }
}

@Composable
private fun ActionItem(
    label: String,
    enabled: Boolean = true,
    color: Color = AccentBlue,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "actionScale",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(200),
        label = "textAlpha",
    )

    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        fontSize = 16.sp,
        color = color.copy(alpha = textAlpha),
    )
}

// ── Composite sections ──────────────────────────────────────────────────────────

@Composable
private fun VoiceSelector(
    voiceLabel: String,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPreview: () -> Unit,
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(200),
        label = "iconAlpha",
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous voice",
                    tint = AccentBlue.copy(alpha = iconAlpha),
                )
            }
            Text(
                voiceLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next voice",
                    tint = AccentBlue.copy(alpha = iconAlpha),
                )
            }
        }
        CardDivider()
        ActionItem(label = "Preview", enabled = enabled, onClick = onPreview)
    }
}

@Composable
private fun VoiceProfileContent(state: SettingsUiState, actions: SettingsActions) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (state.hasVoiceProfile) "Enrolled from ${state.voiceSampleCount} recording(s)"
                else "No voice enrolled",
                fontSize = 16.sp,
                color = if (state.hasVoiceProfile) Color.White else TextSecondary,
            )
            AnimatedVisibility(
                visible = state.hasVoiceProfile,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    androidx.compose.animation.scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150)),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Enrolled",
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        CardDivider()
        ActionItem(
            label = if (state.hasVoiceProfile) "Re-enrol" else "Enrol My Voice",
            enabled = state.microphoneGranted && !state.busy,
            onClick = actions.onEnrolVoice,
        )
        AnimatedVisibility(
            visible = state.hasVoiceProfile,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            Column {
                CardDivider()
                ActionItem(
                    label = "Test",
                    enabled = state.microphoneGranted && !state.busy,
                    onClick = actions.onTestVoice,
                )
                CardDivider()
                ActionItem(
                    label = "Delete Voice Print",
                    enabled = !state.busy,
                    color = AccentRed,
                    onClick = actions.onDeleteVoice,
                )
            }
        }
    }
}

package com.vexo.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val ENTER_DURATION_MILLIS = 260
private const val EXIT_DURATION_MILLIS = 200

/**
 * Height of the version 2 waveform band, which spans the full width. The wave is drawn centred in
 * the band and the shader tapers it to nothing at the left and right edges, so no horizontal inset is
 * needed; this height is the knob for how tall the wave is and how far up the screen it sits, since
 * its centre line is half of it above the bottom edge.
 */
private val WaveHeight = 220.dp

/**
 * Bottom-anchored assistant surface. It owns only its own enter/exit animation; [onClosed] fires
 * after the exit animation completes so the host can tear the window down without a visible cut.
 *
 * A long press on the orb calls [onOpenSettings]. VEXO has no home screen, so without a gesture here
 * the only route to settings is the launcher icon's long-press shortcut, which is easy to miss.
 */
@Composable
fun AssistantSurface(
    audioLevel: Float,
    dismiss: Boolean,
    onClosed: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(dismiss) { if (dismiss) closing = true }

    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(EXIT_DURATION_MILLIS.toLong())
            onClosed()
        }
    }

    BackHandler(enabled = !closing) { closing = true }

    // One detector for the whole surface rather than one here and another on the animation. Nested
    // detectTapGestures compete, and the outer one wins a long press even when the inner one is
    // directly under the finger — so the position is hit-tested against the wave instead.
    val waveHeightPx = with(LocalDensity.current) { WaveHeight.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(waveHeightPx) {
                detectTapGestures(
                    onTap = { closing = true },
                    onLongPress = { offset ->
                        // The wave spans the width and sits flush with the bottom.
                        val onWave = offset.y >= size.height - waveHeightPx
                        if (onWave) onOpenSettings() else closing = true
                    },
                )
            }
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(ENTER_DURATION_MILLIS)) { it / 2 } +
                fadeIn(tween(ENTER_DURATION_MILLIS)) +
                scaleIn(tween(ENTER_DURATION_MILLIS), initialScale = 0.85f),
            exit = slideOutVertically(tween(EXIT_DURATION_MILLIS)) { it / 2 } +
                fadeOut(tween(EXIT_DURATION_MILLIS)) +
                scaleOut(tween(EXIT_DURATION_MILLIS), targetScale = 0.85f),
        ) {
            // Animation version 2, spanning the screen. The shader's waveform is aspect-independent
            // (see SiriWaveShader), so widening it stretches one lens rather than adding cycles.
            SiriWave(
                variant = SiriWaveVariant.Wave,
                audioLevel = audioLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    // Flush with the bottom edge and no navigation-bar inset: the wave is drawn
                    // centred in its box and the shader fades out well before the edges, so the lower
                    // part draws nothing and any inset would only push the glow up the screen.
                    .height(WaveHeight),
            )
        }
    }
}

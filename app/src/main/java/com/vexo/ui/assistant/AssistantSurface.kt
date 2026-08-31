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
import androidx.compose.foundation.layout.size
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
 * Side of the version 2 square panel. Square because the shaders are aspect-ratio dependent — see
 * [SiriWave]. Version 1's orb size is [OrbSize].
 */
/**
 * Side of the version 2 square panel. Square because the shaders are aspect-ratio dependent — see
 * [SiriWave]. The wave is drawn centred in this square, so its distance from the bottom of the screen
 * is roughly half of this value: shrink it to sit the wave lower, at the cost of a smaller wave.
 * Version 1's orb size is [OrbSize].
 */
private val WaveSize = 260.dp

/** Retained for animation version 1 ([VoiceOrb]), which is no longer wired up. */
@Suppress("unused")
private val OrbSize = 148.dp

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
    val waveSizePx = with(LocalDensity.current) { WaveSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(waveSizePx) {
                detectTapGestures(
                    onTap = { closing = true },
                    onLongPress = { offset ->
                        // The wave is a square flush with the bottom, centred horizontally.
                        val left = (size.width - waveSizePx) / 2f
                        val top = size.height - waveSizePx
                        val onWave = offset.x >= left &&
                            offset.x <= left + waveSizePx &&
                            offset.y >= top
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
            // Animation version 2. Square on purpose: both shaders derive their geometry from the
            // aspect ratio, and only at aspect 1 does the wave form the compact lens of the original.
            SiriWave(
                variant = SiriWaveVariant.Wave,
                audioLevel = audioLevel,
                modifier = Modifier
                    // Flush with the bottom edge, and deliberately without a navigation-bar inset:
                    // the wave sits centred in the square and the shader fades out well before the
                    // edges, so the lower part of the square draws nothing and there is nothing to
                    // keep clear of. Any inset here just pushes the visible glow up the screen.
                    .size(WaveSize),
            )
        }
    }
}

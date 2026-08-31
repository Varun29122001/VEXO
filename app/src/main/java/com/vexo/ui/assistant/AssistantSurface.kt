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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val ENTER_DURATION_MILLIS = 260
private const val EXIT_DURATION_MILLIS = 200
private val OrbSize = 148.dp

/**
 * Bottom-anchored assistant surface. It owns only its own enter/exit animation; [onClosed] fires
 * after the exit animation completes so the host can tear the window down without a visible cut.
 */
@Composable
fun AssistantSurface(
    audioLevel: Float,
    dismiss: Boolean,
    onClosed: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { closing = true } }
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
            VoiceOrb(
                audioLevel = audioLevel,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .size(OrbSize),
            )
        }
    }
}

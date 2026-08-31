package com.vexo.ui.assistant

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.min

private const val BASE_ROTATION_SPEED = 0.3f
private const val VOICE_ROTATION_THRESHOLD = 0.05f

/**
 * **Animation version 1 — retained, not wired up.** `AssistantSurface` renders [SiriWave] instead.
 * Left in place so the original look can be restored by swapping one call; nothing references it.
 *
 * Renders the assistant orb. [audioLevel] is a normalised 0..1 voice amplitude: it accelerates
 * rotation and drives the shader's displacement, so at 0 the orb idles on its noise field alone.
 */
@Suppress("unused")
@Composable
fun VoiceOrb(
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f,
    hue: Float = 0f,
    maxRotationSpeed: Float = 1.2f,
    maxHoverIntensity: Float = 0.8f,
) {
    val shader = remember { RuntimeShader(ORB_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }

    var time by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val level by rememberUpdatedState(audioLevel.coerceIn(0f, 1f))
    val rotationSpeed by rememberUpdatedState(maxRotationSpeed)

    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frame ->
                if (previousFrame != 0L) {
                    val delta = (frame - previousFrame) / 1_000_000_000f
                    time += delta
                    if (level > VOICE_ROTATION_THRESHOLD) {
                        rotation += delta * (BASE_ROTATION_SPEED + level * rotationSpeed * 2f)
                    }
                }
                previousFrame = frame
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("hue", hue)
            shader.setFloatUniform("rot", rotation)
            shader.setFloatUniform("hover", min(level * 2f, 1f))
            shader.setFloatUniform(
                "hoverIntensity",
                min(level * maxHoverIntensity * 0.8f, maxHoverIntensity),
            )
            drawRect(brush)
        }
    )
}

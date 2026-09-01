package com.vexo.ui.assistant

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG = "VexoSiriWave"

/** Which shader [SiriWave] renders. */
enum class SiriWaveVariant {
    /** The iOS voice waveform: chromatic, frequency-reactive. */
    Wave,

    /** Six metaball dots that merge, scatter and gather. */
    FluidDots,
}

/**
 * Animation version 2: a Siri-style shader driven from a `withFrameNanos` loop.
 *
 * **[SiriWaveVariant.Wave] wants a wide surface; [SiriWaveVariant.FluidDots] wants a square one.**
 * The wave normalises its horizontal geometry by the aspect ratio and tapers to nothing at the left
 * and right edges, so it fills whatever band it is given — one waveform stretched, never extra
 * cycles. The dots normalise by `min(res.x, res.y)` and so still render small in a wide band.
 *
 * The shader's own black areas are already transparent — alpha comes from the brightest channel — so
 * with no [background] only the glow is drawn and the overlay stays see-through.
 *
 * Three uniforms are pushed per frame — `iResolution`, `iTime` and `iAudio`. [audioLevel] is the
 * normalised 0..1 microphone amplitude; at 0 nothing in the animation depends on the microphone, and
 * speech only adds movement.
 *
 * Version 1 ([VoiceOrb]) is still in the codebase and can be swapped back in at the call site.
 */
@Composable
fun SiriWave(
    modifier: Modifier = Modifier,
    variant: SiriWaveVariant = SiriWaveVariant.Wave,
    audioLevel: Float = 0f,
    /**
     * Strength of a soft radial dimming drawn behind the wave. **Off by default**, because over a
     * dark background the gradient itself reads as a dim circular blob behind the wave rather than as
     * invisible depth — only the wave should be visible.
     *
     * Raise it if the wave ever needs help standing out against light content; a bright additive glow
     * has little to add over white.
     */
    scrimAlpha: Float = 0f,
    cornerRadius: Dp = 0.dp,
    /**
     * A hard backdrop beneath the shader, transparent by default. Pass `Color.Black` with a
     * [cornerRadius] to get the framed panel of the source component back.
     */
    background: Color = Color.Transparent,
) {
    val source = when (variant) {
        SiriWaveVariant.Wave -> SIRI_WAVE_SHADER
        SiriWaveVariant.FluidDots -> SIRI_FLUID_DOTS_SHADER
    }

    // A malformed shader throws here rather than at draw time. Returning null keeps the overlay
    // usable — VEXO would still listen and answer, just without an animation.
    val shader = remember(source) {
        runCatching { RuntimeShader(source) }
            .onFailure { Log.e(TAG, "Could not compile the $variant shader", it) }
            .getOrNull()
    } ?: return

    val brush = remember(shader) { ShaderBrush(shader) }

    var time by remember { mutableFloatStateOf(0f) }
    val level by rememberUpdatedState(audioLevel.coerceIn(0f, 1f))

    LaunchedEffect(shader) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frame ->
                if (previousFrame != 0L) {
                    time += (frame - previousFrame) / 1_000_000_000f
                }
                previousFrame = frame
            }
        }
    }

    val clipped =
        if (cornerRadius > 0.dp) modifier.clip(RoundedCornerShape(cornerRadius)) else modifier

    Box(
        modifier = clipped.drawBehind {
            if (background != Color.Transparent) drawRect(background)
            if (scrimAlpha > 0f) {
                // Centred on the wave and faded out before the edges, so it reads as depth behind
                // the glow rather than as a panel with a boundary.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f)),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.5f,
                    )
                )
            }
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iAudio", level)
            drawRect(brush)
        }
    )
}

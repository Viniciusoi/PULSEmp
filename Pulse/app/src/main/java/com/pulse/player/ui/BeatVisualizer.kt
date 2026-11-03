package com.pulse.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min as dpMin
import com.pulse.player.PulseApp
import com.pulse.player.audio.BeatAnalyzer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BARS = 72

/** Valores suavizados que a animação desenha a cada quadro. */
private class VisualState {
    val bands = FloatArray(BeatAnalyzer.BAND_COUNT)
    var level = 0f
    var pulse = 0f
    val rings = FloatArray(4) { -1f }
    private var lastBeat = 0
    private var phase = 0f
    private var last = 0L
    private var fallbackTimer = 0f

    fun isIdle() = level < 0.003f && pulse < 0.003f && bands.all { it < 0.003f } && rings.all { it < 0f }

    private fun spawnRing() {
        var idx = rings.indexOfFirst { it < 0f }
        if (idx < 0) {
            idx = 0
            for (i in rings.indices) if (rings[i] > rings[idx]) idx = i
        }
        rings[idx] = 0f
    }

    fun update(now: Long, target: FloatArray, tLevel: Float, beats: Int, live: Boolean, playing: Boolean) {
        val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        last = now
        phase += dt

        for (i in bands.indices) {
            val t = when {
                !playing -> 0f
                live -> target.getOrElse(i) { 0f }
                else -> 0.18f + 0.12f * sin(phase * 2.2f + i * 0.45f)
            }
            val k = if (t > bands[i]) 18f else 6f
            bands[i] += (t - bands[i]) * min(1f, dt * k)
        }

        val tl = when {
            !playing -> 0f
            live -> tLevel
            else -> 0.25f + 0.15f * sin(phase * 3f)
        }
        val kl = if (tl > level) 25f else 7f
        level += (tl - level) * min(1f, dt * kl)

        pulse = max(0f, pulse - dt * 3.5f)
        if (live && playing) {
            if (beats != lastBeat) {
                lastBeat = beats
                pulse = 1f
                spawnRing()
            }
        } else {
            lastBeat = beats
            if (playing) {
                fallbackTimer += dt
                if (fallbackTimer > 1.1f) {
                    fallbackTimer = 0f
                    pulse = 0.6f
                    spawnRing()
                }
            }
        }

        for (i in rings.indices) {
            if (rings[i] >= 0f) {
                rings[i] += dt / 0.9f
                if (rings[i] > 1f) rings[i] = -1f
            }
        }
    }
}

/** Capa redonda no centro com barras, onda e anéis que pulsam no grave da música. */
@Composable
fun BeatVisualizer(cover: Bitmap?, accent: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as PulseApp
    val analyzer = app.analyzer
    val vs = remember { VisualState() }
    var tick by remember { mutableLongStateOf(0L) }
    val playingNow by rememberUpdatedState(playing)

    LaunchedEffect(Unit) {
        while (true) {
            if (!playingNow && vs.isIdle()) {
                delay(120)
            } else {
                withFrameNanosCompat { n ->
                    vs.update(
                        n,
                        analyzer.bands.value,
                        analyzer.level.value,
                        analyzer.beatCount.value,
                        analyzer.active.value,
                        playingNow,
                    )
                    tick = n
                }
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = dpMin(maxWidth, maxHeight)
        val coverSize = d * 0.58f

        Canvas(Modifier.fillMaxSize()) {
            val t = tick
            if (t < 0) return@Canvas
            val c = center
            val minDim = size.minDimension
            val coverR = minDim * 0.29f
            val r0 = coverR * 1.16f
            val maxLen = minDim * 0.5f - r0 - 2.dp.toPx()
            val glow = 0.25f + 0.5f * vs.level + 0.25f * vs.pulse

            // brilho de fundo
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = (0.45f * glow).coerceIn(0f, 1f)), Color.Transparent),
                    center = c,
                    radius = minDim * 0.5f,
                ),
                radius = minDim * 0.5f,
                center = c,
            )

            // anéis que se expandem a cada batida
            for (a in vs.rings) {
                if (a < 0f) continue
                val rr = coverR * 1.05f + (minDim * 0.5f - coverR * 1.05f) * a
                drawCircle(
                    color = accent.copy(alpha = 0.55f * (1f - a)),
                    radius = rr,
                    center = c,
                    style = Stroke(width = 3.dp.toPx() * (1f - a) + 1f),
                )
            }

            // barras ao redor da capa (graves no topo, espelhadas)
            val half = BARS / 2
            val bn = vs.bands.size
            for (i in 0 until BARS) {
                val j = if (i < half) i else BARS - 1 - i
                val b = vs.bands[(j * bn / half).coerceIn(0, bn - 1)]
                val ang = (i.toFloat() / BARS) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val dx = cos(ang)
                val dy = sin(ang)
                val len = 4.dp.toPx() + b * maxLen
                drawLine(
                    color = lerp(accent, Color.White, b * 0.5f).copy(alpha = 0.45f + 0.55f * b),
                    start = Offset(c.x + dx * r0, c.y + dy * r0),
                    end = Offset(c.x + dx * (r0 + len), c.y + dy * (r0 + len)),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // onda contínua em volta
            val pts = 140
            val path = Path()
            for (k in 0..pts) {
                val u = k / pts.toFloat()
                val m = if (u < 0.5f) u * 2f else (1f - u) * 2f
                val pos = m * (bn - 1)
                val i0 = pos.toInt().coerceIn(0, bn - 1)
                val i1 = min(i0 + 1, bn - 1)
                val f = pos - i0
                val v = vs.bands[i0] * (1f - f) + vs.bands[i1] * f
                val rad = r0 * 0.97f + (0.35f * vs.level + 0.5f * v) * maxLen * 0.5f
                val ang = u * 2f * PI.toFloat() - PI.toFloat() / 2f
                val x = c.x + cos(ang) * rad
                val y = c.y + sin(ang) * rad
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, accent.copy(alpha = 0.10f))
            drawPath(path, accent.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx()))
        }

        val cs = MaterialTheme.colorScheme
        val coverMod = Modifier
            .size(coverSize)
            .graphicsLayer {
                val t = tick
                val s = if (t < 0) 1f else 1f + 0.045f * vs.level + 0.04f * vs.pulse
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .border(2.dp, accent.copy(alpha = 0.7f), CircleShape)

        if (cover != null) {
            val img = remember(cover) { cover.asImageBitmap() }
            Image(img, null, modifier = coverMod, contentScale = ContentScale.Crop)
        } else {
            androidx.compose.foundation.layout.Box(
                coverMod.background(Brush.linearGradient(listOf(cs.primaryContainer, cs.surfaceVariant))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(64.dp))
            }
        }
    }
}

private suspend fun withFrameNanosCompat(block: (Long) -> Unit) {
    androidx.compose.runtime.withFrameNanos { block(it) }
}

package com.pulse.player.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lê o áudio que o próprio app está tocando (Visualizer) e entrega:
 *  - bands: 48 faixas de frequência (0..1)
 *  - level: energia do grave (0..1)
 *  - beatCount: sobe 1 a cada batida detectada
 * Precisa da permissão de microfone (RECORD_AUDIO). Nada é gravado.
 */
class BeatAnalyzer(private val context: Context) {
    companion object {
        const val BAND_COUNT = 48
    }

    val bands = MutableStateFlow(FloatArray(BAND_COUNT))
    val level = MutableStateFlow(0f)
    val beatCount = MutableStateFlow(0)
    /** true quando o Visualizer está realmente funcionando. */
    val active = MutableStateFlow(false)

    private var visualizer: Visualizer? = null
    private var sessionId = 0
    private var wanted = false

    // estado usado só pela thread do Visualizer
    private var peak = 20f
    private var bandPeak = 20f
    private var avg = 0f
    private var lastBeat = 0L

    fun attach(sessionId: Int) {
        this.sessionId = sessionId
        rebuild()
    }

    fun setWanted(w: Boolean) {
        wanted = w
        rebuild()
    }

    fun refresh() = rebuild()

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun release() {
        try {
            visualizer?.apply {
                enabled = false
                release()
            }
        } catch (e: Throwable) {
        }
        visualizer = null
    }

    private fun rebuild() {
        release()
        if (!wanted || sessionId == 0 || !hasPermission()) {
            active.value = false
            if (!wanted) {
                level.value = 0f
                bands.value = FloatArray(BAND_COUNT)
            }
            return
        }
        try {
            val v = Visualizer(sessionId)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(listener, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
            active.value = true
        } catch (e: Throwable) {
            active.value = false
        }
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft != null) process(fft, samplingRate)
        }
    }

    private fun process(fft: ByteArray, samplingRateMilliHz: Int) {
        val n = fft.size
        val half = n / 2
        if (half < 8) return
        val mags = FloatArray(half)
        for (k in 1 until half) {
            val re = fft[2 * k].toInt()
            val im = fft[2 * k + 1].toInt()
            mags[k] = hypot(re.toFloat(), im.toFloat())
        }
        val binHz = (samplingRateMilliHz / 1000f) / n

        // ---- grave (até ~150 Hz) ----
        val kHi = (150f / binHz).toInt().coerceIn(2, half - 1)
        var sum = 0f
        for (k in 1..kHi) sum += mags[k]
        val energy = sum / kHi
        peak = max(max(peak * 0.995f, energy), 8f)
        val norm = (energy / peak).coerceIn(0f, 1f)

        // ---- detecção de batida ----
        val now = SystemClock.elapsedRealtime()
        avg = avg * 0.92f + energy * 0.08f
        if (energy > avg * 1.3f && energy > peak * 0.35f && now - lastBeat > 230) {
            lastBeat = now
            beatCount.value = beatCount.value + 1
        }

        // ---- 48 faixas em escala logarítmica (40 Hz .. 14 kHz) ----
        val out = FloatArray(BAND_COUNT)
        var maxBand = 0f
        val ratio = (14000f / 40f).toDouble()
        for (i in 0 until BAND_COUNT) {
            val f0 = 40.0 * ratio.pow(i.toDouble() / BAND_COUNT)
            val f1 = 40.0 * ratio.pow((i + 1).toDouble() / BAND_COUNT)
            val k0 = (f0 / binHz).toInt().coerceIn(1, half - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0 + 1, half)
            var s = 0f
            for (k in k0 until k1) s += mags[k]
            val a = s / (k1 - k0) * (1f + i / BAND_COUNT.toFloat() * 1.5f)
            out[i] = a
            if (a > maxBand) maxBand = a
        }
        bandPeak = max(max(bandPeak * 0.995f, maxBand), 8f)
        for (i in out.indices) out[i] = sqrt((out[i] / bandPeak).coerceIn(0f, 1f))

        bands.value = out
        level.value = norm * norm
    }
}

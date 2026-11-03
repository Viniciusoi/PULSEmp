package com.pulse.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.pulse.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

class EqualizerManager(private val prefs: Prefs) {

    data class Info(
        val enabled: Boolean,
        val minLevel: Int,
        val maxLevel: Int,
        val freqsHz: List<Int>,
        val levels: List<Int>,
        val presets: List<String>,
        val bass: Int,
        val bassSupported: Boolean,
    )

    val info = MutableStateFlow<Info?>(null)

    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var enabled = prefs.eqEnabled
    private var bass = prefs.bassStrength

    fun attach(sessionId: Int) {
        if (sessionId == 0) return
        release()
        try {
            val e = Equalizer(0, sessionId)
            val saved = prefs.eqLevels
            for (i in 0 until e.numberOfBands.toInt()) {
                saved.getOrNull(i)?.let { e.setBandLevel(i.toShort(), it.toShort()) }
            }
            e.enabled = enabled
            eq = e
        } catch (t: Throwable) {
            eq = null
        }
        try {
            val b = BassBoost(0, sessionId)
            if (b.strengthSupported) {
                b.setStrength(bass.toShort())
                b.enabled = bass > 0
                bassBoost = b
            } else {
                b.release()
            }
        } catch (t: Throwable) {
            bassBoost = null
        }
        publish()
    }

    private fun release() {
        try { eq?.release() } catch (t: Throwable) {}
        try { bassBoost?.release() } catch (t: Throwable) {}
        eq = null
        bassBoost = null
    }

    private fun publish() {
        val e = eq
        if (e == null) {
            info.value = null
            return
        }
        try {
            val bands = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            info.value = Info(
                enabled = enabled,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                freqsHz = (0 until bands).map { e.getCenterFreq(it.toShort()) / 1000 },
                levels = (0 until bands).map { e.getBandLevel(it.toShort()).toInt() },
                presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) },
                bass = bass,
                bassSupported = bassBoost != null,
            )
        } catch (t: Throwable) {
            info.value = null
        }
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        prefs.eqEnabled = v
        try { eq?.enabled = v } catch (t: Throwable) {}
        publish()
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        try {
            e.setBandLevel(index.toShort(), level.toShort())
            prefs.eqLevels = (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (t: Throwable) {}
        publish()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            prefs.eqLevels = (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (t: Throwable) {}
        publish()
    }

    fun setBass(strength: Int) {
        bass = strength.coerceIn(0, 1000)
        prefs.bassStrength = bass
        try {
            bassBoost?.setStrength(bass.toShort())
            bassBoost?.enabled = bass > 0
        } catch (t: Throwable) {}
        publish()
    }
}

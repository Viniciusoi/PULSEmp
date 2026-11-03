package com.pulse.player.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.player.PulseApp

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as PulseApp
    val eqm = app.equalizer
    val info by eqm.info.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val i = info
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            if (i == null) {
                Text("Equalizador", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Toque uma música primeiro. Se continuar indisponível, o seu aparelho não oferece equalizador.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Equalizador",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = i.enabled, onCheckedChange = { eqm.setEnabled(it) })
                }
                if (i.presets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        i.presets.forEachIndexed { idx, name ->
                            FilterChip(selected = false, onClick = { eqm.usePreset(idx) }, label = { Text(name) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                i.freqsHz.forEachIndexed { band, hz ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabel(hz), modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = i.levels[band].toFloat().coerceIn(i.minLevel.toFloat(), i.maxLevel.toFloat()),
                            onValueChange = { eqm.setBand(band, it.toInt()) },
                            valueRange = i.minLevel.toFloat()..i.maxLevel.toFloat(),
                            enabled = i.enabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+d dB".format(i.levels[band] / 100),
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (i.bassSupported) {
                    Spacer(Modifier.height(8.dp))
                    Text("Reforço de graves", fontWeight = FontWeight.Medium)
                    Slider(
                        value = i.bass.toFloat(),
                        onValueChange = { eqm.setBass(it.toInt()) },
                        valueRange = 0f..1000f,
                    )
                }
            }
        }
    }
}

package com.pulse.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.pulse.player.PulseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(vm: MainViewModel, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as PulseApp
    val player = app.player
    val state by player.state.collectAsState()
    val position by player.position.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    val sleepLeft by player.sleepRemainingMs.collectAsState()
    val track = state.current

    var showQueue by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    val bmp by rememberCoverBitmap(track, 720)
    val base = MaterialTheme.colorScheme.primary
    var accent by remember { mutableStateOf(base) }
    LaunchedEffect(bmp, base) {
        val b = bmp
        accent = if (b == null) base else withContext(Dispatchers.Default) {
            try {
                val p = Palette.from(b).maximumColorCount(16).generate()
                var col = Color(p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(base.toArgb()))))
                if (col.luminance() < 0.25f) col = lerp(col, Color.White, 0.4f)
                col
            } catch (e: Throwable) {
                base
            }
        }
    }
    val animAccent by animateColorAsState(accent, tween(700), label = "accent")
    val bg = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .background(Brush.verticalGradient(listOf(animAccent.copy(alpha = 0.40f), bg, bg)))
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // topo
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Fechar", modifier = Modifier.size(32.dp)) }
                Text(
                    "Tocando agora",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Fila") }
            }

            // visualizador (capa + ondas)
            BeatVisualizer(
                cover = bmp,
                accent = animAccent,
                playing = state.isPlaying,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            // título / artista / favorita
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Nenhuma música",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track != null) {
                    val fav = track.id in favs
                    IconButton(onClick = { vm.toggleFavorite(track.id) }) {
                        Icon(
                            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Favoritar",
                            tint = if (fav) animAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // barra de progresso
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val dur = max(state.durationMs, 1L)
            val shown = if (dragging) dragValue else position.toFloat()
            Slider(
                value = shown.coerceIn(0f, dur.toFloat()),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    player.seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..dur.toFloat(),
                enabled = track != null,
                colors = SliderDefaults.colors(
                    thumbColor = animAccent,
                    activeTrackColor = animAccent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(shown.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // controles
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, "Aleatório",
                        tint = if (state.shuffle) animAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", modifier = Modifier.size(38.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(animAccent)
                        .clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play/Pausa",
                        tint = if (animAccent.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) animAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // extras
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showEq = true }) {
                    Icon(Icons.Rounded.Equalizer, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Equalizador")
                }
                TextButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, null, tint = if (sleepLeft > 0) animAccent else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(if (sleepLeft > 0) formatTime(sleepLeft) else "Dormir")
                }
            }
        }
    }

    if (showEq) EqualizerSheet(onDismiss = { showEq = false })

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Timer para dormir") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        TextButton(
                            onClick = {
                                player.setSleepTimer(m)
                                showSleep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$m minutos") }
                    }
                    if (sleepLeft > 0) {
                        TextButton(
                            onClick = {
                                player.setSleepTimer(0)
                                showSleep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Desativar timer") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text("Fechar") } },
        )
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                "Fila de reprodução (${state.queue.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) {
                if (state.currentIndex > 1) listState.scrollToItem(state.currentIndex - 1)
            }
            LazyColumn(state = listState, modifier = Modifier.height(480.dp)) {
                itemsIndexed(state.queue) { index, t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { player.jumpTo(index) }
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(t, 44.dp, px = 160)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                t.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { player.removeAt(index) }) { Icon(Icons.Rounded.Close, "Remover da fila") }
                    }
                }
            }
        }
    }
}

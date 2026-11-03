package com.pulse.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.player.data.Track
import kotlin.random.Random

@Composable
private fun TrackList(
    vm: MainViewModel,
    tracks: List<Track>,
    onMore: (Track) -> Unit,
) {
    val state by vm.player.state.collectAsState()
    val curId = state.current?.id
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        items(tracks, key = { it.id }) { t ->
            TrackRow(
                track = t,
                isCurrent = t.id == curId,
                onClick = { vm.player.playList(tracks, tracks.indexOf(t)) },
                onMore = { onMore(t) },
            )
        }
    }
}

@Composable
fun SongsTab(vm: MainViewModel, onMore: (Track) -> Unit) {
    val tracks by vm.visible.collectAsState()
    val query by vm.query.collectAsState()
    val loading by vm.loading.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Buscar música, artista ou álbum") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Limpar") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        if (tracks.isNotEmpty()) {
            PlayButtons(
                count = tracks.size,
                onPlay = { vm.player.playList(tracks, 0, false) },
                onShuffle = { vm.player.playList(tracks, Random.nextInt(tracks.size), true) },
            )
            TrackList(vm, tracks, onMore)
        } else {
            EmptyState(
                when {
                    loading -> "Carregando músicas…"
                    query.isNotEmpty() -> "Nada encontrado para \"$query\""
                    else -> "Nenhuma música encontrada.\nBaixe músicas para o celular e toque em atualizar."
                }
            )
        }
    }
}

@Composable
fun FoldersTab(vm: MainViewModel, onOpen: (String) -> Unit) {
    val folders by vm.folders.collectAsState()
    if (folders.isEmpty()) {
        EmptyState("Nenhuma pasta com músicas.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Marque as pastas que entram na sua biblioteca. Toque na pasta para ver as músicas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(folders, key = { it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(f.path) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${f.count} músicas • ${f.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(checked = f.included, onCheckedChange = { vm.toggleFolder(f.path) })
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(vm: MainViewModel, onOpen: (String) -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = { creating = true }, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Nova playlist")
        }
        if (playlists.isEmpty()) {
            EmptyState("Você ainda não tem playlists.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${p.trackIds.size} músicas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nova playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.createPlaylist(name)
                    name = ""
                    creating = false
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun FavoritesTab(vm: MainViewModel, onMore: (Track) -> Unit) {
    val favs by vm.favorites.collectAsState()
    if (favs.isEmpty()) {
        EmptyState("Toque nos três pontinhos de uma música e escolha \"Adicionar às favoritas\".")
        return
    }
    Column(Modifier.fillMaxSize()) {
        PlayButtons(
            count = favs.size,
            onPlay = { vm.player.playList(favs, 0, false) },
            onShuffle = { vm.player.playList(favs, Random.nextInt(favs.size), true) },
        )
        TrackList(vm, favs, onMore)
    }
}

/** Tela de detalhe usada por pastas e playlists. */
@Composable
fun TrackListDetail(
    vm: MainViewModel,
    title: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    onMore: (Track) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        if (tracks.isEmpty()) {
            EmptyState("Nenhuma música aqui.")
        } else {
            PlayButtons(
                count = tracks.size,
                onPlay = { vm.player.playList(tracks, 0, false) },
                onShuffle = { vm.player.playList(tracks, Random.nextInt(tracks.size), true) },
            )
            TrackList(vm, tracks, onMore)
        }
    }
}

@Composable
fun DeleteIconWithConfirm(label: String, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    IconButton(onClick = { ask = true }) { Icon(Icons.Rounded.Delete, "Excluir") }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Excluir $label?") },
            text = { Text("As músicas continuam no celular, só a playlist é removida.") },
            confirmButton = { TextButton(onClick = { ask = false; onConfirm() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancelar") } },
        )
    }
}

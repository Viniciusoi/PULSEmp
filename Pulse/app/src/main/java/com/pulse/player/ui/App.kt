package com.pulse.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.player.PulseApp
import com.pulse.player.data.Track

enum class Tab(val label: String, val icon: ImageVector) {
    Songs("Músicas", Icons.Rounded.LibraryMusic),
    Folders("Pastas", Icons.Rounded.Folder),
    Playlists("Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    Favorites("Favoritas", Icons.Rounded.Favorite),
}

sealed interface Detail {
    data class FolderD(val path: String) : Detail
    data class PlaylistD(val id: String) : Detail
}

@Composable
fun PulseRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as PulseApp
    val pc = rememberPermissionController()
    val perms by pc.perms
    var onboarded by remember { mutableStateOf(app.prefs.onboardingDone) }

    LaunchedEffect(perms.storage) { if (perms.storage) vm.refresh() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!perms.storage || !onboarded) {
            PermissionsScreen(pc, onContinue = {
                app.prefs.onboardingDone = true
                onboarded = true
            })
        } else {
            MainScreen(vm, pc)
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel, pc: PermissionController) {
    val app = LocalContext.current.applicationContext as PulseApp

    var tab by rememberSaveable { mutableStateOf(Tab.Songs) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showNow by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuPlaylistId by remember { mutableStateOf<String?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    val favIds by vm.favoriteIds.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val allTracks by vm.allTracks.collectAsState()

    DisposableEffect(showNow) {
        app.nowPlayingVisible.value = showNow
        onDispose { app.nowPlayingVisible.value = false }
    }
    BackHandler(showNow) { showNow = false }
    BackHandler(!showNow && showSettings) { showSettings = false }
    BackHandler(!showNow && !showSettings && detail != null) { detail = null }

    fun openMenu(t: Track, playlistId: String?) {
        menuPlaylistId = playlistId
        menuTrack = t
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(onOpen = { showNow = true })
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t && !showSettings,
                                onClick = {
                                    tab = t
                                    detail = null
                                    showSettings = false
                                },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                val d = detail
                when {
                    showSettings -> SettingsScreen(vm, pc) { showSettings = false }

                    d is Detail.FolderD -> {
                        val list = remember(allTracks, d.path) { allTracks.filter { it.folder == d.path } }
                        TrackListDetail(
                            vm = vm,
                            title = d.path.substringAfterLast('/'),
                            tracks = list,
                            onBack = { detail = null },
                            onMore = { openMenu(it, null) },
                        )
                    }

                    d is Detail.PlaylistD -> {
                        val pl = playlists.firstOrNull { it.id == d.id }
                        if (pl == null) {
                            LaunchedEffect(Unit) { detail = null }
                        } else {
                            val byId = remember(allTracks) { allTracks.associateBy { it.id } }
                            val list = pl.trackIds.mapNotNull { byId[it] }
                            TrackListDetail(
                                vm = vm,
                                title = pl.name,
                                tracks = list,
                                onBack = { detail = null },
                                onMore = { openMenu(it, pl.id) },
                                actions = {
                                    DeleteIconWithConfirm(pl.name) {
                                        vm.deletePlaylist(pl.id)
                                        detail = null
                                    }
                                },
                            )
                        }
                    }

                    else -> Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                "Pulse",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Rounded.Refresh, "Atualizar") }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Configurações") }
                        }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Songs -> SongsTab(vm) { openMenu(it, null) }
                                Tab.Folders -> FoldersTab(vm) { detail = Detail.FolderD(it) }
                                Tab.Playlists -> PlaylistsTab(vm) { detail = Detail.PlaylistD(it) }
                                Tab.Favorites -> FavoritesTab(vm) { openMenu(it, null) }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNow,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, onClose = { showNow = false })
        }
    }

    menuTrack?.let { t ->
        val pid = menuPlaylistId
        TrackMenuSheet(
            track = t,
            isFavorite = t.id in favIds,
            onDismiss = { menuTrack = null },
            onPlayNext = { vm.player.playNext(t) },
            onAddToQueue = { vm.player.addToQueue(t) },
            onToggleFavorite = { vm.toggleFavorite(t.id) },
            onAddToPlaylist = { addToPlaylistTrack = t },
            onRemoveFromPlaylist = if (pid != null) ({ vm.removeFromPlaylist(pid, t.id) }) else null,
        )
    }

    addToPlaylistTrack?.let { t ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistTrack = null },
            onPick = {
                vm.addToPlaylist(it.id, t.id)
                addToPlaylistTrack = null
            },
            onCreate = {
                vm.createPlaylist(it, t.id)
                addToPlaylistTrack = null
            },
        )
    }
}

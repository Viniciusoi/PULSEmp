package com.pulse.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.player.PulseApp
import com.pulse.player.data.FolderInfo
import com.pulse.player.data.Playlist
import com.pulse.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.UUID

private val ACCENTS = Regex("\\p{Mn}+")

/** minúsculas e sem acento, para a busca ignorar "á" x "a". */
fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseApp
    private val prefs = app.prefs
    val player get() = app.player

    val allTracks = MutableStateFlow<List<Track>>(emptyList())
    val loading = MutableStateFlow(false)
    val excluded = MutableStateFlow(prefs.excludedFolders)
    val favoriteIds = MutableStateFlow(prefs.favorites)
    val playlists = MutableStateFlow(prefs.playlists)
    val query = MutableStateFlow("")
    val themeMode = MutableStateFlow(prefs.themeMode)

    private val haystack = HashMap<Long, String>()

    /** Músicas das pastas incluídas. */
    val library: StateFlow<List<Track>> =
        combine(allTracks, excluded) { all, ex -> all.filter { it.folder !in ex } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Biblioteca filtrada pela busca. */
    val visible: StateFlow<List<Track>> =
        combine(library, query) { lib, q ->
            val n = q.trim().normalized()
            if (n.isEmpty()) lib else lib.filter { haystack[it.id]?.contains(n) == true }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<Track>> =
        combine(library, favoriteIds) { lib, f -> lib.filter { it.id in f } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderInfo>> =
        combine(allTracks, excluded) { all, ex ->
            all.groupBy { it.folder }
                .map { (path, list) ->
                    FolderInfo(path, path.substringAfterLast('/').ifEmpty { "/" }, list.size, path !in ex)
                }
                .sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            val list = app.repository.scan()
            val map = withContext(Dispatchers.Default) {
                list.associate { it.id to "${it.title} ${it.artist} ${it.album}".normalized() }
            }
            haystack.clear()
            haystack.putAll(map)
            allTracks.value = list
            app.player.onLibraryLoaded(list)
            loading.value = false
        }
    }

    fun toggleFolder(path: String) {
        val next = excluded.value.toMutableSet()
        if (!next.add(path)) next.remove(path)
        excluded.value = next
        prefs.excludedFolders = next
    }

    fun toggleFavorite(id: Long) {
        val next = favoriteIds.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        favoriteIds.value = next
        prefs.favorites = next
    }

    private fun savePlaylists(list: List<Playlist>) {
        playlists.value = list
        prefs.playlists = list
    }

    fun createPlaylist(name: String, firstTrack: Long? = null) {
        val p = Playlist(UUID.randomUUID().toString(), name.trim(), listOfNotNull(firstTrack))
        savePlaylists(playlists.value + p)
    }

    fun deletePlaylist(id: String) = savePlaylists(playlists.value.filter { it.id != id })

    fun addToPlaylist(playlistId: String, trackId: Long) {
        savePlaylists(playlists.value.map {
            if (it.id == playlistId && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        })
    }

    fun removeFromPlaylist(playlistId: String, trackId: Long) {
        savePlaylists(playlists.value.map {
            if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
        })
    }

    fun setTheme(mode: Int) {
        themeMode.value = mode
        prefs.themeMode = mode
    }
}

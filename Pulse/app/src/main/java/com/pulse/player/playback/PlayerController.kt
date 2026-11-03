package com.pulse.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pulse.player.data.Prefs
import com.pulse.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
)

/** Fala com o PlaybackService e expõe o estado do player para a interface. Vive enquanto o processo viver. */
class PlayerController(private val context: Context, private val prefs: Prefs) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var library: Map<Long, Track> = emptyMap()
    private var restored = false
    private var sleepJob: Job? = null

    val state = MutableStateFlow(PlayerState())
    val position = MutableStateFlow(0L)
    val sleepRemainingMs = MutableStateFlow(0L)

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            try {
                val c = future.get()
                controller = c
                attach(c)
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun attach(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) savePosition()
            }
        })
        val todo = ArrayList(pending)
        pending.clear()
        todo.forEach { it(c) }
        publish()
        startLoop()
    }

    private fun run(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending.add(block)
            connect()
        }
    }

    private fun startLoop() {
        scope.launch {
            var ticks = 0
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    position.value = c.currentPosition.coerceAtLeast(0L)
                    ticks++
                    if (ticks % 25 == 0) savePosition()
                }
                delay(200)
            }
        }
    }

    // ---------------------------------------------------------------- estado

    private fun item(t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(t.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(t.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artUri)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun toTrack(item: MediaItem): Track {
        val id = item.mediaId.toLongOrNull() ?: -1L
        return library[id] ?: Track(
            id = id,
            uri = item.localConfiguration?.uri ?: Uri.EMPTY,
            title = item.mediaMetadata.title?.toString() ?: "",
            artist = item.mediaMetadata.artist?.toString() ?: "",
            album = item.mediaMetadata.albumTitle?.toString() ?: "",
            albumId = 0L,
            durationMs = 0L,
            path = "",
        )
    }

    private fun publish() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { toTrack(c.getMediaItemAt(it)) }
        val idx = c.currentMediaItemIndex
        val cur = items.getOrNull(idx)
        val d = c.duration
        val dur = if (d == C.TIME_UNSET || d < 0) (cur?.durationMs ?: 0L) else d
        state.value = PlayerState(
            current = cur,
            isPlaying = c.isPlaying,
            durationMs = dur,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = items,
            currentIndex = if (cur == null) -1 else idx,
        )
        position.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun saveQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        prefs.lastQueue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
    }

    fun savePosition() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        c.currentMediaItem?.mediaId?.toLongOrNull()?.let { prefs.lastTrackId = it }
        prefs.lastPosition = c.currentPosition.coerceAtLeast(0L)
        prefs.shuffle = c.shuffleModeEnabled
        prefs.repeat = c.repeatMode
    }

    /** Chamado quando a biblioteca termina de carregar; também restaura a última fila. */
    fun onLibraryLoaded(tracks: List<Track>) {
        library = tracks.associateBy { it.id }
        if (!restored) {
            restored = true
            val ids = prefs.lastQueue
            if (ids.isNotEmpty()) {
                run { c ->
                    if (c.mediaItemCount == 0) {
                        val items = ids.mapNotNull { library[it] }.map { item(it) }
                        if (items.isNotEmpty()) {
                            val idx = items.indexOfFirst { it.mediaId == prefs.lastTrackId.toString() }
                                .coerceAtLeast(0)
                            c.shuffleModeEnabled = prefs.shuffle
                            c.repeatMode = prefs.repeat
                            c.setMediaItems(items, idx, prefs.lastPosition)
                            c.prepare()
                        }
                    }
                }
            }
        }
        publish()
    }

    // ---------------------------------------------------------------- comandos

    fun playList(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        run { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { item(it) }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = run { c ->
        if (c.mediaItemCount == 0) return@run
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = run { it.seekToNextMediaItem() }
    fun previous() = run { it.seekToPrevious() }

    fun seekTo(ms: Long) = run {
        it.seekTo(ms)
        position.value = ms
    }

    fun toggleShuffle() = run { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = run {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun addToQueue(t: Track) = run { c ->
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItem(item(t))
        if (wasEmpty) c.prepare()
    }

    fun playNext(t: Track) = run { c ->
        if (c.mediaItemCount == 0) {
            c.addMediaItem(item(t))
            c.prepare()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item(t))
        }
    }

    fun jumpTo(index: Int) = run {
        it.seekToDefaultPosition(index)
        it.play()
    }

    fun removeAt(index: Int) = run {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    // ---------------------------------------------------------------- timer para dormir

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepRemainingMs.value = 0L
            return
        }
        sleepJob = scope.launch {
            var left = minutes * 60_000L
            while (left > 0) {
                sleepRemainingMs.value = left
                delay(1000)
                left -= 1000
            }
            sleepRemainingMs.value = 0L
            controller?.pause()
        }
    }
}

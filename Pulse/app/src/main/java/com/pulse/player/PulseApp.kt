package com.pulse.player

import android.app.Application
import com.pulse.player.audio.BeatAnalyzer
import com.pulse.player.audio.EqualizerManager
import com.pulse.player.data.MusicRepository
import com.pulse.player.data.Prefs
import com.pulse.player.playback.PlaybackService
import com.pulse.player.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PulseApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var analyzer: BeatAnalyzer
        private set
    lateinit var equalizer: EqualizerManager
        private set

    /** true enquanto a tela "Tocando agora" está aberta (o analisador só roda aí, para poupar bateria). */
    val nowPlayingVisible = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = MusicRepository(this)
        analyzer = BeatAnalyzer(this)
        equalizer = EqualizerManager(prefs)
        player = PlayerController(this, prefs)
        player.connect()

        scope.launch {
            PlaybackService.audioSessionId.collect { id ->
                analyzer.attach(id)
                equalizer.attach(id)
            }
        }
        scope.launch {
            combine(
                player.state.map { it.isPlaying }.distinctUntilChanged(),
                nowPlayingVisible,
            ) { playing, visible -> playing && visible }
                .distinctUntilChanged()
                .collect { analyzer.setWanted(it) }
        }
    }
}

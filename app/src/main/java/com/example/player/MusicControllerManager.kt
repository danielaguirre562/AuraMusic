package com.example.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.domain.model.RepeatModeState
import com.example.domain.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val sleepTimerRemainingSeconds: Int? = null
)

/**
 * Gestiona la conexión con MusicPlaybackService mediante MediaController (Media3)
 * y mantiene el estado de reproducción, la cola editable y el temporizador de apagado (Sleep Timer).
 */
class MusicControllerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    // Mapa en memoria ligero para resolver Song por ID desde MediaItem.mediaId
    private val queueSongsById = LinkedHashMap<Long, Song>()

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    val equalizerManager = SystemEqualizerManager()

    init {
        connectController()
        scope.launch {
            PlayerInstanceHolder.audioSessionIdFlow.collect { sessionId ->
                if (sessionId > 0) {
                    equalizerManager.attachToAudioSession(sessionId)
                }
            }
        }
    }

    private fun connectController() {
        runCatching {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, MusicPlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    runCatching {
                        val controller = future.get()
                        mediaController = controller
                        attachPlayerListener(controller)
                        syncStateFromController(controller)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncStateFromController(controller)
                if (isPlaying) startPositionTicker() else stopPositionTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncStateFromController(controller)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncStateFromController(controller)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncStateFromController(controller)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                syncStateFromController(controller)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                syncStateFromController(controller)
            }
        })
    }

    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0 }
                        ?: _playbackState.value.currentSong?.durationMs
                        ?: 0L
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(500L) // Actualización cada 500ms para minimizar recomposiciones en gama baja
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun syncStateFromController(controller: MediaController) {
        val count = controller.mediaItemCount
        val currentQueue = ArrayList<Song>(count)
        for (i in 0 until count) {
            val item = controller.getMediaItemAt(i)
            val id = item.mediaId.toLongOrNull()
            val song = if (id != null) queueSongsById[id] else null
            if (song != null) {
                currentQueue.add(song)
            }
        }

        val currentMediaId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        val currentSong = if (currentMediaId != null) queueSongsById[currentMediaId] else currentQueue.getOrNull(controller.currentMediaItemIndex)
        val repeatModeState = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
            Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
            else -> RepeatModeState.OFF
        }

        val duration = controller.duration.takeIf { it > 0 } ?: currentSong?.durationMs ?: 0L
        val position = controller.currentPosition.coerceAtLeast(0L)

        _playbackState.value = _playbackState.value.copy(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            currentPositionMs = position,
            durationMs = duration,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = repeatModeState,
            queue = currentQueue,
            currentQueueIndex = controller.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        )
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        songs.forEach { queueSongsById[it.id] = it }

        val controller = mediaController
        if (controller != null) {
            val mediaItems = songs.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
            syncStateFromController(controller)
        } else {
            // Actualización optimista mientras conecta el servicio
            val chosen = songs[safeIndex]
            _playbackState.value = _playbackState.value.copy(
                currentSong = chosen,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = chosen.durationMs,
                queue = songs,
                currentQueueIndex = safeIndex
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller != null) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
            syncStateFromController(controller)
        } else {
            _playbackState.value = _playbackState.value.copy(isPlaying = !_playbackState.value.isPlaying)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController
        if (controller != null) {
            controller.seekTo(positionMs.coerceAtLeast(0L))
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs.coerceAtLeast(0L))
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        }
        syncStateFromController(controller)
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(controller.mediaItemCount - 1, 0L)
        }
        syncStateFromController(controller)
    }

    fun toggleShuffle() {
        val controller = mediaController
        if (controller != null) {
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            syncStateFromController(controller)
        } else {
            _playbackState.value = _playbackState.value.copy(
                shuffleEnabled = !_playbackState.value.shuffleEnabled
            )
        }
    }

    fun cycleRepeatMode() {
        val controller = mediaController
        if (controller != null) {
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            syncStateFromController(controller)
        } else {
            val nextState = when (_playbackState.value.repeatMode) {
                RepeatModeState.OFF -> RepeatModeState.ALL
                RepeatModeState.ALL -> RepeatModeState.ONE
                RepeatModeState.ONE -> RepeatModeState.OFF
            }
            _playbackState.value = _playbackState.value.copy(repeatMode = nextState)
        }
    }

    fun playQueueItemAt(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, 0L)
            controller.play()
            syncStateFromController(controller)
        }
    }

    fun addSongToQueueNext(song: Song) {
        queueSongsById[song.id] = song
        val controller = mediaController
        if (controller != null) {
            val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertIndex, song.toMediaItem())
            if (controller.mediaItemCount == 1) {
                controller.prepare()
                controller.play()
            }
            syncStateFromController(controller)
        } else {
            val updated = _playbackState.value.queue.toMutableList().apply { add(song) }
            _playbackState.value = _playbackState.value.copy(queue = updated, currentSong = _playbackState.value.currentSong ?: song)
        }
    }

    fun addSongToQueueEnd(song: Song) {
        queueSongsById[song.id] = song
        val controller = mediaController
        if (controller != null) {
            controller.addMediaItem(song.toMediaItem())
            if (controller.mediaItemCount == 1) {
                controller.prepare()
                controller.play()
            }
            syncStateFromController(controller)
        } else {
            val updated = _playbackState.value.queue.toMutableList().apply { add(song) }
            _playbackState.value = _playbackState.value.copy(queue = updated, currentSong = _playbackState.value.currentSong ?: song)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController
        if (controller != null) {
            if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
                syncStateFromController(controller)
            }
        } else {
            val list = _playbackState.value.queue.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                _playbackState.value = _playbackState.value.copy(queue = list)
            }
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController
        if (controller != null) {
            if (index in 0 until controller.mediaItemCount) {
                controller.removeMediaItem(index)
                syncStateFromController(controller)
            }
        } else {
            val list = _playbackState.value.queue.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                _playbackState.value = _playbackState.value.copy(queue = list)
            }
        }
    }

    fun clearQueue() {
        val controller = mediaController
        if (controller != null) {
            controller.clearMediaItems()
            syncStateFromController(controller)
        } else {
            _playbackState.value = _playbackState.value.copy(
                queue = emptyList(),
                currentSong = null,
                isPlaying = false
            )
        }
    }

    /**
     * Temporizador de apagado (Sleep Timer) en minutos. Si minutes == 0, cancela el temporizador.
     */
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
            return
        }

        val totalSeconds = minutes * 60
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = totalSeconds)
        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            if (remaining <= 0) {
                mediaController?.pause()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    sleepTimerRemainingSeconds = null
                )
            }
        }
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        equalizerManager.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }
}

package com.example.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity

/**
 * Servicio en primer plano con Media3 MediaSessionService y ExoPlayer.
 * Soporta controles en pantalla de bloqueo, notificación multimedia del sistema,
 * auriculares Bluetooth, Audio Focus (llamadas entrantes), desconexión de auriculares,
 * WakeLock (reproducción con pantalla apagada) y reproducción gapless.
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configuración de buffers ligera para dispositivos con <= 1 GB de RAM
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 25_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setTargetBufferBytes(2 * 1024 * 1024) // Máximo 2 MB de buffer de audio en RAM
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // Pausa automáticamente al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_LOCAL) // Continúa reproduciendo con pantalla apagada
            .build()
            .apply {
                pauseAtEndOfMediaItems = false // Reproducción gapless continua entre canciones
            }

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            pendingIntentFlags
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        exoPlayer = player
        PlayerInstanceHolder.bindPlayer(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerInstanceHolder.unbindPlayer()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}

/**
 * Puente ligero en proceso para compartir el audioSessionId de ExoPlayer con el Equalizer del sistema.
 */
object PlayerInstanceHolder {
    private val _audioSessionId = kotlinx.coroutines.flow.MutableStateFlow(0)
    val audioSessionIdFlow: kotlinx.coroutines.flow.StateFlow<Int> = _audioSessionId

    @OptIn(UnstableApi::class)
    fun bindPlayer(player: ExoPlayer) {
        _audioSessionId.value = player.audioSessionId
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                _audioSessionId.value = audioSessionId
            }
        })
    }

    fun unbindPlayer() {
        _audioSessionId.value = 0
    }
}

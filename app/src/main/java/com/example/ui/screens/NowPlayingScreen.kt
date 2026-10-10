package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.RepeatModeState
import com.example.domain.model.Song
import com.example.player.PlaybackUiState
import com.example.ui.components.LargeNowPlayingCover

/**
 * Pantalla completa "Ahora reproduciendo" con carátula grande, controles completos,
 * aleatorio, repetir una/todas, cola editable, ecualizador y temporizador de apagado.
 */
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    BackHandler(onBack = onClose)

    val song = playbackState.currentSong
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableFloatStateOf(0f) }

    val totalDurationMs = playbackState.durationMs.coerceAtLeast(1L)
    val displayedPositionMs = if (isUserDraggingSlider) {
        sliderDragPositionMs.toLong()
    } else {
        playbackState.currentPositionMs.coerceIn(0L, totalDurationMs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.30f),
                        bgColor.copy(alpha = 0.96f),
                        bgColor
                    )
                )
            )
            .statusBarsPadding()
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabecera superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_now_playing_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Minimizar reproductor",
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "REPRODUCIENDO DESDE BIBLIOTECA LOCAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = song?.album ?: "Sin álbum",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_queue_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Ver cola de reproducción"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Carátula grande con límite seguro de memoria (512x512 px)
            LargeNowPlayingCover(
                albumArtUri = song?.albumArtUri,
                contentDescription = song?.title ?: "Carátula",
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Título, Artista, Formato y Favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "Sin canción seleccionada",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song?.artist ?: "Artista desconocido",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (song != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = accent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${song.formatBadge} • GAPLESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (song != null) {
                    IconButton(
                        onClick = { onAddToPlaylist(song) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_add_playlist_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlaylistAdd,
                            contentDescription = "Añadir a lista de reproducción",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    enabled = song != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_fav_btn")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de progreso interactiva
            Slider(
                value = displayedPositionMs.toFloat(),
                onValueChange = {
                    isUserDraggingSlider = true
                    sliderDragPositionMs = it
                },
                onValueChangeFinished = {
                    onSeekTo(sliderDragPositionMs.toLong())
                    isUserDraggingSlider = false
                },
                valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Song.formatDuration(displayedPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Song.formatDuration(playbackState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controles principales: Shuffle, Anterior, Play/Pause, Siguiente, Repetir (Off / All / One)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("shuffle_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Modo aleatorio",
                        tint = if (playbackState.shuffleEnabled) {
                            accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("prev_track_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Canción anterior",
                        modifier = Modifier.size(36.dp)
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("play_pause_main_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("next_track_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente canción",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("repeat_mode_btn")
                ) {
                    val repeatIcon = when (playbackState.repeatMode) {
                        RepeatModeState.ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }
                    val repeatTint = when (playbackState.repeatMode) {
                        RepeatModeState.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        RepeatModeState.ALL, RepeatModeState.ONE -> accent
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Modo de repetición",
                        tint = repeatTint
                    )
                }
            }

            // Etiqueta explicativa del modo de repetición actual
            val repeatModeLabel = when (playbackState.repeatMode) {
                RepeatModeState.OFF -> "Repetición desactivada"
                RepeatModeState.ALL -> "Repetir todas las canciones"
                RepeatModeState.ONE -> "Repetir esta canción"
            }
            Text(
                text = if (playbackState.shuffleEnabled) "$repeatModeLabel • Aleatorio activo" else repeatModeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Accesos rápidos inferiores: Ecualizador del sistema, Temporizador de apagado y Cola
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilterChip(
                    selected = false,
                    onClick = onOpenEqualizer,
                    label = { Text("Ecualizador") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("open_eq_chip")
                )

                val sleepActive = playbackState.sleepTimerRemainingSeconds != null
                FilterChip(
                    selected = sleepActive,
                    onClick = onOpenSleepTimer,
                    label = {
                        val text = if (sleepActive) {
                            val mins = (playbackState.sleepTimerRemainingSeconds ?: 0) / 60
                            val secs = (playbackState.sleepTimerRemainingSeconds ?: 0) % 60
                            "Apagado: %d:%02d".format(mins, secs)
                        } else {
                            "Temporizador"
                        }
                        Text(text, fontWeight = FontWeight.Medium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("open_sleep_timer_chip")
                )

                FilterChip(
                    selected = false,
                    onClick = onOpenQueue,
                    label = { Text("Cola (${playbackState.queue.size})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("open_queue_chip")
                )
            }
        }
    }
}

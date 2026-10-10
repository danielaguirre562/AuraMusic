package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.CustomizableAppBackground
import com.example.ui.components.PersistentMiniPlayerBar
import com.example.ui.screens.AddToPlaylistDialog
import com.example.ui.screens.EditableQueueSheet
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.screens.LibraryHomeScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SleepTimerDialog
import com.example.ui.theme.AuraMusicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels {
        MusicViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraMusicAppContent(viewModel = viewModel)
        }
    }
}

@Composable
fun AuraMusicAppContent(viewModel: MusicViewModel) {
    val context = LocalContext.current

    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistsState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val dynamicPaletteColor by viewModel.dynamicPaletteColor.collectAsStateWithLifecycle()

    // Permiso de lectura de audio según versión de Android (Android 13+ READ_MEDIA_AUDIO vs READ_EXTERNAL_STORAGE)
    val requiredAudioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                requiredAudioPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[requiredAudioPermission] == true
        hasStoragePermission = granted
        if (granted) {
            viewModel.rescanLibrary()
        }
    }

    // Color de acento efectivo (Palette automático desde la carátula o color elegido por el usuario)
    val effectiveAccentArgb = remember(
        preferences.useDynamicAlbumPalette,
        preferences.customAccentColorArgb,
        dynamicPaletteColor
    ) {
        if (preferences.useDynamicAlbumPalette && dynamicPaletteColor != null) {
            dynamicPaletteColor ?: preferences.customAccentColorArgb
        } else {
            preferences.customAccentColorArgb
        }
    }

    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isQueueSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isEqualizerSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isSleepTimerDialogOpen by rememberSaveable { mutableStateOf(false) }
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(libraryState.lastScanMessage) {
        val msg = libraryState.lastScanMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearScanMessage()
        }
    }

    val progressFraction by remember(playbackState.currentPositionMs, playbackState.durationMs) {
        derivedStateOf {
            val dur = playbackState.durationMs
            if (dur > 0L) {
                (playbackState.currentPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    AuraMusicTheme(
        themeMode = preferences.themeMode,
        accentColorArgb = effectiveAccentArgb,
        solidBackgroundArgb = preferences.solidBackgroundColorArgb
    ) {
        CustomizableAppBackground(
            customBackgroundUri = preferences.customBackgroundUri,
            blurRadius = preferences.backgroundBlurRadius,
            dimAlpha = preferences.backgroundDimAlpha,
            accentColor = Color(effectiveAccentArgb),
            baseBackgroundColor = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    // Barra de reproducción inferior (mini player) siempre visible, estilo Spotify
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        PersistentMiniPlayerBar(
                            currentSong = playbackState.currentSong,
                            isPlaying = playbackState.isPlaying,
                            progressFraction = progressFraction,
                            sleepTimerRemainingSeconds = playbackState.sleepTimerRemainingSeconds,
                            onBarClick = { isNowPlayingOpen = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipPrevious = viewModel::skipPrevious,
                            onSkipNext = viewModel::skipNext,
                            onOpenQueue = { isQueueSheetOpen = true }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isSettingsOpen) {
                        SettingsScreen(
                            preferences = preferences,
                            discoveredFolders = libraryState.allKnownFolderPaths,
                            onBack = { isSettingsOpen = false },
                            onMinDurationChange = viewModel::setMinDurationSeconds,
                            onAddExcludedFolder = viewModel::addExcludedFolder,
                            onRemoveExcludedFolder = viewModel::removeExcludedFolder,
                            onThemeModeChange = viewModel::setThemeMode,
                            onAccentColorChange = viewModel::setCustomAccentColor,
                            onDynamicPaletteToggle = viewModel::setUseDynamicAlbumPalette,
                            onSelectGalleryBackground = viewModel::setCustomBackgroundUri,
                            onBlurRadiusChange = viewModel::setBackgroundBlurRadius,
                            onDimAlphaChange = viewModel::setBackgroundDimAlpha,
                            onSolidBackgroundChange = viewModel::setSolidBackgroundColor,
                            onRescanLibrary = viewModel::rescanLibrary,
                            onGenerateSampleSongs = viewModel::generateLocalSampleSongs
                        )
                    } else {
                        LibraryHomeScreen(
                            libraryState = libraryState,
                            playlists = playlists,
                            favoriteSongIds = favoriteSongIds,
                            currentSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying,
                            searchQuery = searchQuery,
                            minDurationSeconds = preferences.minDurationSeconds,
                            hasStoragePermission = hasStoragePermission,
                            onRequestStoragePermission = {
                                val perms = buildList {
                                    add(requiredAudioPermission)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                permissionLauncher.launch(perms)
                            },
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onPlaySongs = { songs, startIdx ->
                                viewModel.playSongs(songs, startIdx)
                            },
                            onShuffleSongs = { songs ->
                                viewModel.shuffleAllSongs(songs)
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onPlayNext = viewModel::addSongToQueueNext,
                            onAddToQueue = viewModel::addSongToQueueEnd,
                            onOpenAddToPlaylistDialog = { song -> songForPlaylistDialog = song },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onRemoveSongFromPlaylist = viewModel::removeSongFromPlaylist,
                            onExcludeFolder = viewModel::addExcludedFolder,
                            onGenerateSampleSongs = viewModel::generateLocalSampleSongs,
                            onOpenEqualizer = { isEqualizerSheetOpen = true },
                            onOpenSettings = { isSettingsOpen = true }
                        )
                    }

                    // Pantalla completa de "Ahora reproduciendo"
                    AnimatedVisibility(
                        visible = isNowPlayingOpen && playbackState.currentSong != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        val currentSong = playbackState.currentSong
                        NowPlayingScreen(
                            playbackState = playbackState,
                            isFavorite = currentSong != null && currentSong.id in favoriteSongIds,
                            onClose = { isNowPlayingOpen = false },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekTo = viewModel::seekTo,
                            onSkipPrevious = viewModel::skipPrevious,
                            onSkipNext = viewModel::skipNext,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onCycleRepeat = viewModel::cycleRepeatMode,
                            onToggleFavorite = {
                                if (currentSong != null) {
                                    viewModel.toggleFavorite(currentSong.id)
                                }
                            },
                            onOpenQueue = { isQueueSheetOpen = true },
                            onOpenEqualizer = { isEqualizerSheetOpen = true },
                            onOpenSleepTimer = { isSleepTimerDialogOpen = true },
                            onAddToPlaylist = { song -> songForPlaylistDialog = song }
                        )
                    }
                }
            }

            if (isQueueSheetOpen) {
                EditableQueueSheet(
                    queue = playbackState.queue,
                    currentIndex = playbackState.currentQueueIndex,
                    onDismiss = { isQueueSheetOpen = false },
                    onPlayAtIndex = viewModel::playQueueItemAt,
                    onMoveItem = viewModel::moveQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onClearQueue = viewModel::clearQueue
                )
            }

            if (isEqualizerSheetOpen) {
                EqualizerBottomSheet(
                    equalizerState = equalizerState,
                    onDismiss = { isEqualizerSheetOpen = false },
                    onToggleEnabled = viewModel::setEqualizerEnabled,
                    onBandLevelChanged = viewModel::setEqualizerBandLevel,
                    onSelectPreset = viewModel::selectEqualizerPreset,
                    onResetFlat = viewModel::resetEqualizerFlat
                )
            }

            if (isSleepTimerDialogOpen) {
                SleepTimerDialog(
                    remainingSeconds = playbackState.sleepTimerRemainingSeconds,
                    onDismiss = { isSleepTimerDialogOpen = false },
                    onSelectMinutes = viewModel::setSleepTimerMinutes
                )
            }

            val targetSong = songForPlaylistDialog
            if (targetSong != null) {
                AddToPlaylistDialog(
                    song = targetSong,
                    playlists = playlists,
                    onDismiss = { songForPlaylistDialog = null },
                    onAddToExistingPlaylist = viewModel::addSongToPlaylist,
                    onCreatePlaylistWithSong = { name, songId ->
                        viewModel.createPlaylist(name, songId)
                    }
                )
            }
        }
    }
}

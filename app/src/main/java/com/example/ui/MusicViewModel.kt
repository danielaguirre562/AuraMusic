package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AuraMusicApplication
import com.example.data.preferences.UserPreferences
import com.example.domain.model.AlbumItem
import com.example.domain.model.ArtistItem
import com.example.domain.model.FolderItem
import com.example.domain.model.PlaylistWithSongs
import com.example.domain.model.SortOrder
import com.example.domain.model.Song
import com.example.domain.model.ThemeMode
import com.example.player.EqualizerUiState
import com.example.player.MusicControllerManager
import com.example.player.PlaybackUiState
import com.example.ui.util.PaletteColorExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibraryCatalogState(
    val isScanning: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val allKnownFolderPaths: List<String> = emptyList(),
    val lastScanMessage: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AuraMusicApplication
    private val musicRepository = app.musicRepository
    private val preferencesRepository = app.preferencesRepository
    private val controllerManager = MusicControllerManager(app)

    val preferencesState: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    val playbackState: StateFlow<PlaybackUiState> = controllerManager.playbackState
    val equalizerState: StateFlow<EqualizerUiState> = controllerManager.equalizerManager.state

    val favoriteSongIds: StateFlow<Set<Long>> = musicRepository.favoriteSongIdsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _scanMessage = MutableStateFlow<String?>(null)
    private val _allDiscoveredFolders = MutableStateFlow<Set<String>>(emptySet())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dynamicPaletteColor = MutableStateFlow<Long?>(null)
    val dynamicPaletteColor: StateFlow<Long?> = _dynamicPaletteColor.asStateFlow()

    val playlistsState: StateFlow<List<PlaylistWithSongs>> =
        musicRepository.observePlaylistsWithSongs(_rawSongs)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val libraryState: StateFlow<LibraryCatalogState> = combine(
        _rawSongs,
        _isScanning,
        _scanMessage,
        _allDiscoveredFolders,
        preferencesState.map { it.sortOrder }.distinctUntilChanged()
    ) { songs, isScanning, message, knownFolders, sortOrder ->
        val sortedSongs = when (sortOrder) {
            SortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION_DESC -> songs.sortedByDescending { it.durationMs }
            SortOrder.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
        }

        val albums = sortedSongs
            .groupBy { "${it.albumId}_${it.album.lowercase()}" }
            .map { (_, albumSongs) ->
                val first = albumSongs.first()
                AlbumItem(
                    id = first.albumId,
                    name = first.album,
                    artist = first.artist,
                    albumArtUri = albumSongs.firstNotNullOfOrNull { it.albumArtUri },
                    songCount = albumSongs.size,
                    songs = albumSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        val artists = sortedSongs
            .groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                ArtistItem(
                    name = artistName,
                    songCount = artistSongs.size,
                    albumCount = artistSongs.map { it.album.lowercase() }.distinct().size,
                    representativeArtUri = artistSongs.firstNotNullOfOrNull { it.albumArtUri },
                    songs = artistSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        val folders = sortedSongs
            .groupBy { it.folderPath }
            .map { (path, folderSongs) ->
                FolderItem(
                    path = path,
                    name = folderSongs.first().folderName,
                    songCount = folderSongs.size,
                    songs = folderSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        LibraryCatalogState(
            isScanning = isScanning,
            songs = sortedSongs,
            albums = albums,
            artists = artists,
            folders = folders,
            allKnownFolderPaths = (knownFolders + folders.map { it.path }).sorted(),
            lastScanMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryCatalogState(isScanning = true)
    )

    init {
        // Re-escanear automáticamente cuando cambia el filtro de duración mínima o las carpetas excluidas
        viewModelScope.launch {
            // Al iniciar por primera vez, garantizamos pistas locales reales por si el dispositivo/emulador está limpio
            musicRepository.generateLocalDemoTracks(force = false)
            preferencesRepository.preferencesFlow
                .map { Triple(it.minDurationSeconds, it.excludedFolders, it.listMemoryLimit) }
                .distinctUntilChanged()
                .collect { (minDurationSec, excluded, limit) ->
                    performLibraryScan(minDurationSec, excluded, limit)
                }
        }

        // Extraer color de acento automáticamente con Palette al cambiar la canción actual
        viewModelScope.launch {
            playbackState
                .map { it.currentSong }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { currentSong ->
                    _dynamicPaletteColor.value = PaletteColorExtractor.extractAccentColor(app, currentSong)
                }
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            val prefs = preferencesState.value
            performLibraryScan(prefs.minDurationSeconds, prefs.excludedFolders, prefs.listMemoryLimit)
        }
    }

    fun generateLocalSampleSongs() {
        viewModelScope.launch {
            _isScanning.value = true
            val created = musicRepository.generateLocalDemoTracks(force = true)
            val prefs = preferencesState.first()
            performLibraryScan(prefs.minDurationSeconds, prefs.excludedFolders, prefs.listMemoryLimit)
            _scanMessage.value = if (created > 0) {
                "Se generaron $created pistas WAV locales listas para reproducir"
            } else {
                "Biblioteca local actualizada"
            }
        }
    }

    private suspend fun performLibraryScan(
        minDurationSeconds: Int,
        excludedFolders: Set<String>,
        maxLimit: Int
    ) {
        _isScanning.value = true
        val scanned = musicRepository.scanLocalLibrary(
            minDurationSeconds = minDurationSeconds,
            excludedFolders = excludedFolders,
            maxLimit = maxLimit
        )
        _rawSongs.value = scanned
        _allDiscoveredFolders.value = _allDiscoveredFolders.value + scanned.map { it.folderPath } + excludedFolders
        _isScanning.value = false
    }

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Controles de Reproducción y Cola ---

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        controllerManager.playQueue(songs, startIndex)
    }

    fun shuffleAllSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        controllerManager.playQueue(shuffled, 0)
    }

    fun togglePlayPause() = controllerManager.togglePlayPause()
    fun seekTo(positionMs: Long) = controllerManager.seekTo(positionMs)
    fun skipNext() = controllerManager.skipNext()
    fun skipPrevious() = controllerManager.skipPrevious()
    fun toggleShuffle() = controllerManager.toggleShuffle()
    fun cycleRepeatMode() = controllerManager.cycleRepeatMode()

    fun playQueueItemAt(index: Int) = controllerManager.playQueueItemAt(index)
    fun addSongToQueueNext(song: Song) = controllerManager.addSongToQueueNext(song)
    fun addSongToQueueEnd(song: Song) = controllerManager.addSongToQueueEnd(song)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = controllerManager.moveQueueItem(fromIndex, toIndex)
    fun removeQueueItem(index: Int) = controllerManager.removeQueueItem(index)
    fun clearQueue() = controllerManager.clearQueue()

    // --- Temporizador de Apagado (Sleep Timer) ---

    fun setSleepTimerMinutes(minutes: Int) = controllerManager.setSleepTimerMinutes(minutes)

    // --- Ecualizador del Sistema ---

    fun setEqualizerEnabled(enabled: Boolean) = controllerManager.equalizerManager.setEnabled(enabled)
    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) =
        controllerManager.equalizerManager.setBandLevel(bandIndex, levelMb)
    fun selectEqualizerPreset(presetIndex: Int) =
        controllerManager.equalizerManager.selectPreset(presetIndex)
    fun resetEqualizerFlat() = controllerManager.equalizerManager.resetFlat()

    // --- Favoritos y Listas de Reproducción ---

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(songId)
        }
    }

    fun createPlaylist(name: String, initialSongId: Long? = null) {
        viewModelScope.launch {
            val playlistId = musicRepository.createPlaylist(name)
            if (playlistId > 0 && initialSongId != null) {
                musicRepository.addSongToPlaylist(playlistId, initialSongId, 0)
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            val currentCount = playlistsState.value.firstOrNull { it.id == playlistId }?.songs?.size ?: 0
            musicRepository.addSongToPlaylist(playlistId, songId, currentCount)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            musicRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // --- Preferencias de Usuario (DataStore) ---

    fun setMinDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setMinDurationSeconds(seconds)
        }
    }

    fun addExcludedFolder(path: String) {
        viewModelScope.launch {
            preferencesRepository.addExcludedFolder(path)
        }
    }

    fun removeExcludedFolder(path: String) {
        viewModelScope.launch {
            preferencesRepository.removeExcludedFolder(path)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setCustomAccentColor(argb: Long) {
        viewModelScope.launch {
            preferencesRepository.setCustomAccentColor(argb)
        }
    }

    fun setUseDynamicAlbumPalette(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseDynamicAlbumPalette(enabled)
        }
    }

    fun setCustomBackgroundUri(uri: Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                runCatching {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            preferencesRepository.setCustomBackgroundUri(uri?.toString())
        }
    }

    fun setBackgroundBlurRadius(radius: Float) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundBlurRadius(radius)
        }
    }

    fun setBackgroundDimAlpha(alpha: Float) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundDimAlpha(alpha)
        }
    }

    fun setSolidBackgroundColor(argb: Long) {
        viewModelScope.launch {
            preferencesRepository.setSolidBackgroundColor(argb)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            preferencesRepository.setSortOrder(order)
        }
    }

    override fun onCleared() {
        controllerManager.release()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MusicViewModel(application) as T
        }
    }
}

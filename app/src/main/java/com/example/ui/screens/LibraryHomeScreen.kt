package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlbumItem
import com.example.domain.model.ArtistItem
import com.example.domain.model.FolderItem
import com.example.domain.model.PlaylistWithSongs
import com.example.domain.model.Song
import com.example.ui.LibraryCatalogState
import com.example.ui.components.SongListItem
import com.example.ui.components.SongThumbnailImage

private sealed interface CollectionDetail {
    data class AlbumDetail(val album: AlbumItem) : CollectionDetail
    data class ArtistDetail(val artist: ArtistItem) : CollectionDetail
    data class FolderDetail(val folder: FolderItem) : CollectionDetail
    data class PlaylistDetail(val playlistId: Long) : CollectionDetail
    data object FavoritesDetail : CollectionDetail
}

/**
 * Pantalla principal de la biblioteca musical con:
 * - Buscador global rápido (filtra con derivedStateOf sin bloquear la UI).
 * - Pestañas: Canciones, Álbumes, Artistas, Carpetas y Listas/Favoritos.
 * - LazyColumn con keys estables para máximo rendimiento en dispositivos de 1 GB de RAM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    libraryState: LibraryCatalogState,
    playlists: List<PlaylistWithSongs>,
    favoriteSongIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    searchQuery: String,
    minDurationSeconds: Int,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenAddToPlaylistDialog: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    onExcludeFolder: (String) -> Unit,
    onGenerateSampleSongs: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var activeDetail by remember { mutableStateOf<CollectionDetail?>(null) }
    var newPlaylistInput by remember { mutableStateOf("") }

    // Filtrado global rápido optimizado con derivedStateOf
    val filteredSongs by remember(libraryState.songs, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                libraryState.songs
            } else {
                libraryState.songs.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.folderName.lowercase().contains(q)
                }
            }
        }
    }

    val filteredAlbums by remember(libraryState.albums, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                libraryState.albums
            } else {
                libraryState.albums.filter {
                    it.name.lowercase().contains(q) || it.artist.lowercase().contains(q)
                }
            }
        }
    }

    val filteredArtists by remember(libraryState.artists, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                libraryState.artists
            } else {
                libraryState.artists.filter { it.name.lowercase().contains(q) }
            }
        }
    }

    val filteredFolders by remember(libraryState.folders, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                libraryState.folders
            } else {
                libraryState.folders.filter {
                    it.name.lowercase().contains(q) || it.path.lowercase().contains(q)
                }
            }
        }
    }

    val favoriteSongs by remember(libraryState.songs, favoriteSongIds) {
        derivedStateOf {
            libraryState.songs.filter { it.id in favoriteSongIds }
        }
    }

    // Si estamos dentro de una vista de detalle (Álbum, Artista, Carpeta o Playlist), manejamos el botón Atrás
    if (activeDetail != null) {
        BackHandler { activeDetail = null }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Barra superior con marca AuraMusic, buscador global rápido y accesos a Ecualizador y Ajustes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AuraMusic",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${libraryState.songs.size} canciones • Filtro ≥ ${minDurationSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_eq_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Ecualizador",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ajustes y personalización",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Buscador global rápido
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar canciones, álbumes, artistas o carpetas…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("global_search_input")
        )

        // Banner discreto para conceder permiso de lectura de MediaStore si el usuario aún no lo ha concedido
        if (!hasStoragePermission) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Permite el acceso al almacenamiento para escanear toda tu música local.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestStoragePermission,
                        modifier = Modifier.testTag("grant_storage_permission_btn")
                    ) {
                        Text("Permitir")
                    }
                }
            }
        }

        val detail = activeDetail
        if (detail != null) {
            CollectionDetailView(
                detail = detail,
                playlists = playlists,
                favoriteSongs = favoriteSongs,
                favoriteSongIds = favoriteSongIds,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                onBack = { activeDetail = null },
                onPlaySongs = onPlaySongs,
                onShuffleSongs = onShuffleSongs,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onOpenAddToPlaylistDialog = onOpenAddToPlaylistDialog,
                onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                onExcludeFolder = { path ->
                    onExcludeFolder(path)
                    activeDetail = null
                }
            )
        } else {
            val tabs = listOf(
                "Canciones" to Icons.Filled.MusicNote,
                "Álbumes" to Icons.Filled.Album,
                "Artistas" to Icons.Filled.Person,
                "Carpetas" to Icons.Filled.Folder,
                "Listas" to Icons.Filled.QueueMusic
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { idx, (title, icon) ->
                    Tab(
                        selected = selectedTabIndex == idx,
                        onClick = { selectedTabIndex = idx },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(title)
                            }
                        },
                        modifier = Modifier.testTag("tab_$idx")
                    )
                }
            }

            if (libraryState.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Escaneando música local…")
                    }
                }
            } else {
                when (selectedTabIndex) {
                    0 -> SongsTabContent(
                        songs = filteredSongs,
                        favoriteSongIds = favoriteSongIds,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onPlaySongs = onPlaySongs,
                        onShuffleSongs = onShuffleSongs,
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onOpenAddToPlaylistDialog = onOpenAddToPlaylistDialog,
                        onGenerateSampleSongs = onGenerateSampleSongs,
                        modifier = Modifier.weight(1f)
                    )

                    1 -> AlbumsTabContent(
                        albums = filteredAlbums,
                        onAlbumClick = { activeDetail = CollectionDetail.AlbumDetail(it) },
                        modifier = Modifier.weight(1f)
                    )

                    2 -> ArtistsTabContent(
                        artists = filteredArtists,
                        onArtistClick = { activeDetail = CollectionDetail.ArtistDetail(it) },
                        modifier = Modifier.weight(1f)
                    )

                    3 -> FoldersTabContent(
                        folders = filteredFolders,
                        onFolderClick = { activeDetail = CollectionDetail.FolderDetail(it) },
                        onExcludeFolder = onExcludeFolder,
                        modifier = Modifier.weight(1f)
                    )

                    4 -> PlaylistsTabContent(
                        playlists = playlists,
                        favoriteSongs = favoriteSongs,
                        newPlaylistInput = newPlaylistInput,
                        onNewPlaylistInputChange = { newPlaylistInput = it },
                        onCreatePlaylist = {
                            if (newPlaylistInput.isNotBlank()) {
                                onCreatePlaylist(newPlaylistInput.trim())
                                newPlaylistInput = ""
                            }
                        },
                        onDeletePlaylist = onDeletePlaylist,
                        onOpenFavorites = { activeDetail = CollectionDetail.FavoritesDetail },
                        onOpenPlaylist = { activeDetail = CollectionDetail.PlaylistDetail(it.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SongsTabContent(
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenAddToPlaylistDialog: (Song) -> Unit,
    onGenerateSampleSongs: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No se encontraron canciones con los filtros actuales",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Revisa el filtro de duración mínima (50s) en Ajustes o genera pistas locales de prueba.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onGenerateSampleSongs) {
                    Text("Generar pistas locales de prueba")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("songs_lazy_column"),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header_controls") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlaySongs(songs, 0) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_all_songs_btn")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reproducir (${songs.size})")
                }

                FilledTonalButton(
                    onClick = { onShuffleSongs(songs) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shuffle_all_songs_btn")
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }

        itemsIndexed(
            items = songs,
            key = { _, song -> song.id },
            contentType = { _, _ -> "song_item" }
        ) { index, song ->
            SongListItem(
                song = song,
                isCurrentSong = song.id == currentSongId,
                isPlaying = isPlaying,
                isFavorite = song.id in favoriteSongIds,
                onSongClick = { onPlaySongs(songs, index) },
                onToggleFavorite = { onToggleFavorite(song.id) },
                onPlayNext = { onPlayNext(song) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { onOpenAddToPlaylistDialog(song) }
            )
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("albums_grid"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = albums,
            key = { "${it.id}_${it.name}" }
        ) { album ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album) }
                    .testTag("album_card_${album.name}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SongThumbnailImage(
                        albumArtUri = album.albumArtUri,
                        contentDescription = album.name,
                        sizeDp = 128.dp,
                        cornerRadiusDp = 12.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} • ${album.songCount} pistas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("artists_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = artists,
            key = { it.name }
        ) { artist ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongThumbnailImage(
                        albumArtUri = artist.representativeArtUri,
                        contentDescription = artist.name,
                        sizeDp = 52.dp,
                        cornerRadiusDp = 26.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${artist.songCount} canciones • ${artist.albumCount} álbumes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<FolderItem>,
    onFolderClick: (FolderItem) -> Unit,
    onExcludeFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("folders_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = folders,
            key = { it.path }
        ) { folder ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folder) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${folder.songCount} canciones • ${folder.path}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = { onExcludeFolder(folder.path) },
                        modifier = Modifier.testTag("exclude_folder_btn_${folder.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOff,
                            contentDescription = "Excluir carpeta",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ocultar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<PlaylistWithSongs>,
    favoriteSongs: List<Song>,
    newPlaylistInput: String,
    onNewPlaylistInputChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (PlaylistWithSongs) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tarjeta destacada de Favoritos
        item(key = "favorites_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFavorites)
                    .testTag("favorites_playlist_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tus Favoritos",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${favoriteSongs.size} canciones guardadas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Crear nueva lista de reproducción propia
        item(key = "create_playlist_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newPlaylistInput,
                    onValueChange = onNewPlaylistInputChange,
                    label = { Text("Crear nueva lista de reproducción…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_playlist_input")
                )
                Button(
                    onClick = onCreatePlaylist,
                    enabled = newPlaylistInput.isNotBlank(),
                    modifier = Modifier.testTag("create_playlist_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear lista")
                }
            }
        }

        items(
            items = playlists,
            key = { it.id }
        ) { playlist ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(playlist) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${playlist.songs.size} canciones",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onDeletePlaylist(playlist.id) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar lista",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailView(
    detail: CollectionDetail,
    playlists: List<PlaylistWithSongs>,
    favoriteSongs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenAddToPlaylistDialog: (Song) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    onExcludeFolder: (String) -> Unit
) {
    val title: String
    val subtitle: String
    val songs: List<Song>
    var playlistIdForRemoval: Long? = null
    var folderPathToExclude: String? = null

    when (detail) {
        is CollectionDetail.AlbumDetail -> {
            title = detail.album.name
            subtitle = "${detail.album.artist} • ${detail.album.songs.size} canciones"
            songs = detail.album.songs
        }
        is CollectionDetail.ArtistDetail -> {
            title = detail.artist.name
            subtitle = "${detail.artist.songs.size} canciones • ${detail.artist.albumCount} álbumes"
            songs = detail.artist.songs
        }
        is CollectionDetail.FolderDetail -> {
            title = detail.folder.name
            subtitle = detail.folder.path
            songs = detail.folder.songs
            folderPathToExclude = detail.folder.path
        }
        is CollectionDetail.PlaylistDetail -> {
            val found = playlists.firstOrNull { it.id == detail.playlistId }
            title = found?.name ?: "Lista de reproducción"
            songs = found?.songs ?: emptyList()
            subtitle = "${songs.size} canciones en la lista"
            playlistIdForRemoval = detail.playlistId
        }
        CollectionDetail.FavoritesDetail -> {
            title = "Favoritos"
            subtitle = "${favoriteSongs.size} canciones favoritas"
            songs = favoriteSongs
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (folderPathToExclude != null) {
                OutlinedButton(onClick = { onExcludeFolder(folderPathToExclude) }) {
                    Text("Excluir carpeta")
                }
            }
        }

        if (songs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlaySongs(songs, 0) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reproducir")
                }
                FilledTonalButton(
                    onClick = { onShuffleSongs(songs) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id }
            ) { idx, song ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SongListItem(
                            song = song,
                            isCurrentSong = song.id == currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = song.id in favoriteSongIds,
                            onSongClick = { onPlaySongs(songs, idx) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                            onPlayNext = { onPlayNext(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onAddToPlaylist = { onOpenAddToPlaylistDialog(song) }
                        )
                    }
                    if (playlistIdForRemoval != null) {
                        IconButton(
                            onClick = { onRemoveSongFromPlaylist(playlistIdForRemoval, song.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Quitar de la lista"
                            )
                        }
                    }
                }
            }
        }
    }
}

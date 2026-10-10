package com.example.data.repository

import android.content.Context
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistSongCrossRef
import com.example.data.scanner.LocalMediaScanner
import com.example.domain.model.PlaylistWithSongs
import com.example.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Repositorio central que coordina el escaneo local de MediaStore y las listas/favoritos en Room.
 */
class MusicRepository(
    context: Context,
    private val playlistDao: PlaylistDao
) {
    private val scanner = LocalMediaScanner(context.applicationContext)

    val favoriteSongIdsFlow: Flow<Set<Long>> =
        playlistDao.observeFavoriteSongIds().map { it.toSet() }

    suspend fun scanLocalLibrary(
        minDurationSeconds: Int,
        excludedFolders: Set<String>,
        maxLimit: Int = 500
    ): List<Song> {
        return scanner.scanMusic(
            minDurationSeconds = minDurationSeconds,
            excludedFolders = excludedFolders,
            maxLimit = maxLimit
        )
    }

    suspend fun generateLocalDemoTracks(force: Boolean = false): Int {
        return scanner.generateLocalSampleTracksIfNeeded(force)
    }

    suspend fun toggleFavorite(songId: Long) {
        if (playlistDao.isFavorite(songId)) {
            playlistDao.removeFavorite(songId)
        } else {
            playlistDao.addFavorite(FavoriteSongEntity(songId = songId))
        }
    }

    fun observePlaylistsWithSongs(songsFlow: Flow<List<Song>>): Flow<List<PlaylistWithSongs>> {
        return combine(
            playlistDao.observePlaylists(),
            playlistDao.observeAllPlaylistSongs(),
            songsFlow
        ) { playlists, crossRefs, allSongs ->
            val songMap = allSongs.associateBy { it.id }
            val refsByPlaylist = crossRefs.groupBy { it.playlistId }
            playlists.map { playlist ->
                val refs = refsByPlaylist[playlist.id].orEmpty().sortedBy { it.position }
                val songs = refs.mapNotNull { songMap[it.songId] }
                PlaylistWithSongs(
                    id = playlist.id,
                    name = playlist.name,
                    createdAt = playlist.createdAt,
                    songs = songs
                )
            }
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        return playlistDao.createPlaylist(PlaylistEntity(name = clean))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            playlistDao.renamePlaylist(playlistId, clean)
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clearPlaylistSongs(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int = 0) {
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = position
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}

package com.example.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * Modelos inmutables (@Immutable) para que Jetpack Compose omita recomposiciones
 * innecesarias en LazyColumn y vistas de detalle, crítico para dispositivos con 1 GB de RAM.
 */
@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val folderPath: String,
    val folderName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val dateAdded: Long
) {
    val durationFormatted: String
        get() = formatDuration(durationMs)

    val formatBadge: String
        get() {
            val lower = mimeType.lowercase()
            return when {
                lower.contains("flac") -> "FLAC"
                lower.contains("wav") || lower.contains("x-wav") -> "WAV"
                lower.contains("opus") -> "OPUS"
                lower.contains("ogg") || lower.contains("vorbis") -> "OGG"
                lower.contains("mp4") || lower.contains("m4a") || lower.contains("aac") -> "M4A/AAC"
                lower.contains("midi") || lower.contains("mid") -> "MIDI"
                lower.contains("mpeg") || lower.contains("mp3") -> "MP3"
                else -> "AUDIO"
            }
        }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}

@Immutable
data class AlbumItem(
    val id: Long,
    val name: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val songs: List<Song>
)

@Immutable
data class ArtistItem(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val representativeArtUri: Uri?,
    val songs: List<Song>
)

@Immutable
data class FolderItem(
    val path: String,
    val name: String,
    val songCount: Int,
    val songs: List<Song>
)

@Immutable
data class PlaylistWithSongs(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songs: List<Song>
)

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED
}

enum class RepeatModeState {
    OFF,
    ONE,
    ALL
}

enum class SortOrder {
    TITLE_ASC,
    DATE_ADDED_DESC,
    DURATION_DESC,
    ARTIST_ASC
}

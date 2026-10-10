package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Escáner de música 100% local mediante MediaStore y sistema de archivos.
 * Soporta MP3, AAC, M4A, FLAC, OGG, OPUS, WAV y MIDI.
 * Incluye además un generador de pistas sintéticas reales (.wav) en el almacenamiento local
 * de la app para probar el reproductor en dispositivos o emuladores sin música previa.
 */
class LocalMediaScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "aac", "m4a", "flac", "ogg", "opus", "wav", "mid", "midi", "ota", "rtttl"
    )

    suspend fun scanMusic(
        minDurationSeconds: Int,
        excludedFolders: Set<String>,
        maxLimit: Int = 600
    ): List<Song> = withContext(Dispatchers.IO) {
        val minDurationMs = minDurationSeconds * 1000L
        val normalizedExcluded = excludedFolders.map { it.lowercase().trimEnd('/') }
        val result = ArrayList<Song>(256)

        // 1. Escanear MediaStore del dispositivo (si el permiso de almacenamiento fue concedido)
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                }
            }.toTypedArray()

            // Filtramos desde SQL la duración mínima para ahorrar memoria y CPU en dispositivos de 1 GB RAM
            val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
            val selectionArgs = arrayOf(minDurationMs.toString())
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext() && result.size < maxLimit) {
                    val id = cursor.getLong(idCol)
                    val duration = cursor.getLong(durationCol)
                    if (duration < minDurationMs) continue

                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val relPath = if (relPathCol != -1) cursor.getString(relPathCol) ?: "" else ""
                    val folderPath = when {
                        rawPath.isNotBlank() -> File(rawPath).parent ?: "/Music"
                        relPath.isNotBlank() -> "/storage/emulated/0/" + relPath.trimEnd('/')
                        else -> "/storage/emulated/0/Music"
                    }

                    // Verificar si la carpeta está en la lista de carpetas excluidas
                    val lowerFolder = folderPath.lowercase().trimEnd('/')
                    val isExcluded = normalizedExcluded.any { excluded ->
                        excluded.isNotEmpty() && (lowerFolder == excluded || lowerFolder.startsWith("$excluded/"))
                    }
                    if (isExcluded) continue

                    // Filtrar audios de WhatsApp por ruta si el usuario mantiene el filtro o carpeta
                    val fileExt = rawPath.substringAfterLast('.', "").lowercase()
                    val mimeType = cursor.getString(mimeCol) ?: "audio/mpeg"
                    if (fileExt.isNotEmpty() && fileExt !in supportedExtensions && !mimeType.startsWith("audio/")) {
                        continue
                    }

                    val title = (cursor.getString(titleCol) ?: "").ifBlank {
                        rawPath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Pista $id" }
                    }
                    val rawArtist = cursor.getString(artistCol) ?: ""
                    val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "Artista desconocido" else rawArtist
                    val rawAlbum = cursor.getString(albumCol) ?: ""
                    val album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") "Álbum local" else rawAlbum
                    val albumId = cursor.getLong(albumIdCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId)
                    } else null

                    val folderName = folderPath.substringAfterLast('/').ifBlank { "Música" }

                    result.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            durationMs = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            folderPath = folderPath,
                            folderName = folderName,
                            sizeBytes = sizeBytes,
                            mimeType = mimeType,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        }

        // 2. Escanear también la carpeta local privada de la app (útil para pistas locales generadas o copiadas)
        val localMusicDir = File(context.filesDir, "LocalMusic")
        if (localMusicDir.exists()) {
            localMusicDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .forEach { file ->
                    val folderPath = file.parentFile?.absolutePath ?: localMusicDir.absolutePath
                    val lowerFolder = folderPath.lowercase().trimEnd('/')
                    val isExcluded = normalizedExcluded.any { excluded ->
                        excluded.isNotEmpty() && (lowerFolder == excluded || lowerFolder.startsWith("$excluded/"))
                    }
                    if (!isExcluded) {
                        val meta = parseLocalDemoFileMetadata(file)
                        if (meta.durationMs >= minDurationMs) {
                            result.add(meta)
                        }
                    }
                }
        }

        result
    }

    /**
     * Genera archivos de audio WAV reales y reproducibles en el almacenamiento local del dispositivo
     * cuando el usuario pulsa "Generar pistas locales de prueba" (o si el emulador está vacío),
     * con distintas duraciones (incluyendo un tono corto de 15s para comprobar el filtro de 50s).
     */
    suspend fun generateLocalSampleTracksIfNeeded(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "LocalMusic")
        val synthWaveDir = File(baseDir, "Synthwave Sessions")
        val acousticDir = File(baseDir, "Acústico Local")
        val whatsappAudioDir = File(baseDir, "WhatsApp Audio")

        if (!force && synthWaveDir.exists() && (synthWaveDir.listFiles()?.isNotEmpty() == true)) {
            return@withContext 0
        }

        synthWaveDir.mkdirs()
        acousticDir.mkdirs()
        whatsappAudioDir.mkdirs()

        data class SampleSpec(
            val dir: File,
            val fileName: String,
            val durationSec: Int,
            val baseFreqHz: Double,
            val chordFreqHz: Double
        )

        val specs = listOf(
            SampleSpec(synthWaveDir, "Horizonte Nocturno - Nova.wav", 64, 220.0, 277.18),
            SampleSpec(synthWaveDir, "Pulso Esmeralda - Aura Synth.wav", 58, 261.63, 329.63),
            SampleSpec(synthWaveDir, "Ciudad de Neón - Kairo.wav", 72, 196.00, 293.66),
            SampleSpec(acousticDir, "Brisa del Sur - Río Claro.wav", 55, 246.94, 369.99),
            SampleSpec(acousticDir, "Preludio en Sol - Alma Acústica.wav", 62, 164.81, 246.94),
            // Este audio dura 18 segundos: con el filtro por defecto en 50s queda oculto automáticamente
            SampleSpec(whatsappAudioDir, "Nota de voz corta - Audio WhatsApp.wav", 18, 440.0, 554.37)
        )

        var created = 0
        for (spec in specs) {
            val target = File(spec.dir, spec.fileName)
            if (!target.exists() || force) {
                writeSynthesizedWavFile(
                    file = target,
                    durationSeconds = spec.durationSec,
                    freq1 = spec.baseFreqHz,
                    freq2 = spec.chordFreqHz
                )
                created++
            }
        }
        created
    }

    private fun parseLocalDemoFileMetadata(file: File): Song {
        val nameWithoutExt = file.nameWithoutExtension
        val parts = nameWithoutExt.split(" - ", limit = 2)
        val title = parts.getOrElse(0) { nameWithoutExt }.trim()
        val artist = parts.getOrElse(1) { "Artista Local" }.trim()
        val folderName = file.parentFile?.name ?: "LocalMusic"
        val album = when {
            folderName.contains("Synthwave", ignoreCase = true) -> "Synthwave Sessions (Local)"
            folderName.contains("Acústico", ignoreCase = true) -> "Sesiones Acústicas"
            folderName.contains("WhatsApp", ignoreCase = true) -> "Notas de Voz"
            else -> folderName
        }

        // Calculamos duración real a partir del tamaño del WAV (8000 Hz, 16-bit mono = 16000 bytes/seg)
        val audioBytes = (file.length() - 44L).coerceAtLeast(0L)
        val durationMs = if (audioBytes > 0) (audioBytes * 1000L) / 16000L else 60_000L

        return Song(
            id = file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL,
            title = title,
            artist = artist,
            album = album,
            albumId = album.hashCode().toLong() and 0x7FFFFFFFL,
            durationMs = durationMs,
            contentUri = Uri.fromFile(file),
            albumArtUri = null,
            folderPath = file.parentFile?.absolutePath ?: "/LocalMusic",
            folderName = folderName,
            sizeBytes = file.length(),
            mimeType = "audio/wav",
            dateAdded = file.lastModified() / 1000L
        )
    }

    /**
     * Escribe un archivo PCM WAV mono de 8 kHz ligero (~900 KB por minuto) con acordes armónicos suaves
     * para mantener bajo el uso de memoria y almacenamiento y permitir reproducción gapless real en ExoPlayer.
     */
    private fun writeSynthesizedWavFile(
        file: File,
        durationSeconds: Int,
        freq1: Double,
        freq2: Double
    ) {
        val sampleRate = 8000
        val totalSamples = sampleRate * durationSeconds
        val dataSize = totalSamples * 2 // 16-bit mono

        FileOutputStream(file).buffered(8192).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size PCM
            header.putShort(1) // AudioFormat PCM = 1
            header.putShort(1) // NumChannels = 1 (Mono)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // ByteRate
            header.putShort(2) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())

            val chunkSamples = 1024
            val buffer = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            var sampleIndex = 0
            while (sampleIndex < totalSamples) {
                buffer.clear()
                val count = minOf(chunkSamples, totalSamples - sampleIndex)
                for (i in 0 until count) {
                    val t = (sampleIndex + i).toDouble() / sampleRate
                    // Progresión melódica suave por compases de 4 segundos
                    val bar = ((t / 4.0).toInt()) % 4
                    val pitchMultiplier = when (bar) {
                        0 -> 1.0
                        1 -> 1.12246 // Segunda mayor
                        2 -> 1.25992 // Tercera mayor
                        else -> 1.33484 // Cuarta justa
                    }
                    val envelope = sin(PI * (t % 2.0) / 2.0).coerceIn(0.15, 1.0)
                    val wave1 = sin(2.0 * PI * (freq1 * pitchMultiplier) * t)
                    val wave2 = sin(2.0 * PI * (freq2 * pitchMultiplier) * t) * 0.6
                    val subBass = sin(2.0 * PI * (freq1 * 0.5) * t) * 0.4
                    val combined = ((wave1 + wave2 + subBass) / 2.0) * envelope
                    val pcmValue = (combined * 12000.0).toInt().coerceIn(-32767, 32767).toShort()
                    buffer.putShort(pcmValue)
                }
                out.write(buffer.array(), 0, count * 2)
                sampleIndex += count
            }
        }
    }
}

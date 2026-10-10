package com.example.ui.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import com.example.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrae el color de acento dominante o vibrante desde la carátula de la canción en reproducción
 * usando una muestra diminuta (inSampleSize = 8) para no consumir memoria en dispositivos de 1 GB RAM.
 */
object PaletteColorExtractor {

    private val defaultPaletteFallbacks = listOf(
        0xFF1DB954L, // Verde Esmeralda
        0xFF00E5FFL, // Cian Neón
        0xFFFF6D00L, // Ámbar Vibrante
        0xFFAA00FFL, // Violeta Eléctrico
        0xFFFF2E93L, // Magenta Coral
        0xFF2979FFL  // Azul Cobalto
    )

    suspend fun extractAccentColor(context: Context, song: Song?): Long? = withContext(Dispatchers.IO) {
        if (song == null) return@withContext null

        val artUri: Uri? = song.albumArtUri
        if (artUri != null) {
            val extracted = runCatching {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    inSampleSize = 8 // Decodifica miniatura muy pequeña para Palette
                }
                context.contentResolver.openInputStream(artUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.dominantSwatch
                        bitmap.recycle()
                        swatch?.rgb?.toLong()?.let { rgb -> (rgb and 0xFFFFFFL) or 0xFF000000L }
                    } else null
                }
            }.getOrNull()

            if (extracted != null) {
                return@withContext ensureReadableAccent(extracted)
            }
        }

        // Si la pista local no tiene carátula embebida, asigna un acento armónico determinista por álbum
        val index = ((song.album.hashCode().toLong() and 0x7FFFFFFFL) % defaultPaletteFallbacks.size).toInt()
        defaultPaletteFallbacks[index]
    }

    private fun ensureReadableAccent(argb: Long): Long {
        val color = Color(argb)
        return if (color.luminance() < 0.12f) {
            // Aclara colores demasiado oscuros para que resalten en modo Oscuro y AMOLED
            0xFF1DB954L
        } else {
            argb
        }
    }
}

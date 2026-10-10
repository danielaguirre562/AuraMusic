package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.AuraDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MusicRepository

/**
 * Aplicación principal optimizada para dispositivos con poca RAM (<= 1 GB).
 * Configura Coil para usar un porcentaje reducido de memoria, RGB_565 en dispositivos
 * de gama baja o Android antiguo, y caché en disco limitada para miniaturas de carátulas.
 */
class AuraMusicApplication : Application(), ImageLoaderFactory {

    val database: AuraDatabase by lazy { AuraDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val musicRepository: MusicRepository by lazy {
        MusicRepository(
            context = this,
            playlistDao = database.playlistDao()
        )
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true ||
            Runtime.getRuntime().maxMemory() <= 128 * 1024 * 1024L

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Limita la caché en memoria al 10% en gama baja (1GB RAM) o 15% normal
                    .maxSizePercent(if (isLowRam) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_thumbnails"))
                    .maxSizeBytes(24L * 1024 * 1024) // 24 MB máximo para miniaturas en disco
                    .build()
            }
            // Usa RGB_565 (2 bytes por píxel en vez de 4) en dispositivos de poca RAM o Android < 8.0
            .bitmapConfig(
                if (isLowRam || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
            )
            .allowRgb565(true)
            .crossfade(false) // Evita animaciones pesadas al hacer scroll rápido en listas
            .build()
    }
}

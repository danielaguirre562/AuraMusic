package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R

/**
 * Miniatura de carátula ultra optimizada para listas (LazyColumn).
 * NUNCA carga la carátula a tamaño completo: fuerza una resolución pequeña (p. ej. 112x112 px)
 * con Precision.INEXACT y caché tanto en memoria como en disco.
 */
@Composable
fun SongThumbnailImage(
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 50.dp,
    cornerRadiusDp: Dp = 10.dp
) {
    val context = LocalContext.current
    val shape = remember(cornerRadiusDp) { RoundedCornerShape(cornerRadiusDp) }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            val request = remember(albumArtUri) {
                ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(112, 112) // Miniatura pequeña para ahorrar RAM
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .error(R.drawable.img_default_cover_1791652496680)
                    .fallback(R.drawable.img_default_cover_1791652496680)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_default_cover_1791652496680),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Carátula para la pantalla "Ahora reproduciendo" (limitada a 512x512 px para evitar OutOfMemoryError
 * en dispositivos con 1 GB de RAM cuando un usuario tiene carátulas de 3000x3000 px).
 */
@Composable
fun LargeNowPlayingCover(
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = remember { RoundedCornerShape(24.dp) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            val request = remember(albumArtUri) {
                ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(512, 512)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .error(R.drawable.img_default_cover_1791652496680)
                    .fallback(R.drawable.img_default_cover_1791652496680)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_default_cover_1791652496680),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Fondo personalizable de la aplicación:
 * - Si el usuario eligió una imagen de su galería, la carga sub-muestreada (360x640 px)
 *   para simular desenfoque suave incluso en versiones antiguas de Android + Modifier.blur()
 *   y aplica una capa de oscurecimiento ajustable para que el texto siempre se lea bien.
 * - O muestra el fondo sólido neutro con un sutil degradado superior del color de acento.
 */
@Composable
fun CustomizableAppBackground(
    customBackgroundUri: String?,
    blurRadius: Float,
    dimAlpha: Float,
    accentColor: Color,
    baseBackgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackgroundColor)
    ) {
        if (!customBackgroundUri.isNullOrBlank()) {
            // Al decodificar a baja resolución (p.ej. 96px..320px según blurRadius),
            // conseguimos un efecto de desenfoque suave que funciona desde Android 5.0 (API 21)
            // sin consumir memoria ni GPU, además de Modifier.blur en Android 12+.
            val downsampleSize = remember(blurRadius) {
                val factor = (28f - blurRadius).coerceIn(3f, 28f)
                (factor * 14f).toInt().coerceIn(48, 400)
            }
            val bgRequest = remember(customBackgroundUri, downsampleSize) {
                ImageRequest.Builder(context)
                    .data(Uri.parse(customBackgroundUri))
                    .size(downsampleSize, downsampleSize)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = bgRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius.dp)
            )
            // Capa de oscurecimiento ajustable para garantizar legibilidad del texto
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha.coerceIn(0.15f, 0.92f)))
            )
        } else {
            // Halo superior sutil estilo Spotify con el color de acento actual
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.16f),
                                baseBackgroundColor.copy(alpha = 0.95f),
                                baseBackgroundColor
                            )
                        )
                    )
            )
        }

        content()
    }
}

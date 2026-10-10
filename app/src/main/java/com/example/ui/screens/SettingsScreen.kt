package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferences
import com.example.domain.model.ThemeMode
import com.example.ui.theme.PresetAccentColors
import com.example.ui.theme.PresetSolidBackgroundColors

/**
 * Pantalla de Ajustes y Personalización Visual:
 * - Filtro de duración mínima (0 a 120 segundos, por defecto 50s para ocultar tonos y audios de WhatsApp).
 * - Lista de carpetas excluidas (añadir/eliminar carpetas detectadas o ruta manual).
 * - Temas: Claro, Oscuro y AMOLED (negro puro).
 * - Color de acento personalizable + extracción automática desde carátula (Palette).
 * - Fondo personalizable: Imagen de la galería con deslizadores de desenfoque (blur) y oscurecimiento,
 *   o fondo de colores neutros sólidos.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    discoveredFolders: List<String>,
    onBack: () -> Unit,
    onMinDurationChange: (Int) -> Unit,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onDynamicPaletteToggle: (Boolean) -> Unit,
    onSelectGalleryBackground: (android.net.Uri?) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onDimAlphaChange: (Float) -> Unit,
    onSolidBackgroundChange: (Long) -> Unit,
    onRescanLibrary: () -> Unit,
    onGenerateSampleSongs: () -> Unit
) {
    BackHandler(onBack = onBack)

    var newFolderInput by remember { mutableStateOf("") }

    // Selector oficial de fotos de Android (sin necesidad de permisos de almacenamiento adicionales)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSelectGalleryBackground(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .widthIn(max = 650.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ajustes y Personalización",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "100% Local • Optimizado para bajo consumo de RAM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SECCIÓN 1: Escaneo y Filtro de Duración Mínima (0 - 120s, por defecto 50s)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filtro de duración mínima",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "Oculta tonos de llamada, notificaciones y notas de voz de WhatsApp cortas. Valor por defecto: 50 segundos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duración mínima actual:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${preferences.minDurationSeconds} seg",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = preferences.minDurationSeconds.toFloat(),
                    onValueChange = { onMinDurationChange(it.toInt()) },
                    valueRange = 0f..120f,
                    steps = 23,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("min_duration_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0s (Todo)", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { onMinDurationChange(50) }) {
                        Text("Restablecer a 50s")
                    }
                    Text("120s (2 min)", style = MaterialTheme.typography.labelSmall)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRescanLibrary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rescan_library_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escanear")
                    }

                    OutlinedButton(
                        onClick = onGenerateSampleSongs,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_demo_tracks_btn")
                    ) {
                        Text("Generar pistas de prueba")
                    }
                }
            }
        }

        // SECCIÓN 2: Carpetas Excluidas
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Carpetas excluidas (${preferences.excludedFolders.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "Añade carpetas que no quieras que se escaneen (p. ej. /storage/emulated/0/WhatsApp/Media/WhatsApp Audio).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderInput,
                        onValueChange = { newFolderInput = it },
                        label = { Text("Ruta de carpeta a excluir…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exclude_folder_input")
                    )
                    Button(
                        onClick = {
                            if (newFolderInput.isNotBlank()) {
                                onAddExcludedFolder(newFolderInput.trim())
                                newFolderInput = ""
                            }
                        },
                        enabled = newFolderInput.isNotBlank(),
                        modifier = Modifier.testTag("add_excluded_folder_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir carpeta excluida")
                    }
                }

                // Sugerencias rápidas de carpetas detectadas en el dispositivo
                val nonExcludedDiscovered = discoveredFolders.filter { it !in preferences.excludedFolders }
                if (nonExcludedDiscovered.isNotEmpty()) {
                    Text(
                        text = "Toca una carpeta detectada para excluirla:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        nonExcludedDiscovered.forEach { folderPath ->
                            val shortName = folderPath.substringAfterLast('/').ifBlank { folderPath }
                            FilterChip(
                                selected = false,
                                onClick = { onAddExcludedFolder(folderPath) },
                                label = { Text("Excluir: $shortName") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CreateNewFolder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (preferences.excludedFolders.isEmpty()) {
                    Text(
                        text = "No hay carpetas excluidas actualmente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    preferences.excludedFolders.forEach { path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { onRemoveExcludedFolder(path) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Quitar exclusión",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECCIÓN 3: Temas (Claro, Oscuro, AMOLED Negro Puro) y Color de Acento / Palette
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tema y Color de Acento",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "Modo de tema:",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.LIGHT to "Claro",
                        ThemeMode.DARK to "Oscuro",
                        ThemeMode.AMOLED to "AMOLED (#000)"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_chip_${mode.name}")
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Color dinámico desde la carátula (Palette)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Extrae automáticamente el color de acento de la canción que está sonando.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.useDynamicAlbumPalette,
                        onCheckedChange = onDynamicPaletteToggle,
                        modifier = Modifier.testTag("dynamic_palette_switch")
                    )
                }

                Text(
                    text = "Color de acento personalizado:",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PresetAccentColors.forEach { (argb, name) ->
                        val isSelected = preferences.customAccentColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onAccentColorChange(argb) }
                                .testTag("accent_swatch_$name"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = name,
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECCIÓN 4: Fondo Personalizable (Imagen de Galería con Blur y Oscurecimiento, o Sólido)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fondo Personalizable",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "Elige una imagen de tu galería con controles de desenfoque (blur) y oscurecimiento para lectura perfecta, o usa un fondo neutro sólido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_gallery_bg_btn")
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Elegir de galería")
                    }

                    if (!preferences.customBackgroundUri.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onSelectGalleryBackground(null) },
                            modifier = Modifier.testTag("clear_gallery_bg_btn")
                        ) {
                            Text("Usar fondo sólido")
                        }
                    }
                }

                // Controles de desenfoque (Blur) y oscurecimiento (Dim)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nivel de desenfoque (Blur)", style = MaterialTheme.typography.titleSmall)
                        Text("${preferences.backgroundBlurRadius.toInt()} dp", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = preferences.backgroundBlurRadius,
                        onValueChange = onBlurRadiusChange,
                        valueRange = 0f..25f
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Oscurecimiento para lectura", style = MaterialTheme.typography.titleSmall)
                        Text("${(preferences.backgroundDimAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = preferences.backgroundDimAlpha,
                        onValueChange = onDimAlphaChange,
                        valueRange = 0.15f..0.90f
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Colores neutros sólidos de fondo:",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetSolidBackgroundColors.forEach { (argb, label) ->
                        val isSelected = preferences.solidBackgroundColorArgb == argb && preferences.customBackgroundUri == null
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onSelectGalleryBackground(null)
                                onSolidBackgroundChange(argb)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

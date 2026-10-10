package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta de acentos seleccionables por el usuario
val AccentEmerald = Color(0xFF1DB954)
val AccentCyanNeon = Color(0xFF00E5FF)
val AccentAmberGlow = Color(0xFFFF6D00)
val AccentElectricViolet = Color(0xFFAA00FF)
val AccentCoralRose = Color(0xFFFF2E93)
val AccentCobaltBlue = Color(0xFF2979FF)
val AccentGold = Color(0xFFFFD600)

val PresetAccentColors = listOf(
    0xFF1DB954L to "Esmeralda Spotify",
    0xFF00E5FFL to "Cian Neón",
    0xFFFF6D00L to "Ámbar Cálido",
    0xFFAA00FFL to "Violeta Eléctrico",
    0xFFFF2E93L to "Rosa Coral",
    0xFF2979FFL to "Azul Cobalto",
    0xFFFFD600L to "Oro Solar"
)

val PresetSolidBackgroundColors = listOf(
    0xFF000000L to "Negro Puro (AMOLED)",
    0xFF0E1318L to "Carbón Nocturno",
    0xFF121824L to "Azul Abisal",
    0xFF1A1423L to "Obsidiana Violeta",
    0xFF0E1B16L to "Bosque Oscuro",
    0xFF1F1916L to "Espresso Mate"
)

// Colores base para tema Claro
val LightBackground = Color(0xFFF6F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEAECEF)
val LightOnBackground = Color(0xFF11151C)
val LightOnSurfaceVariant = Color(0xFF535D6C)

// Colores base para tema Oscuro (estilo Spotify)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF181818)
val DarkSurfaceVariant = Color(0xFF242424)
val DarkOnBackground = Color(0xFFF5F7FA)
val DarkOnSurfaceVariant = Color(0xFFA7B0BC)

// Colores base para tema AMOLED (Negro Puro #000000 para máximo ahorro de batería)
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledSurfaceVariant = Color(0xFF161616)
val AmoledOnBackground = Color(0xFFFFFFFF)
val AmoledOnSurfaceVariant = Color(0xFF9EA7B3)

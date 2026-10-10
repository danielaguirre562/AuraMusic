package com.example.player

import android.media.audiofx.Equalizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
) {
    val labelFreq: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000}kHz" else "${centerFreqHz}Hz"
}

@Immutable
data class EqualizerUiState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val audioSessionId: Int = 0,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val selectedPresetIndex: Int = -1
)

/**
 * Controlador de ecualizador usando la API oficial de Android (android.media.audiofx.Equalizer)
 * conectado al audioSessionId de ExoPlayer. Maneja con seguridad excepciones en dispositivos
 * antiguos (API 21+) o ROMs con soporte parcial de DSP.
 */
class SystemEqualizerManager {

    private var equalizer: Equalizer? = null
    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    fun attachToAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (_state.value.audioSessionId == sessionId && equalizer != null) return

        release()
        runCatching {
            val eq = Equalizer(0, sessionId)
            eq.enabled = _state.value.isEnabled
            equalizer = eq
            refreshState(sessionId)
        }.onFailure {
            // Si el dispositivo o emulador no expone hardware DSP para esta sesión, mostramos bandas estándar seguras
            _state.value = EqualizerUiState(
                isAvailable = false,
                isEnabled = false,
                audioSessionId = sessionId,
                bands = defaultFallbackBands(),
                presets = listOf("Normal", "Acústico", "Bajos Potentes", "Electrónica", "Rock", "Vocal")
            )
        }
    }

    private fun refreshState(sessionId: Int = _state.value.audioSessionId) {
        val eq = equalizer ?: return
        runCatching {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { -1500 }.toInt()
            val maxMb = range.getOrElse(1) { 1500 }.toInt()

            val bands = (0 until numBands).map { idx ->
                val bandShort = idx.toShort()
                val freqMilliHz = eq.getCenterFreq(bandShort)
                val level = eq.getBandLevel(bandShort).toInt()
                EqBand(
                    index = idx,
                    centerFreqHz = (freqMilliHz / 1000).coerceAtLeast(30),
                    minLevelMb = minMb,
                    maxLevelMb = maxMb,
                    currentLevelMb = level
                )
            }

            val numPresets = eq.numberOfPresets.toInt()
            val presets = (0 until numPresets).map { idx ->
                eq.getPresetName(idx.toShort()) ?: "Preset ${idx + 1}"
            }

            _state.value = EqualizerUiState(
                isAvailable = true,
                isEnabled = eq.enabled,
                audioSessionId = sessionId,
                bands = bands,
                presets = presets,
                selectedPresetIndex = eq.currentPreset.toInt()
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.enabled = enabled
                refreshState()
            }
        } else {
            _state.value = _state.value.copy(isEnabled = enabled)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.setBandLevel(bandIndex.toShort(), levelMb.toShort())
                refreshState()
            }
        } else {
            val updated = _state.value.bands.map {
                if (it.index == bandIndex) it.copy(currentLevelMb = levelMb) else it
            }
            _state.value = _state.value.copy(bands = updated, selectedPresetIndex = -1)
        }
    }

    fun selectPreset(presetIndex: Int) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.usePreset(presetIndex.toShort())
                refreshState()
            }
        } else {
            val curve = when (presetIndex) {
                1 -> listOf(300, 150, 0, 200, 350)
                2 -> listOf(750, 500, 0, -100, 100)
                3 -> listOf(500, 200, -150, 300, 550)
                4 -> listOf(450, 250, -100, 250, 450)
                5 -> listOf(-200, 100, 450, 300, 0)
                else -> listOf(0, 0, 0, 0, 0)
            }
            val updated = _state.value.bands.mapIndexed { idx, band ->
                band.copy(currentLevelMb = curve.getOrElse(idx) { 0 })
            }
            _state.value = _state.value.copy(bands = updated, selectedPresetIndex = presetIndex)
        }
    }

    fun resetFlat() {
        val currentBands = _state.value.bands
        currentBands.forEach { band ->
            setBandLevel(band.index, 0)
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    private fun defaultFallbackBands(): List<EqBand> = listOf(
        EqBand(0, 60, -1500, 1500, 0),
        EqBand(1, 230, -1500, 1500, 0),
        EqBand(2, 910, -1500, 1500, 0),
        EqBand(3, 3600, -1500, 1500, 0),
        EqBand(4, 14000, -1500, 1500, 0)
    )
}

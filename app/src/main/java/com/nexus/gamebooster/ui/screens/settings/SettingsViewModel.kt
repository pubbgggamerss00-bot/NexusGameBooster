package com.nexus.gamebooster.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.data.Language
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.data.ThemeMode
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.games.GameScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: Language = Language.SYSTEM,
    val reduceMotion: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK_NAVY,
    /**
     * Number of launchable apps this build can see (from the last real scan), or null
     * when no scan has completed yet — the About row is hidden until then.
     */
    val visibleAppCount: Int? = null,
)

/**
 * Settings ViewModel. Language, theme and reduce-motion are persisted via DataStore;
 * "Reset" clears the DataStore keys (including the game-library sets) and the
 * in-memory repositories.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        language = settings.language,
                        reduceMotion = settings.reduceMotion,
                        theme = settings.theme,
                    )
                }
            }
        }
        viewModelScope.launch {
            gameRepository.lastScanResult.collect { result ->
                when (result) {
                    is GameScanResult.Success -> _uiState.update {
                        it.copy(visibleAppCount = result.apps.size)
                    }

                    is GameScanResult.Failed -> _uiState.update {
                        it.copy(visibleAppCount = null)
                    }

                    null -> Unit
                }
            }
        }
    }

    fun setLanguage(language: Language) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
        }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReduceMotion(enabled)
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            settingsRepository.clear()
            gameRepository.reset()
        }
    }
}

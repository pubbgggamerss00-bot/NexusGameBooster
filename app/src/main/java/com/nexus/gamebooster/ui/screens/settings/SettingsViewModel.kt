package com.nexus.gamebooster.ui.screens.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.R
import com.nexus.gamebooster.data.DeviceChangeRecord
import com.nexus.gamebooster.data.DeviceChangeStore
import com.nexus.gamebooster.data.Language
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.data.ThemeMode
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.games.GameScanResult
import com.nexus.gamebooster.optimizer.actions.DeviceRestoreExecutor
import com.nexus.gamebooster.optimizer.actions.DeviceRestoreExecutor.ItemStatus
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Restore status of ONE ledger record after the last restore run — used by the UI to
 * mark rows as restored (they disappear), skipped (why) or failed (raw message).
 */
data class DeviceChangeStatusUi(
    val record: DeviceChangeRecord,
    val status: ItemStatus?,
)

data class SettingsUiState(
    val language: Language = Language.SYSTEM,
    val reduceMotion: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK_NAVY,
    /**
     * Number of launchable apps this build can see (from the last real scan), or null
     * when no scan has completed yet — the About row is hidden until then.
     */
    val visibleAppCount: Int? = null,
    // --- Phase 10: restore device changes ---
    /** Real recorded device changes (newest first). Empty → the app has recorded nothing. */
    val deviceChanges: List<DeviceChangeRecord> = emptyList(),
    val shizukuStatus: ShizukuPrivilege.Status = ShizukuPrivilege.Status.NOT_RUNNING,
    /** True while a restore run is executing Shizuku commands. */
    val restoreBusy: Boolean = false,
    /** Result of the last restore run; null = no report to show. */
    val lastRestoreReport: DeviceRestoreExecutor.Report? = null,
    /** Per-record status from the last run (kept for rows that stayed in the ledger). */
    val lastStatuses: Map<String, ItemStatus> = emptyMap(),
    /** One-shot hint after Activate was tapped (res id of an existing shizuku_msg_* string). */
    @StringRes val shizukuHintRes: Int? = null,
)

/**
 * Settings ViewModel. Language, theme and reduce-motion are persisted via DataStore;
 * "Reset" clears the DataStore keys (including the game-library sets) and the
 * in-memory repositories.
 *
 * Phase 10: "Restore device changes" reverts what boost actually changed on the
 * device (animation scales, battery whitelist, standby bucket, ART compile) using
 * the pre-state recorded at boost time. No record → honest empty state; Shizuku
 * not ready → honest "required" report; unknown originals are skipped, not guessed.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
    private val shizukuPrivilege: ShizukuPrivilege,
    private val restoreExecutor: DeviceRestoreExecutor,
    private val activateShizuku: () -> ShizukuPrivilege.ActivateOutcome,
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
        viewModelScope.launch {
            settingsRepository.deviceChanges.collect { records ->
                _uiState.update { it.copy(deviceChanges = records) }
            }
        }
        viewModelScope.launch {
            shizukuPrivilege.status.collect { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
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

    // ---------------------------------------------- Phase 10: restore device changes

    /** One-tap Shizuku activation (same ladder as Home). Shows the matching hint. */
    fun activateShizukuForRestore() {
        _uiState.update { it.copy(shizukuHintRes = activateShizuku().toMessageRes()) }
    }

    fun dismissShizukuHint() {
        _uiState.update { it.copy(shizukuHintRes = null) }
    }

    /** Runs the restore; the executor never fakes success and keeps failed records. */
    fun restoreDeviceChanges() {
        if (_uiState.value.restoreBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(restoreBusy = true) }
            val report = runCatching { restoreExecutor.restoreAll() }.getOrNull()
            _uiState.update { state ->
                state.copy(
                    restoreBusy = false,
                    lastRestoreReport = report,
                    lastStatuses = report?.results
                        ?.associate { it.record.id to it.status }
                        .orEmpty(),
                )
            }
        }
    }

    /** Forgets the ledger WITHOUT touching the device (user-explicit, confirmed in UI). */
    fun forgetDeviceRecords() {
        viewModelScope.launch {
            (settingsRepository as DeviceChangeStore).clearDeviceChanges()
            _uiState.update { it.copy(lastStatuses = emptyMap(), lastRestoreReport = null) }
        }
    }

    fun dismissRestoreReport() {
        _uiState.update { it.copy(lastRestoreReport = null) }
    }

    private fun ShizukuPrivilege.ActivateOutcome.toMessageRes(): Int = when (this) {
        ShizukuPrivilege.ActivateOutcome.ALREADY_READY -> R.string.shizuku_msg_ready
        ShizukuPrivilege.ActivateOutcome.PERMISSION_REQUESTED -> R.string.shizuku_msg_permission
        ShizukuPrivilege.ActivateOutcome.NEED_START_SERVICE -> R.string.shizuku_msg_need_start
        ShizukuPrivilege.ActivateOutcome.NOT_INSTALLED -> R.string.shizuku_msg_not_installed
    }
}

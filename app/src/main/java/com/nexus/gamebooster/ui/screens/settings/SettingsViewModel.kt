package com.nexus.gamebooster.ui.screens.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.BuildConfig
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
import com.nexus.gamebooster.update.GitHubUpdateClient
import com.nexus.gamebooster.update.UpdateTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Restore status of ONE ledger record after the last restore run — used by the UI to
 * mark rows as restored (they disappear), skipped (why) or failed (raw message).
 */
data class DeviceChangeStatusUi(
    val record: DeviceChangeRecord,
    val status: ItemStatus?,
)

/**
 * Phase 11 state machine for the Settings → "Check for updates" card. Every state
 * is a REAL outcome of a REAL network/system call — the card never invents a result.
 */
sealed interface UpdateCheckUi {
    data object Idle : UpdateCheckUi

    data object Checking : UpdateCheckUi

    /** The numeric prefix of the latest GitHub release equals ours. */
    data class UpToDate(val remoteVersion: String) : UpdateCheckUi

    /**
     * A release with a numerically greater prefix exists. [whatsNew] is the friendly
     * notes text when the release ships one, null → the UI shows an honest generic line.
     */
    data class Available(
        val versionName: String,
        val whatsNew: String?,
        val apkSizeBytes: Long,
    ) : UpdateCheckUi

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateCheckUi

    /** Honest failure: [errorRes] + its string args (the raw detail travels inside). */
    data class Failed(val errorRes: Int, val args: List<String> = emptyList()) : UpdateCheckUi
}

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
    // --- Phase 11: check for updates ---
    val update: UpdateCheckUi = UpdateCheckUi.Idle,
    /** True when the user tapped download but the system "Install unknown apps" switch is off. */
    val updateNeedsInstallPermission: Boolean = false,
    /** One-shot inline hint, e.g. after the system installer was opened. */
    @StringRes val updateHintRes: Int? = null,
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
    private val updateClient: GitHubUpdateClient = GitHubUpdateClient,
    private val canRequestInstalls: () -> Boolean = { false },
    private val requestInstallPermission: () -> Unit = {},
    private val downloadUpdate: suspend (url: String, fileName: String, onProgress: (Long, Long) -> Unit) -> Result<File> = { _, _, _ ->
        Result.failure(IllegalStateException("no download wiring"))
    },
    private val startInstall: (File) -> Result<Unit> = { Result.failure(IllegalStateException("no install wiring")) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Release behind the current Available/Downloading card — reused for retry/resume. */
    private var pendingRelease: UpdateTools.LatestRelease? = null

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

    // ---------------------------------------------- Phase 11: check for updates

    /**
     * Asks GitHub for the latest release and compares numeric prefixes honestly:
     * greater → Available (with user-level notes), equal/less → UpToDate,
     * unparseable/HTTP error → Failed with the raw detail. Never fakes either way.
     */
    fun checkForUpdates() {
        if (_uiState.value.update is UpdateCheckUi.Checking) return
        if (_uiState.value.update is UpdateCheckUi.Downloading) return
        _uiState.update {
            it.copy(
                update = UpdateCheckUi.Checking,
                updateHintRes = null,
                updateNeedsInstallPermission = false,
            )
        }
        viewModelScope.launch {
            val languageTag = when (settingsRepository.settings.first().language) {
                Language.PERSIAN -> "fa"
                Language.ENGLISH -> "en"
                Language.SYSTEM -> Locale.getDefault().language.ifBlank { "en" }
            }
            val fetch = updateClient.fetchLatestRelease(languageTag)
            val release = fetch.getOrNull()
            if (release == null) {
                _uiState.update {
                    it.copy(
                        update = UpdateCheckUi.Failed(
                            R.string.settings_update_error,
                            listOf(fetch.exceptionOrNull()?.message.orEmpty()),
                        ),
                    )
                }
                return@launch
            }
            pendingRelease = release
            when (val comparison = UpdateTools.compareVersionNames(BuildConfig.VERSION_NAME, release.versionName)) {
                null -> _uiState.update {
                    it.copy(
                        update = UpdateCheckUi.Failed(
                            R.string.settings_update_version_mismatch,
                            listOf(BuildConfig.VERSION_NAME, release.versionName, release.tagName),
                        ),
                    )
                }

                else ->
                    // Standard compareTo semantics: local.compareTo(remote).
                    // < 0 → a numerically newer release exists → Available;
                    // 0 (equal prefix) or > 0 (this build is newer than latest) → up to date.
                    if (comparison < 0) {
                        _uiState.update {
                            it.copy(
                                update = UpdateCheckUi.Available(
                                    versionName = release.versionName,
                                    whatsNew = release.whatsNewText,
                                    apkSizeBytes = release.apkSizeBytes,
                                ),
                            )
                        }
                    } else {
                        _uiState.update { it.copy(update = UpdateCheckUi.UpToDate(release.versionName)) }
                    }
            }
        }
    }

    /**
     * Download + hand to the system installer, fully in-app. Before ANY bytes move,
     * the real per-app install switch is checked; if it is off, the one-time system
     * page opens and the download resumes automatically when the user returns.
     */
    fun downloadAndInstallUpdate() {
        val release = pendingRelease ?: return
        if (_uiState.value.update is UpdateCheckUi.Downloading) return
        if (!canRequestInstalls()) {
            _uiState.update { it.copy(updateNeedsInstallPermission = true) }
            requestInstallPermission()
            return
        }
        startDownload(release)
    }

    /** Called on ON_RESUME: the user just came back from the install-permission page. */
    fun onScreenResumed() {
        val release = pendingRelease ?: return
        if (_uiState.value.updateNeedsInstallPermission && canRequestInstalls()) {
            _uiState.update { it.copy(updateNeedsInstallPermission = false) }
            startDownload(release)
        }
    }

    /** Re-opens the system install-permission page from the card's Allow button. */
    fun grantInstallPermission() {
        requestInstallPermission()
    }

    private fun startDownload(release: UpdateTools.LatestRelease) {
        _uiState.update {
            it.copy(
                update = UpdateCheckUi.Downloading(0, release.apkSizeBytes),
                updateHintRes = null,
                updateNeedsInstallPermission = false,
            )
        }
        viewModelScope.launch {
            val fileName = "nexus-update-${release.versionName}.apk"
            val downloaded = downloadUpdate(release.apkUrl, fileName) { done, total ->
                _uiState.update { state ->
                    if (state.update is UpdateCheckUi.Downloading) {
                        state.copy(update = UpdateCheckUi.Downloading(done, total))
                    } else {
                        state
                    }
                }
            }
            val apkFile = downloaded.getOrNull()
            if (apkFile == null) {
                _uiState.update {
                    it.copy(
                        update = UpdateCheckUi.Failed(
                            R.string.settings_update_download_failed,
                            listOf(downloaded.exceptionOrNull()?.message.orEmpty()),
                        ),
                    )
                }
                return@launch
            }
            val install = startInstall(apkFile)
            if (install.isSuccess) {
                _uiState.update {
                    it.copy(
                        update = UpdateCheckUi.Available(
                            versionName = release.versionName,
                            whatsNew = release.whatsNewText,
                            apkSizeBytes = release.apkSizeBytes,
                        ),
                        updateHintRes = R.string.settings_update_install_opened,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        update = UpdateCheckUi.Failed(
                            R.string.settings_update_install_failed,
                            listOf(install.exceptionOrNull()?.message.orEmpty()),
                        ),
                    )
                }
            }
        }
    }

    private fun ShizukuPrivilege.ActivateOutcome.toMessageRes(): Int = when (this) {
        ShizukuPrivilege.ActivateOutcome.ALREADY_READY -> R.string.shizuku_msg_ready
        ShizukuPrivilege.ActivateOutcome.PERMISSION_REQUESTED -> R.string.shizuku_msg_permission
        ShizukuPrivilege.ActivateOutcome.NEED_START_SERVICE -> R.string.shizuku_msg_need_start
        ShizukuPrivilege.ActivateOutcome.NOT_INSTALLED -> R.string.shizuku_msg_not_installed
    }
}

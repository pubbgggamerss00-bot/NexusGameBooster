package com.nexus.gamebooster.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.R
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.games.GameLibraryEvent
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.DeviceInfoRepository
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MonitoringRepository
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.profiles.ProfileRepository
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_LOG_ENTRIES = 20

/**
 * One line of the in-memory activity log. Only REAL user actions / real reads are
 * logged; nothing is invented. [messageArg] carries a plain display argument
 * (e.g. "87%"), [messageArgRes] carries a string resource argument (e.g. a profile name).
 */
data class ActivityEntry(
    val timeMillis: Long,
    val messageRes: Int,
    val messageArg: String? = null,
    val messageArgRes: Int? = null,
)

data class HomeUiState(
    val deviceFacts: DeviceFacts? = null,
    val battery: BatterySnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    /** PowerManager thermal headroom (API 30+); null headroom means "not reported". */
    val headroom: ThermalHeadroomSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val cpu: CpuSnapshot? = null,
    val profiles: List<PerformanceProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** True when persisting the last profile selection failed (previous one stays active). */
    val profileSaveError: Boolean = false,
    /** Real count of the user's selected library (from GameRepository). */
    val gameCount: Int = 0,
    val activityLog: List<ActivityEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    /** Phase 9: live Shizuku privilege status for the Home status card. */
    val shizukuStatus: ShizukuPrivilege.Status = ShizukuPrivilege.Status.NOT_RUNNING,
)

/**
 * Home ViewModel. Device/metric numbers come from real one-shot reads; the game count
 * mirrors the real library; the activity log records only genuine events (scans,
 * library changes, launches, profile selections — plus the boost events forwarded by
 * [onBoostLogEvent] from the screen's [com.nexus.gamebooster.ui.screens.home.BoostViewModel]).
 *
 * The boost flow itself lives entirely in BoostViewModel (Phase 5): the old Phase 1
 * OptimizerEngine stub path was removed when the real engine replaced it.
 */
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val monitoringRepository: MonitoringRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val gameRepository: GameRepository,
    private val profileRepository: ProfileRepository,
    private val shizukuPrivilege: ShizukuPrivilege,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(profiles = profileRepository.profiles))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Real device facts from android.os.Build / Settings.Global (cheap, synchronous).
        _uiState.update { it.copy(deviceFacts = deviceInfoRepository.deviceFacts()) }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(activeProfileId = settings.activeProfileId) }
            }
        }

        // Real library size for the "My games" shortcut card.
        viewModelScope.launch {
            gameRepository.games.collect { games ->
                _uiState.update { it.copy(gameCount = games.size) }
            }
        }

        // Real library events (scan / add / remove / launch) feed the activity log.
        viewModelScope.launch {
            gameRepository.events.collect { event ->
                logActivityEntry(activityEntry(event))
            }
        }

        // Phase 9: live Shizuku status (binder + permission).
        viewModelScope.launch {
            shizukuPrivilege.status.collect { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
            }
        }

        refresh()
    }

    /** Opens the Shizuku permission dialog when binder is up and permission is missing. */
    fun requestShizukuPermission() {
        shizukuPrivilege.requestPermission()
        shizukuPrivilege.refreshStatus()
    }

    /** Re-check binder / permission (e.g. after user returns from Shizuku app). */
    fun refreshShizukuStatus() {
        shizukuPrivilege.refreshStatus()
    }

    /** One-shot read of the monitoring repository (button or first entry into the screen). */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val snapshot = monitoringRepository.refresh()
            _uiState.update {
                it.copy(
                    battery = snapshot.battery,
                    thermal = snapshot.thermal,
                    headroom = snapshot.headroom,
                    memory = snapshot.memory,
                    cpu = snapshot.cpu,
                    isRefreshing = false,
                )
            }
            log(R.string.activity_monitoring_refreshed)
            val percent = snapshot.battery.levelPercent
            if (percent != null) {
                log(R.string.activity_battery_read, messageArg = "$percent%")
            }
        }
    }

    /**
     * Boost pressed. The boost flow lives in the screen's BoostViewModel (Phase 5) —
     * HomeViewModel has no boost logic of its own anymore. The BoostCard tap forwards
     * here so the activity log records the real user intent, then BoostViewModel runs.
     */
    fun onBoostPressed() {
        log(R.string.activity_boost_requested)
    }

    /**
     * Receives honest boost log events from the screen's BoostViewModel (started /
     * finished-with-status / cancelled) and appends them to the shared activity log.
     */
    fun onBoostLogEvent(event: BoostLogEvent) {
        logActivityEntry(
            ActivityEntry(
                timeMillis = System.currentTimeMillis(),
                messageRes = event.messageRes,
                messageArg = event.arg,
                messageArgRes = event.argRes,
            )
        )
    }

    /**
     * Persists the selection. Nothing is applied to the system. The persist is wrapped
     * in runCatching: on failure the activity log stays untouched and a small honest
     * error flag is shown under the quick-select card (the previous profile is still
     * the active one); on success the error flag is cleared and the selection is logged.
     */
    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = profileRepository.findById(profileId)
            val persisted = runCatching { settingsRepository.setActiveProfile(profileId) }
            persisted
                .onSuccess {
                    _uiState.update { it.copy(profileSaveError = false) }
                    if (profile != null) {
                        log(R.string.activity_profile_selected, messageArgRes = profile.titleRes)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(profileSaveError = true) }
                }
        }
    }

    private fun activityEntry(event: GameLibraryEvent): ActivityEntry {
        val now = System.currentTimeMillis()
        return when (event) {
            is GameLibraryEvent.ScanCompleted -> ActivityEntry(
                now, R.string.activity_scan_completed, messageArg = event.launchableCount.toString(),
            )
            is GameLibraryEvent.GameAdded -> ActivityEntry(
                now, R.string.activity_game_added, messageArg = event.name,
            )
            is GameLibraryEvent.GameRemoved -> ActivityEntry(
                now, R.string.activity_game_removed, messageArg = event.name,
            )
            is GameLibraryEvent.GameLaunched -> ActivityEntry(
                now, R.string.activity_game_launched, messageArg = event.name,
            )
            is GameLibraryEvent.GameLaunchFailed -> ActivityEntry(
                now, R.string.activity_game_launch_failed, messageArg = event.name,
            )
        }
    }

    private fun logActivityEntry(entry: ActivityEntry) {
        _uiState.update { state ->
            state.copy(activityLog = (listOf(entry) + state.activityLog).take(MAX_LOG_ENTRIES))
        }
    }

    private fun log(
        messageRes: Int,
        messageArg: String? = null,
        messageArgRes: Int? = null,
    ) {
        logActivityEntry(
            ActivityEntry(
                timeMillis = System.currentTimeMillis(),
                messageRes = messageRes,
                messageArg = messageArg,
                messageArgRes = messageArgRes,
            )
        )
    }
}

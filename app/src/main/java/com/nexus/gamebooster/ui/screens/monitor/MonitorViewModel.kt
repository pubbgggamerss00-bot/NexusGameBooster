package com.nexus.gamebooster.ui.screens.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.DeviceInfoRepository
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MonitoringRepository
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.profiles.MonitoringPolicy
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.profiles.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitorUiState(
    val battery: BatterySnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    val headroom: ThermalHeadroomSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val cpu: CpuSnapshot? = null,
    /** Real Build/Settings facts — cheap synchronous read done once at init. */
    val deviceFacts: DeviceFacts? = null,
    val isRefreshing: Boolean = false,
    /** Real wall-clock time of the last successful refresh (from the snapshot). */
    val lastUpdatedMillis: Long? = null,
    /** Stable UnavailableReasons key of the last FAILED refresh; null when healthy. */
    val errorReason: String? = null,
    /**
     * The active profile once the persisted id has loaded; null until then. Null means
     * "policy not yet known" — the UI shows a neutral loading chip and NOTHING runs
     * automatically (no auto refresh, no live battery collection) until a profile is set.
     */
    val profile: PerformanceProfile? = null,
    /**
     * Phase 5 session mode from [SessionMonitorController]: EFFICIENT means the boost's
     * session-monitoring action decided (from a real low-battery read) that this app
     * should conserve power for the rest of the session — the EFFECTIVE policy turns
     * auto refresh and the live battery stream off (manual refresh always stays).
     */
    val sessionMode: SessionMonitorMode = SessionMonitorMode.NORMAL,
)

/**
 * Monitor ViewModel (Phase 4 — profile-driven monitoring policy).
 *
 * Data sources — all real; nothing system-wide is ever changed or simulated:
 *
 *  1. One-shot `refresh()` on init and on the manual Refresh button. It reads the
 *     whole [MonitoringSnapshot] (battery, thermal status, thermal headroom, memory,
 *     CPU cores) plus the snapshot's real completion timestamp. The one-shot refresh is
 *     NOT affected by the active profile: it works on every profile, including Battery
 *     Saver (whose only difference is that NOTHING runs automatically).
 *
 *  2. PROFILE → policy: the persisted profile id (via [activeProfileStore], normalized
 *     through [ProfileRepository.findByIdOrFallback], so the legacy "gaming" value also
 *     lands on Balanced) is mapped to a [PerformanceProfile] and surfaced in the state.
 *     Its [MonitoringPolicy] changes ONLY this app's own behavior:
 *
 *       - AUTO-REFRESH TICKER: one viewModelScope job `combine`s the policy with an
 *         explicit SCREEN-ACTIVE flag (set by the Monitor composable's lifecycle
 *         observer via [onMonitorScreenActive] / [onMonitorScreenInactive]) and, only
 *         when the screen is lifecycle-active AND `policy.inAppMonitoringEnabled` AND
 *         `autoRefreshIntervalMillis != null`, runs a `delay`-based ticker through
 *         `flatMapLatest`. Switching screen visibility or profile cancels the pending
 *         delay promptly (flatMapLatest cancels the previous inner flow), so:
 *           * the ticker NEVER runs while the Monitor screen is inactive,
 *           * it NEVER runs for Battery Saver (`inAppMonitoringEnabled = false`,
 *             interval = null),
 *           * manual refresh stays available on every profile.
 *
 *       - LIVE BATTERY: `monitoringRepository.batteryUpdates()` is collected ONLY while
 *         `policy.liveBatteryEvents` is true (`collectLatest` cancels the collection —
 *         and therefore unregisters the runtime receiver — the moment the profile turns
 *         the stream off, and re-subscribes when it turns back on).
 *
 *  2b. SESSION MODE (Phase 5): the boost engine's session-monitoring action may switch
 *      [SessionMonitorController] to EFFICIENT after a REAL low-battery read. The
 *      effective policy used by the ticker and the live battery stream is the profile
 *      policy combined with that session mode — EFFICIENT copies the policy with auto
 *      refresh and live battery events OFF for the rest of the session (keep-screen-on
 *      still follows the explicit profile policy; manual refresh always works). The
 *      Monitor screen shows an honest chip while the session is EFFICIENT.
 *
 *  3. Lifecycle chain (honest, unchanged from Phase 3 and still true in Phase 4):
 *
 *       UI collects `uiState` via `collectAsStateWithLifecycle` (Phase 1 pattern)
 *         → when the Monitor composable is STOPPED, that UI-side collection stops,
 *           so updates are no longer observed or recomposed;
 *       the composable's LifecycleEventObserver flips the screen-active flag
 *         → ON_START sets it, ON_STOP clears it, so the auto-refresh ticker exists
 *           ONLY while the screen is at least STARTED;
 *       the VM-side battery collection lives as long as this ViewModel
 *         → when the ViewModel is cleared (Monitor's back-stack entry destroyed),
 *           viewModelScope is cancelled
 *         → the callbackFlow's `awaitClose` block runs
 *         → the runtime-registered ACTION_BATTERY_CHANGED receiver is unregistered.
 *
 *     Honest caveats (deliberately documented, not hidden): while this ViewModel is
 *     alive but its screen is not visible, the conflated battery Flow may still
 *     receive system broadcasts into memory when `liveBatteryEvents` is true — the
 *     system delivers them only when the battery actually changes, there is NO
 *     polling, NO wake lock and NO work beyond an in-memory state copy, and the
 *     receiver is gone the moment the ViewModel dies. The policy changes nothing
 *     outside this app: no system process is throttled, no frequency is set, no
 *     thermal limit is touched, and no value is ever invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModel(
    private val monitoringRepository: MonitoringRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val sessionMonitorController: SessionMonitorController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    /**
     * True only while the Monitor screen is lifecycle-active (at least STARTED).
     * Driven from the composable's LifecycleEventObserver — never by the system.
     */
    private val screenActive = MutableStateFlow(false)

    /**
     * Current RAW monitoring policy: the active profile's policy, or null while the
     * persisted id has not loaded ("policy not yet known" → nothing automatic runs).
     */
    private val policyFlow: Flow<MonitoringPolicy?> = _uiState
        .map { it.profile?.monitoringPolicy }
        .distinctUntilChanged()

    /**
     * EFFECTIVE policy (Phase 5): the profile policy combined with the session mode.
     * When the boost engine put this session into [SessionMonitorMode.EFFICIENT] (real
     * low battery), the effective policy turns auto refresh and the live battery stream
     * OFF for the rest of the session; keep-screen-on still follows the explicit
     * profile policy. Manual refresh is never affected.
     */
    private val effectivePolicyFlow: Flow<MonitoringPolicy?> =
        combine(policyFlow, sessionMonitorController.mode) { policy, mode ->
            when {
                policy == null -> null
                mode == SessionMonitorMode.EFFICIENT -> policy.copy(
                    autoRefreshIntervalMillis = null,
                    liveBatteryEvents = false,
                )

                else -> policy
            }
        }
            .distinctUntilChanged()

    init {
        // Cheap synchronous device facts (Build / Settings.Global reads).
        _uiState.update { it.copy(deviceFacts = deviceInfoRepository.deviceFacts()) }

        refresh()

        // Active profile → policy (legacy "gaming" falls back to Balanced here too).
        viewModelScope.launch {
            activeProfileStore.activeProfileId
                .map { profileRepository.findByIdOrFallback(it) }
                .distinctUntilChanged()
                .collect { profile ->
                    _uiState.update { it.copy(profile = profile) }
                }
        }

        // Session mode → honest chip state (EFFICIENT = boost decided to conserve power).
        viewModelScope.launch {
            sessionMonitorController.mode.collect { mode ->
                _uiState.update { it.copy(sessionMode = mode) }
            }
        }

        // Auto-refresh ticker — ONLY while the screen is lifecycle-active AND the
        // EFFECTIVE policy (profile + session mode) allows in-app monitoring. See the
        // class KDoc for the full lifecycle chain; flatMapLatest cancels the pending
        // delay promptly whenever the screen goes inactive, the profile changes, or the
        // boost engine switches the session to EFFICIENT.
        viewModelScope.launch {
            combine(effectivePolicyFlow, screenActive) { policy, active ->
                if (active && policy != null && policy.inAppMonitoringEnabled) {
                    policy
                } else {
                    null
                }
            }
                .distinctUntilChanged()
                .flatMapLatest { policy ->
                    val interval = policy?.autoRefreshIntervalMillis
                    if (interval == null) {
                        emptyFlow()
                    } else {
                        flow {
                            while (true) {
                                delay(interval)
                                emit(Unit)
                            }
                        }
                    }
                }
                .collect { refresh() }
        }

        // Live battery — collected only while the EFFECTIVE policy allows it. On Battery
        // Saver (or an EFFICIENT session) the stream is never subscribed (no receiver is
        // registered at all); leaving a live profile stops it via collectLatest.
        viewModelScope.launch {
            effectivePolicyFlow
                .map { it?.liveBatteryEvents == true }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (enabled) {
                        monitoringRepository.batteryUpdates().collect { battery ->
                            _uiState.update { it.copy(battery = battery) }
                        }
                    }
                }
        }
    }

    /** Called by the Monitor composable when it reaches ON_START (screen observable). */
    fun onMonitorScreenActive() {
        screenActive.value = true
    }

    /** Called by the Monitor composable on ON_STOP (screen not observable). */
    fun onMonitorScreenInactive() {
        screenActive.value = false
    }

    /**
     * One-shot refresh of everything. On success the state carries the new snapshots
     * and the real snapshot timestamp; on failure an honest stable error key is set
     * (the previous good values stay on screen — nothing is blanked or faked). Manual
     * and init refresh are unaffected by the active profile: they work on every
     * profile, including Battery Saver.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching { monitoringRepository.refresh() }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            battery = snapshot.battery,
                            thermal = snapshot.thermal,
                            headroom = snapshot.headroom,
                            memory = snapshot.memory,
                            cpu = snapshot.cpu,
                            lastUpdatedMillis = snapshot.timestampMillis,
                            errorReason = null,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorReason = UnavailableReasons.API_ERROR,
                        )
                    }
                }
        }
    }
}

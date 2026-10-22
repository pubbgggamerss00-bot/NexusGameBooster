package com.nexus.gamebooster.testing

import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.data.SelectedGamesStore
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameLauncher
import com.nexus.gamebooster.games.InstalledApp
import com.nexus.gamebooster.games.InstalledAppQueryResult
import com.nexus.gamebooster.games.InstalledAppSource
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.DeviceInfoRepository
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.MonitoringRepository
import com.nexus.gamebooster.monitoring.MonitoringSnapshot
import com.nexus.gamebooster.monitoring.PluggedLabels
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalLabels
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.profiles.ProfileIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.io.IOException

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * JVM fakes for the persistence/monitoring/games seams. All of them mirror the REAL
 * implementations' observable contracts (write → flow-emit, failWrites → throw,
 * upsert-by-id, live-collector tracking) so the tests exercise honest seams — never
 * Android, never DataStore, never sleeps.
 */

/**
 * Fake [ActiveProfileStore]: mirrors the real SettingsRepository contract —
 * a write becomes visible through the flow, a failed write throws (callers wrap in
 * runCatching) and leaves the previous value intact. [writeGate] lets a test observe
 * the optimistic UI state while a write is still in flight (one-shot: the next write
 * suspends on it once, then the gate is consumed).
 */
class FakeActiveProfileStore(initialId: String = ProfileIds.BALANCED) : ActiveProfileStore {

    private val _activeProfileId = MutableStateFlow(initialId)
    override val activeProfileId: Flow<String> = _activeProfileId

    /** Total number of write attempts (including gated/failed ones). */
    var writes: Int = 0
        private set

    /** When true, [setActiveProfile] throws a simulated DataStore I/O failure. */
    var failWrites: Boolean = false

    /** When set, the NEXT write suspends on this gate before applying (one-shot). */
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun setActiveProfile(profileId: String) {
        writes++
        val gate = writeGate
        if (gate != null) {
            writeGate = null
            gate.await()
        }
        if (failWrites) throw IOException("FakeActiveProfileStore: simulated write failure")
        _activeProfileId.value = profileId
    }
}

/** Fake [SelectedGamesStore]: in-memory string sets with write → flow-emit. */
class FakeSelectedGamesStore(
    initialPackages: Set<String> = emptySet(),
    initialManualEntries: Set<String> = emptySet(),
) : SelectedGamesStore {

    private val _selectedGamePackages = MutableStateFlow(initialPackages)
    private val _manualGameEntries = MutableStateFlow(initialManualEntries)

    override val selectedGamePackages: Flow<Set<String>> = _selectedGamePackages
    override val manualGameEntries: Flow<Set<String>> = _manualGameEntries

    override suspend fun setSelectedGamePackages(packages: Set<String>) {
        _selectedGamePackages.value = packages
    }

    override suspend fun setManualGameEntries(entries: Set<String>) {
        _manualGameEntries.value = entries
    }
}

/**
 * Fake [MonitoringRepository]: serves a configurable [MonitoringSnapshot] from
 * [refresh] (optionally throwing via [failRefresh]) and counts refreshes. The live
 * battery stream is a [MutableSharedFlow] — [liveCollectors] tracks how many ACTIVE
 * collections exist, so tests can await a subscription before emitting (the flow has
 * no replay: an emission before anybody subscribes is honestly lost, exactly like the
 * real broadcast-based cold flow).
 */
class FakeMonitoringRepository(
    var snapshot: MonitoringSnapshot = testSnapshot(),
) : MonitoringRepository {

    var failRefresh: Boolean = false

    var refreshCount: Int = 0
        private set

    val liveCollectors = MutableStateFlow(0)

    private val batteryFlow = MutableSharedFlow<BatterySnapshot>(extraBufferCapacity = 16)

    override suspend fun refresh(): MonitoringSnapshot {
        refreshCount++
        if (failRefresh) throw IOException("FakeMonitoringRepository: simulated refresh failure")
        return snapshot
    }

    override fun batteryUpdates(): Flow<BatterySnapshot> = batteryFlow
        .onStart { liveCollectors.value += 1 }
        .onCompletion { liveCollectors.value -= 1 }

    /** Emits a live battery snapshot (never suspends; dropped when nobody collects). */
    fun emitBattery(snapshot: BatterySnapshot): Boolean = batteryFlow.tryEmit(snapshot)
}

/** Fake [DeviceInfoRepository]: fixed real-shaped facts and snapshots. */
class FakeDeviceInfoRepository(
    private val facts: DeviceFacts = testDeviceFacts(),
    private var batterySnapshot: BatterySnapshot = testSnapshot().battery,
    private var memorySnapshot: MemorySnapshot = testSnapshot().memory,
) : DeviceInfoRepository {

    override fun deviceFacts(): DeviceFacts = facts

    override fun battery(): BatterySnapshot = batterySnapshot

    override fun memory(): MemorySnapshot = memorySnapshot

    override suspend fun refreshMemory(): MemorySnapshot = memorySnapshot
}

/** Fixed, real-shaped [DeviceFacts] used by the fake device-info repository. */
fun testDeviceFacts(): DeviceFacts = DeviceFacts(
    manufacturer = "Nexus",
    model = "One",
    deviceName = "Nexus One",
    androidVersion = "15",
    sdkLevel = 35,
    securityPatch = "2024-01-01",
)

/**
 * Builds a fully-available [MonitoringSnapshot] with real-shaped values. Every
 * parameter is overridable so a test can model unavailable metrics — a missing value
 * never becomes a zero (honest-results contract).
 */
fun testSnapshot(
    levelPercent: Int? = 80,
    charging: Boolean = false,
    availableMb: Long = 2_048,
    totalMb: Long = 8_192,
    thermalLabel: String? = ThermalLabels.NONE,
    batteryAvailability: MetricAvailability = MetricAvailability.Available,
    timestampMillis: Long = 1_000L,
): MonitoringSnapshot {
    val batteryUnavailable = batteryAvailability is MetricAvailability.Unavailable
    return MonitoringSnapshot(
        battery = BatterySnapshot(
            availability = batteryAvailability,
            levelPercent = if (batteryUnavailable) null else levelPercent,
            charging = if (batteryUnavailable) null else charging,
            pluggedLabel = if (!batteryUnavailable && charging) PluggedLabels.AC else null,
        ),
        thermal = ThermalSnapshot(
            availability = if (thermalLabel == null) {
                MetricAvailability.Unavailable(UnavailableReasons.REQUIRES_ANDROID_10)
            } else {
                MetricAvailability.Available
            },
            statusLabel = thermalLabel,
            severity = if (thermalLabel == null) null else 0,
        ),
        headroom = ThermalHeadroomSnapshot(
            availability = MetricAvailability.Unavailable(UnavailableReasons.HEADROOM_NOT_REPORTED),
            headroomSeconds = null,
            forecastSeconds = 0,
        ),
        memory = MemorySnapshot(
            availability = MetricAvailability.Available,
            totalMb = totalMb,
            availableMb = availableMb,
            availableBytes = availableMb * 1_048_576,
            totalBytes = totalMb * 1_048_576,
            lowMemory = false,
            appHeapUsedMb = 12,
            appHeapAllocatedMb = 64,
        ),
        cpu = CpuSnapshot(availability = MetricAvailability.Available, coreCount = 8),
        timestampMillis = timestampMillis,
    )
}

/**
 * Fake [GameLauncher]: per-package result maps for the two verbs. [launch] and
 * [resolve] record every call so tests can prove resolve-not-launch (and that nothing
 * launches without explicit user consent).
 */
class FakeGameLauncher(
    private val launchResults: Map<String, GameLaunchResult> = emptyMap(),
    private val resolveResults: Map<String, GameLaunchResult> = emptyMap(),
) : GameLauncher {

    val launchCalls = mutableListOf<String>()
    val resolveCalls = mutableListOf<String>()

    override suspend fun launch(packageName: String): GameLaunchResult =
        (launchResults[packageName] ?: GameLaunchResult.NotFound).also { launchCalls += packageName }

    override suspend fun resolve(packageName: String): GameLaunchResult =
        (resolveResults[packageName] ?: GameLaunchResult.NotFound).also { resolveCalls += packageName }
}

/** Fake [InstalledAppSource]: serves a fixed launchable-app catalog, never real scans. */
class FakeInstalledAppSource(
    private val apps: List<InstalledApp> = emptyList(),
) : InstalledAppSource {

    override suspend fun queryLaunchableApps(): InstalledAppQueryResult =
        InstalledAppQueryResult.Success(apps)

    override suspend fun loadIcon(packageName: String): Nothing? = null
}

/**
 * Fake [BoostHistoryStore]: newest-first list backed by a [MutableStateFlow] with the
 * same UPSERT semantics as the real SettingsRepository implementation — a report with
 * an existing id REPLACES its previous version (used by BoostViewModel when appending
 * user-explicit action outcomes to a persisted report).
 */
class FakeBoostHistoryStore : BoostHistoryStore {

    private val _history = MutableStateFlow<List<BoostReport>>(emptyList())
    override val boostHistory: Flow<List<BoostReport>> = _history

    /** Every [addBoostReport] call ever made (upserts included). */
    val addedReports = mutableListOf<BoostReport>()

    override suspend fun addBoostReport(report: BoostReport) {
        addedReports += report
        _history.value = (listOf(report) + _history.value.filterNot { it.id == report.id })
            .sortedByDescending { it.completedAtMillis }
    }

    override suspend fun clearBoostHistory() {
        _history.value = emptyList()
    }
}

/**
 * Fake [com.nexus.gamebooster.optimizer.actions.BoostExplicitActions]: returns
 * configurable outcomes and records every call — proves the user-explicit consent
 * seam (nothing runs unless the user tapped it).
 */
class FakeBoostExplicitActions(
    var batteryOutcome: BoostActionOutcome = BoostActionOutcome.Applied(),
    var detailsOutcome: BoostActionOutcome = BoostActionOutcome.Applied(),
    var launchOutcome: BoostActionOutcome = BoostActionOutcome.Applied(),
) : com.nexus.gamebooster.optimizer.actions.BoostExplicitActions {

    var batteryCalls: Int = 0
        private set
    val detailsCalls = mutableListOf<String?>()
    val launchCalls = mutableListOf<String>()

    override suspend fun openBatterySettings(): BoostActionOutcome =
        batteryOutcome.also { batteryCalls++ }

    override suspend fun openGameDetails(packageName: String?): BoostActionOutcome =
        detailsOutcome.also { detailsCalls += packageName }

    override suspend fun launchGame(packageName: String): BoostActionOutcome =
        launchOutcome.also { launchCalls += packageName }
}

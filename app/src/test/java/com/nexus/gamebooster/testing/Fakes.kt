package com.nexus.gamebooster.testing

import androidx.compose.ui.graphics.ImageBitmap
import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.data.SelectedGamesStore
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameLauncher
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
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalLabels
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.profiles.ProfileIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Fakes for every seam the tested ViewModels / actions / collector depend on.
 *
 * They mirror the REAL interface signatures exactly (read from the main sources in this
 * repo) so a signature drift in production code fails the test COMPILE, not the test.
 * Everything is pure Kotlin — no android.* types, no Robolectric.
 *
 * These tests are executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d.
 */

/**
 * Fake [ActiveProfileStore]. Mirrors the real SettingsRepository behavior: a successful
 * write updates the flow (so collectors "catch up"); a failed write throws and leaves
 * the flow untouched — which is what drives ProfilesViewModel's rollback semantics.
 */
class FakeActiveProfileStore(
    initial: String = ProfileIds.BALANCED,
) : ActiveProfileStore {

    private val _profileId = MutableStateFlow(initial)

    /** Last value the store flow emitted (the real store always emits normalized ids). */
    override val activeProfileId: Flow<String> = _profileId.asStateFlow()

    /** Latest successful write, for asserting what was persisted. */
    var written: String? = null
        private set

    /** When true, [setActiveProfile] throws [IOException] like a DataStore I/O failure. */
    var failWrites = false

    override suspend fun setActiveProfile(profileId: String) {
        if (failWrites) throw IOException("fake disk full")
        written = profileId
        _profileId.value = profileId
    }
}

/** Fake [SelectedGamesStore] backing [com.nexus.gamebooster.games.GameRepository] in tests. */
class FakeSelectedGamesStore(
    initialPackages: Set<String> = emptySet(),
    initialManuals: Set<String> = emptySet(),
) : SelectedGamesStore {

    private val _packages = MutableStateFlow(initialPackages)
    private val _manuals = MutableStateFlow(initialManuals)

    override val selectedGamePackages: Flow<Set<String>> = _packages.asStateFlow()
    override val manualGameEntries: Flow<Set<String>> = _manuals.asStateFlow()

    var lastPackages: Set<String>? = null
        private set
    var lastManuals: Set<String>? = null
        private set

    override suspend fun setSelectedGamePackages(packages: Set<String>) {
        lastPackages = packages
        _packages.value = packages
    }

    override suspend fun setManualGameEntries(entries: Set<String>) {
        lastManuals = entries
        _manuals.value = entries
    }
}

/** Default fully-available snapshot used by most tests. */
fun testSnapshot(
    battery: BatterySnapshot = BatterySnapshot(
        availability = MetricAvailability.Available,
        levelPercent = 87,
        charging = false,
        pluggedLabel = null,
    ),
    thermal: ThermalSnapshot = ThermalSnapshot(
        availability = MetricAvailability.Available,
        statusLabel = ThermalLabels.NONE,
        severity = 0,
    ),
    headroom: ThermalHeadroomSnapshot = ThermalHeadroomSnapshot(
        availability = MetricAvailability.Unavailable(UnavailableReasons.REQUIRES_ANDROID_11),
        headroomSeconds = null,
        forecastSeconds = 0,
    ),
    memory: MemorySnapshot = MemorySnapshot(
        availability = MetricAvailability.Available,
        totalMb = 8192,
        availableMb = 2048,
        availableBytes = 2048L * 1024,
        totalBytes = 8192L * 1024,
        lowMemory = false,
        appHeapUsedMb = 42,
        appHeapAllocatedMb = 64,
    ),
    cpu: CpuSnapshot = CpuSnapshot(availability = MetricAvailability.Available, coreCount = 8),
    timestampMillis: Long = 1_234L,
): MonitoringSnapshot = MonitoringSnapshot(
    battery = battery,
    thermal = thermal,
    headroom = headroom,
    memory = memory,
    cpu = cpu,
    timestampMillis = timestampMillis,
)

/**
 * Fake [MonitoringRepository]. Counts one-shot refreshes (for the Monitor auto-refresh
 * ticker tests, driven by VIRTUAL time) and tracks how many collectors are subscribed
 * to the live battery stream (subscription == a runtime-registered receiver in prod).
 */
class FakeMonitoringRepository(
    initialSnapshot: MonitoringSnapshot = testSnapshot(),
) : MonitoringRepository {

    /** Snapshot returned by [refresh]; swapped per test to simulate device readings. */
    var nextSnapshot: MonitoringSnapshot = initialSnapshot

    /** When true, [refresh] throws — the honest api_error path. */
    var failRefresh = false

    var refreshCount = 0
        private set

    private val _liveCollectors = MutableStateFlow(0)

    /** How many live-battery collectors are currently subscribed (0 or 1 in practice). */
    val liveCollectors: Int
        get() = _liveCollectors.value

    private val _liveBattery = MutableStateFlow<BatterySnapshot?>(null)

    /** Simulates a system battery broadcast arriving while the stream is subscribed. */
    fun emitLiveBattery(snapshot: BatterySnapshot) {
        _liveBattery.value = snapshot
    }

    override suspend fun refresh(): MonitoringSnapshot {
        refreshCount++
        if (failRefresh) throw IllegalStateException("fake refresh failure")
        return nextSnapshot
    }

    override fun batteryUpdates(): Flow<BatterySnapshot> = flow {
        _liveCollectors.value += 1
        try {
            _liveBattery.filterNotNull().collect { emit(it) }
        } finally {
            _liveCollectors.value -= 1
        }
    }
}

/** Fake [DeviceInfoRepository] — plain canned facts/snapshots, no Android Build reads. */
class FakeDeviceInfoRepository(
    private val facts: DeviceFacts = DeviceFacts(
        manufacturer = "Nexus",
        model = "Booster X1",
        deviceName = "Zed's Booster",
        androidVersion = "15",
        sdkLevel = 35,
        securityPatch = "2024-10-05",
    ),
    private val batterySnapshot: BatterySnapshot = BatterySnapshot(
        availability = MetricAvailability.Available,
        levelPercent = 87,
        charging = false,
    ),
    private val memorySnapshot: MemorySnapshot = MemorySnapshot(
        availability = MetricAvailability.Available,
        totalMb = 8192,
        availableMb = 2048,
    ),
) : DeviceInfoRepository {

    override fun deviceFacts(): DeviceFacts = facts

    override fun battery(): BatterySnapshot = batterySnapshot

    override fun memory(): MemorySnapshot = memorySnapshot

    override suspend fun refreshMemory(): MemorySnapshot = memorySnapshot
}

/**
 * Fake [GameLauncher] with a preset result per verb. [launchCalls]/[resolveCalls] record
 * exactly which package was asked for — used to prove PrepareGameLaunchAction never
 * launches and LaunchGameAction never resolves.
 */
class FakeGameLauncher(
    var launchResult: GameLaunchResult = GameLaunchResult.Launched,
    var resolveResult: GameLaunchResult = GameLaunchResult.Ready,
) : GameLauncher {

    val launchCalls = mutableListOf<String>()
    val resolveCalls = mutableListOf<String>()

    override suspend fun launch(packageName: String): GameLaunchResult {
        launchCalls += packageName
        return launchResult
    }

    override suspend fun resolve(packageName: String): GameLaunchResult {
        resolveCalls += packageName
        return resolveResult
    }
}

/** Fake [InstalledAppSource] — canned scan result, never touches PackageManager. */
class FakeInstalledAppSource(
    var queryResult: InstalledAppQueryResult = InstalledAppQueryResult.Success(emptyList()),
) : InstalledAppSource {

    override suspend fun queryLaunchableApps(): InstalledAppQueryResult = queryResult

    override suspend fun loadIcon(packageName: String): ImageBitmap? = null
}

/**
 * Fake [BoostHistoryStore]. Mirrors the REAL SettingsRepository contract: upsert by id,
 * newest-first by completedAtMillis. [added] records every raw add call so tests can
 * distinguish "persisted twice via upsert" from "stored once".
 */
class FakeBoostHistoryStore(
    initial: List<BoostReport> = emptyList(),
) : BoostHistoryStore {

    private val _reports = MutableStateFlow(initial.sortedByDescending { it.completedAtMillis })

    override val boostHistory: StateFlow<List<BoostReport>> = _reports.asStateFlow()

    /** Every add call in order (an upsert appears twice here, once in [reports]). */
    val added = mutableListOf<BoostReport>()

    override suspend fun addBoostReport(report: BoostReport) {
        added += report
        _reports.value = (listOf(report) + _reports.value.filterNot { it.id == report.id })
            .sortedByDescending { it.completedAtMillis }
    }

    override suspend fun clearBoostHistory() {
        _reports.value = emptyList()
    }
}

/**
 * Fake [com.nexus.gamebooster.optimizer.actions.BoostExplicitActions]. Records which
 * explicit verb was called (and with what package) and returns [nextOutcome] — the
 * user-explicit consent contract is exercised in BoostViewModelTest.
 */
class FakeBoostExplicitActions(
    var nextOutcome: BoostActionOutcome = BoostActionOutcome.Applied(),
) : com.nexus.gamebooster.optimizer.actions.BoostExplicitActions {

    val calls = mutableListOf<String>()
    val packages = mutableListOf<String?>()

    override suspend fun openBatterySettings(): BoostActionOutcome =
        nextOutcome.also { calls += "openBatterySettings" }

    override suspend fun openGameDetails(packageName: String?): BoostActionOutcome =
        nextOutcome.also {
            calls += "openGameDetails"
            packages += packageName
        }

    override suspend fun launchGame(packageName: String): BoostActionOutcome =
        nextOutcome.also {
            calls += "launchGame"
            packages += packageName
        }
}

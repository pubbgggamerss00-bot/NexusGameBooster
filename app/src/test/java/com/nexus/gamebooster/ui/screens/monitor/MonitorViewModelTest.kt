package com.nexus.gamebooster.ui.screens.monitor

import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.FakeDeviceInfoRepository
import com.nexus.gamebooster.testing.FakeMonitoringRepository
import com.nexus.gamebooster.testing.MainDispatcherRule
import com.nexus.gamebooster.testing.testDeviceFacts
import com.nexus.gamebooster.testing.testSnapshot
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * ALL 12 tests run under VIRTUAL TIME with the shared Main dispatcher.
 * THE HANG RULES (Task 14-d lessons, honored here):
 *  1. `advanceUntilIdle` is NEVER called while the auto-refresh ticker could be alive —
 *     only `testScheduler.advanceTimeBy` + `runCurrent`.
 *  2. `shutdownForTest()` (cancels `vm.viewModelScope`) is the LAST statement of EVERY
 *     test body, so no ticker survives into runTest's cleanup.
 *  3. State is always awaited with `uiState.first { ... }` — no sleeps anywhere.
 */
class MonitorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var monitoringRepository: FakeMonitoringRepository
    private lateinit var deviceInfoRepository: FakeDeviceInfoRepository
    private lateinit var profileStore: FakeActiveProfileStore
    private lateinit var sessionController: SessionMonitorController

    @Before
    fun setUp() {
        monitoringRepository = FakeMonitoringRepository(testSnapshot())
        deviceInfoRepository = FakeDeviceInfoRepository()
        profileStore = FakeActiveProfileStore(ProfileIds.BALANCED)
        sessionController = SessionMonitorController()
    }

    private fun newViewModel(
        store: FakeActiveProfileStore = profileStore,
    ): MonitorViewModel = MonitorViewModel(
        monitoringRepository = monitoringRepository,
        deviceInfoRepository = deviceInfoRepository,
        profileRepository = StaticProfileRepository(),
        activeProfileStore = store,
        sessionMonitorController = sessionController,
    )

    /** MUST be the last statement of every test body (hang rule #2). */
    private fun shutdownForTest(vm: MonitorViewModel) {
        vm.viewModelScope.cancel()
    }

    // ----------------------------------------------------------- init & refresh

    @Test
    fun init_refreshesOnce_andLoadsDeviceFacts() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        val state = vm.uiState.first { it.battery != null && it.deviceFacts != null }

        assertEquals(1, monitoringRepository.refreshCount)
        assertEquals(testDeviceFacts(), state.deviceFacts)
        assertEquals(80, state.battery?.levelPercent)
        assertFalse(state.battery?.charging ?: true)
        assertEquals("none", state.thermal?.statusLabel)
        assertEquals(2_048L, state.memory?.availableMb)
        assertEquals(8, state.cpu?.coreCount)
        assertEquals(testSnapshot().timestampMillis, state.lastUpdatedMillis)
        assertNull(state.errorReason)
        assertFalse(state.isRefreshing)
        shutdownForTest(vm)
    }

    @Test
    fun refreshFailure_setsApiError_andKeepsThePreviousValues() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.battery != null }

        monitoringRepository.failRefresh = true
        vm.refresh()

        val state = vm.uiState.first { it.errorReason == UnavailableReasons.API_ERROR }
        // Honest degradation: the previous good readings stay on screen, nothing is blanked.
        assertEquals(80, state.battery?.levelPercent)
        assertFalse(state.isRefreshing)
        shutdownForTest(vm)
    }

    // ------------------------------------------------------------------ profile

    @Test
    fun profile_loadsIntoState_andFollowsStoreChanges() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        assertEquals(
            ProfileIds.BALANCED,
            vm.uiState.first { it.profile != null }.profile?.id,
        )

        profileStore.setActiveProfile(ProfileIds.BATTERY_SAVER)
        vm.uiState.first { it.profile?.id == ProfileIds.BATTERY_SAVER }
        shutdownForTest(vm)
    }

    @Test
    fun legacyGamingProfile_normalizedToBalanced() = runTest(mainDispatcherRule.testDispatcher) {
        // The fake store deliberately emits the RAW legacy value: the VM must map it.
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        val vm = newViewModel(store)

        val state = vm.uiState.first { it.profile != null }
        assertEquals(ProfileIds.BALANCED, state.profile?.id)
        shutdownForTest(vm)
    }

    // ------------------------------------------------- auto-refresh tick policy

    @Test
    fun balanced_autoRefreshTicksAt15sBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.onMonitorScreenActive()
        runCurrent()

        assertEquals(1, monitoringRepository.refreshCount) // init refresh only
        testScheduler.advanceTimeBy(14_999)
        assertEquals(1, monitoringRepository.refreshCount) // no tick one millisecond early
        testScheduler.advanceTimeBy(1)
        runCurrent()
        assertEquals(2, monitoringRepository.refreshCount) // ticked at exactly 15 s
        shutdownForTest(vm)
    }

    @Test
    fun performance_autoRefreshTicksAt5s() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val vm = newViewModel(store)
        vm.onMonitorScreenActive()
        runCurrent()

        testScheduler.advanceTimeBy(15_000)
        runCurrent()
        // init refresh + ticks at 5 s, 10 s, 15 s
        assertEquals(4, monitoringRepository.refreshCount)
        shutdownForTest(vm)
    }

    @Test
    fun batterySaver_neverAutoTicks() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.BATTERY_SAVER)
        val vm = newViewModel(store)
        vm.onMonitorScreenActive()
        runCurrent()

        testScheduler.advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, monitoringRepository.refreshCount) // manual refresh stays the only path
        assertEquals(0, monitoringRepository.liveCollectors.value)
        shutdownForTest(vm)
    }

    @Test
    fun screenInactive_pausesTicker_reactivationRestartsIt() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.onMonitorScreenActive()
        runCurrent()
        testScheduler.advanceTimeBy(15_000)
        runCurrent()
        assertEquals(2, monitoringRepository.refreshCount)

        vm.onMonitorScreenInactive()
        testScheduler.advanceTimeBy(45_000)
        runCurrent()
        assertEquals(2, monitoringRepository.refreshCount) // paused while inactive

        vm.onMonitorScreenActive()
        testScheduler.advanceTimeBy(15_000)
        runCurrent()
        assertEquals(3, monitoringRepository.refreshCount) // restarted fresh
        shutdownForTest(vm)
    }

    // ------------------------------------------------------------- live battery

    @Test
    fun liveBattery_collectedOnlyWhenPolicyAllows_andUpdatesState() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel() // Balanced: live battery events on
        monitoringRepository.liveCollectors.first { it >= 1 }

        monitoringRepository.emitBattery(
            BatterySnapshot(
                availability = MetricAvailability.Available,
                levelPercent = 42,
                charging = false,
            )
        )
        val state = vm.uiState.first { it.battery?.levelPercent == 42 }
        assertEquals(42, state.battery?.levelPercent)
        shutdownForTest(vm)
    }

    @Test
    fun batterySaverProfile_unsubscribesTheLiveStream() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        monitoringRepository.liveCollectors.first { it >= 1 }

        profileStore.setActiveProfile(ProfileIds.BATTERY_SAVER)
        monitoringRepository.liveCollectors.first { it == 0 } // collectLatest cancelled the stream
        shutdownForTest(vm)
    }

    // -------------------------------------------------------------- session mode

    @Test
    fun efficientSession_suppressesAutoRefreshAndLive_manualRefreshStillWorks() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.onMonitorScreenActive()
        monitoringRepository.liveCollectors.first { it >= 1 }

        sessionController.setEfficient()
        monitoringRepository.liveCollectors.first { it == 0 }
        vm.uiState.first { it.sessionMode == SessionMonitorMode.EFFICIENT }

        testScheduler.advanceTimeBy(45_000)
        runCurrent()
        assertEquals(1, monitoringRepository.refreshCount) // no auto ticks while EFFICIENT

        vm.refresh() // manual refresh is never affected
        assertEquals(2, monitoringRepository.refreshCount)
        shutdownForTest(vm)
    }

    @Test
    fun efficientSession_onPerformance_keepsKeepScreenOnPolicy_andSuppressesAutomation() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val vm = newViewModel(store)
        vm.onMonitorScreenActive()
        monitoringRepository.liveCollectors.first { it >= 1 }

        sessionController.setEfficient()
        monitoringRepository.liveCollectors.first { it == 0 }
        val state = vm.uiState.first { it.sessionMode == SessionMonitorMode.EFFICIENT }

        testScheduler.advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, monitoringRepository.refreshCount) // 5 s Performance ticker suppressed
        // The profile's own keep-screen-on policy is untouched by the session mode.
        assertTrue(state.profile?.monitoringPolicy?.keepScreenOnWhileVisible == true)
        shutdownForTest(vm)
    }
}

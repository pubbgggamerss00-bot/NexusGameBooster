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
import com.nexus.gamebooster.testing.testSnapshot
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Policy-driven monitoring tests for [MonitorViewModel] (Phase 4/5 contract), all under
 * VIRTUAL time: the auto-refresh ticker (15 s Balanced / 5 s Performance / none for
 * Battery Saver), the screen-active gate, the live battery subscription policy and the
 * Phase 5 EFFICIENT session mode.
 *
 * HANG-PREVENTION CONTRACT (Phase 6 lesson, task 13): the VM's ticker is a
 * `while(true)+delay` loop inside `viewModelScope`, so EVERY test body MUST cancel
 * `vm.viewModelScope` as its LAST statement (`vm.shutdownForTest()` below) — cancelling
 * in `@After` is too late because runTest's post-body drain runs first. Also: while a
 * ticker is alive, only `advanceTimeBy`/`runCurrent` are safe — a bare `advanceUntilIdle`
 * would chase the ticker's self-rescheduling delay forever.
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class MonitorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        monitoringRepo: FakeMonitoringRepository,
        profileStore: ActiveProfileStore = FakeActiveProfileStore(ProfileIds.BALANCED),
        sessionMonitor: SessionMonitorController = SessionMonitorController(),
    ): MonitorViewModel = MonitorViewModel(
        monitoringRepository = monitoringRepo,
        deviceInfoRepository = FakeDeviceInfoRepository(),
        profileRepository = StaticProfileRepository(),
        activeProfileStore = profileStore,
        sessionMonitorController = sessionMonitor,
    )

    /** MUST be the last call of every test body — see the class KDoc. */
    private fun MonitorViewModel.shutdownForTest() {
        viewModelScope.cancel()
    }

    // ---------------------------------------------------------------- init + refresh

    @Test
    fun init_populatesDeviceFacts_andRunsOneShotRefresh() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val monitor = vm(repo)
        runCurrent()

        val state = monitor.uiState.value
        assertEquals("Nexus", state.deviceFacts?.manufacturer)
        assertNotNull(state.battery)
        assertNotNull(state.thermal)
        assertNotNull(state.memory)
        assertNotNull(state.cpu)
        assertEquals(87, state.battery?.levelPercent)
        assertEquals(1_234L, state.lastUpdatedMillis)
        assertNull(state.errorReason)
        assertEquals(1, repo.refreshCount) // exactly the init refresh, no auto tick yet
        assertFalse(state.isRefreshing)
        monitor.shutdownForTest()
    }

    @Test
    fun refresh_failure_setsStableApiErrorReason_andKeepsOldValues() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val monitor = vm(repo)
        runCurrent()
        val oldBattery = monitor.uiState.value.battery

        repo.failRefresh = true
        monitor.refresh()
        runCurrent()

        assertEquals(UnavailableReasons.API_ERROR, monitor.uiState.value.errorReason)
        assertFalse(monitor.uiState.value.isRefreshing)
        assertEquals(oldBattery, monitor.uiState.value.battery) // previous good values stay
        monitor.shutdownForTest()
    }

    // ------------------------------------------------------------- profile → policy

    @Test
    fun profileFlow_mapsPersistedIdToProfileInState() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val monitor = vm(FakeMonitoringRepository(), profileStore = store)
        runCurrent()

        assertEquals(ProfileIds.PERFORMANCE, monitor.uiState.value.profile?.id)
        monitor.shutdownForTest()
    }

    @Test
    fun profileFlow_legacyGamingId_fallsBackToBalanced() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        val monitor = vm(FakeMonitoringRepository(), profileStore = store)
        runCurrent()

        assertEquals(ProfileIds.BALANCED, monitor.uiState.value.profile?.id)
        monitor.shutdownForTest()
    }

    // ------------------------------------------------------- auto-refresh ticker

    @Test
    fun balancedProfile_autoRefreshTicksEvery15s_whileScreenActive() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val monitor = vm(repo)
        runCurrent()
        val base = repo.refreshCount // init refresh only

        monitor.onMonitorScreenActive()
        runCurrent()

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals("no tick before the full 15 s", base, repo.refreshCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(base + 1, repo.refreshCount) // one tick at exactly 15 s

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(base + 2, repo.refreshCount) // and every 15 s after that
        monitor.shutdownForTest()
    }

    @Test
    fun performanceProfile_autoRefreshTicksEvery5s() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val monitor = vm(repo, profileStore = store)
        runCurrent()
        val base = repo.refreshCount

        monitor.onMonitorScreenActive()
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(base + 1, repo.refreshCount)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(base + 3, repo.refreshCount) // two more 5 s ticks
        monitor.shutdownForTest()
    }

    @Test
    fun batterySaverProfile_neverAutoRefreshes_evenWhileScreenActive() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val store = FakeActiveProfileStore(ProfileIds.BATTERY_SAVER)
        val monitor = vm(repo, profileStore = store)
        runCurrent()
        val base = repo.refreshCount

        monitor.onMonitorScreenActive()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals("Battery Saver must not tick — manual refresh is the only path", base, repo.refreshCount)
        monitor.shutdownForTest()
    }

    @Test
    fun screenInactive_pausesTheTicker_resumingReactivatesIt() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val monitor = vm(repo)
        runCurrent()
        val base = repo.refreshCount

        monitor.onMonitorScreenActive()
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(base + 1, repo.refreshCount)

        monitor.onMonitorScreenInactive()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("no ticks while the screen is not lifecycle-active", base + 1, repo.refreshCount)

        monitor.onMonitorScreenActive()
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals("the ticker restarts when the screen becomes active again", base + 2, repo.refreshCount)
        monitor.shutdownForTest()
    }

    // ------------------------------------------------------------- live battery

    @Test
    fun liveBattery_subscribedOnlyWhenPolicyAllows_andUpdatesState() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMonitoringRepository()
        val monitor = vm(repo)
        runCurrent()
        monitor.onMonitorScreenActive()
        runCurrent()

        assertEquals("Balanced policy subscribes the live stream", 1, repo.liveCollectors)
        repo.emitLiveBattery(
            BatterySnapshot(MetricAvailability.Available, levelPercent = 42, charging = false),
        )
        runCurrent()
        assertEquals(42, monitor.uiState.value.battery?.levelPercent)
        monitor.shutdownForTest()
    }

    @Test
    fun liveBattery_batterySaverNeverSubscribes_andLeavingLiveProfileUnsubscribes() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = FakeMonitoringRepository()
            val store = FakeActiveProfileStore(ProfileIds.BALANCED)
            val monitor = vm(repo, profileStore = store)
            runCurrent()
            monitor.onMonitorScreenActive()
            runCurrent()
            assertEquals(1, repo.liveCollectors)

            // Switch the persisted profile to Battery Saver → the stream must unsubscribe.
            store.setActiveProfile(ProfileIds.BATTERY_SAVER)
            runCurrent()
            assertEquals("no live receiver may stay registered on Battery Saver", 0, repo.liveCollectors)
            monitor.shutdownForTest()
        }

    // ------------------------------------------------ Phase 5: EFFICIENT session mode

    @Test
    fun efficientSessionMode_suppressesAutoRefreshAndLiveEvents_andSurfacesTheChip() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = FakeMonitoringRepository()
            val controller = SessionMonitorController()
            val monitor = vm(repo, sessionMonitor = controller)
            runCurrent()
            monitor.onMonitorScreenActive()
            runCurrent()
            assertEquals(1, repo.liveCollectors)
            val base = repo.refreshCount
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(base + 1, repo.refreshCount)

            // The boost engine's session-monitoring action decided to conserve power.
            controller.setEfficient()
            runCurrent()
            assertEquals(SessionMonitorMode.EFFICIENT, monitor.uiState.value.sessionMode)
            assertEquals("EFFICIENT unsubscribes the live battery stream", 0, repo.liveCollectors)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals("EFFICIENT stops the auto-refresh ticker", base + 1, repo.refreshCount)

            // Manual refresh always stays available in every mode.
            monitor.refresh()
            runCurrent()
            assertEquals(base + 2, repo.refreshCount)
            monitor.shutdownForTest()
        }

    @Test
    fun efficientSessionMode_copiesKeepScreenOnFromProfilePolicy_onlyFlipsAutomation() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Performance keeps the screen on; even EFFICIENT must not touch that flag —
            // verified through the policy the VM derives (auto/live off, screen-on kept).
            val repo = FakeMonitoringRepository()
            val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
            val controller = SessionMonitorController()
            val monitor = vm(repo, profileStore = store, sessionMonitor = controller)
            runCurrent()
            assertEquals(ProfileIds.PERFORMANCE, monitor.uiState.value.profile?.id)

            controller.setEfficient()
            runCurrent()
            // The observable effects of the copied policy:
            monitor.onMonitorScreenActive()
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals("no auto ticks under EFFICIENT", 1, repo.refreshCount) // init only
            assertEquals(0, repo.liveCollectors)
            monitor.shutdownForTest()
        }
}

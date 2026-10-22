package com.nexus.gamebooster.ui.screens.home

import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.BoostPrecheckCollector
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.FakeBoostExplicitActions
import com.nexus.gamebooster.testing.FakeBoostEngine
import com.nexus.gamebooster.testing.FakeBoostHistoryStore
import com.nexus.gamebooster.testing.FakeGameLauncher
import com.nexus.gamebooster.testing.FakeMonitoringRepository
import com.nexus.gamebooster.testing.FakeSelectedGamesStore
import com.nexus.gamebooster.testing.MainDispatcherRule
import com.nexus.gamebooster.testing.testSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
 * End-to-end JVM tests for [BoostViewModel]'s boost flow (Phase 5 contract): duplicate
 * prevention, Cancel → CANCELLED persistence via `NonCancellable`, retry gating, honest
 * unavailable pre-checks and user-explicit action upsert.
 *
 * The VM runs against REAL [BoostPrecheckCollector] + [GameRepository] over fakes, so
 * any interface drift fails the compile instead of the test.
 *
 * SHARED-SCHEDULER CONTRACT: every test uses `runTest(mainDispatcherRule.testDispatcher)`
 * (see [MainDispatcherRule]) — the Phase 6 lesson about mismatched schedulers.
 * Cross-thread resumption (the VM's `withContext(Dispatchers.Default)` pre-check phase)
 * is awaited via state conditions (`uiState.first { ... }`), never by sleeps.
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class BoostViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var historyStore: FakeBoostHistoryStore
    private lateinit var profileStore: FakeActiveProfileStore
    private lateinit var gamesStore: FakeSelectedGamesStore
    private lateinit var gameRepo: GameRepository
    private lateinit var gameLauncher: FakeGameLauncher
    private lateinit var monitoringRepo: FakeMonitoringRepository
    private lateinit var explicitActions: FakeBoostExplicitActions

    @Before
    fun setUp() {
        historyStore = FakeBoostHistoryStore()
        profileStore = FakeActiveProfileStore(ProfileIds.BALANCED)
        // Pre-seed the library with one packaged game; GameRepository restores it on init.
        gamesStore = FakeSelectedGamesStore(initialPackages = setOf("com.game.one"))
        gameLauncher = FakeGameLauncher()
        gameRepo = GameRepository(
            installedAppSource = com.nexus.gamebooster.testing.FakeInstalledAppSource(),
            gameLauncher = gameLauncher,
            store = gamesStore,
        )
        monitoringRepo = FakeMonitoringRepository()
        explicitActions = FakeBoostExplicitActions()
    }

    private fun makeVm(engine: BoostEngine): BoostViewModel = BoostViewModel(
        engineFactory = { engine },
        precheckCollector = BoostPrecheckCollector(
            monitoringRepository = monitoringRepo,
            profileRepository = StaticProfileRepository(),
            activeProfileStore = profileStore,
            gameRepository = gameRepo,
        ),
        historyStore = historyStore,
        gameRepository = gameRepo,
        explicitActions = explicitActions,
    )

    /** Deterministically waits for GameRepository's IO-thread seeding to finish. */
    private suspend fun awaitLibrarySeeded() {
        gameRepo.games.first { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ happy path

    @Test
    fun startBoost_runsToEnd_persistsReport_andSurfacesRealChecks() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED)
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BoostPhase.FINISHED, state.phase)
        assertEquals(BoostStatus.COMPLETED, state.status)
        assertFalse(state.isRunning)
        assertTrue(state.showResultDialog)
        assertFalse(state.viewingHistoryReport)
        // Six honest pre-checks: battery level, charging, memory, thermal, profile, game.
        assertEquals(6, state.checks.size)
        assertEquals(
            setOf(
                BoostCheckKeys.BATTERY_LEVEL,
                BoostCheckKeys.BATTERY_CHARGING,
                BoostCheckKeys.MEMORY_AVAILABLE,
                BoostCheckKeys.THERMAL_STATUS,
                BoostCheckKeys.PROFILE,
                BoostCheckKeys.GAME,
            ),
            state.checks.map { it.labelKey }.toSet(),
        )
        assertEquals("87", state.checks.first { it.labelKey == BoostCheckKeys.BATTERY_LEVEL }.valueText)
        assertEquals(ProfileIds.BALANCED, state.report?.profileId)
        assertEquals("com.game.one", state.report?.gameName)
        // The report went through the store exactly once, and the history flow caught up.
        assertEquals(1, historyStore.added.size)
        assertEquals(state.report, historyStore.added.single())
        assertEquals(listOf(state.report?.id), historyStore.boostHistory.value.map { it.id })
        assertEquals(1, state.history.size)
        assertEquals(1, engine.boostCalls)
        // Healthy 87% battery, nothing prepared → no battery hint, no launch buttons.
        assertFalse(state.canOpenBatterySettings)
        assertNull(state.readyToLaunchGame)
    }

    // ------------------------------------------------------- duplicate-run prevention

    @Test
    fun secondStartWhileRunning_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED, gate = gate)
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.RUNNING } // parked inside engine.boost
        vm.startBoost() // duplicate — must be a no-op
        assertEquals(1, engine.boostCalls)

        gate.complete(Unit)
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        advanceUntilIdle()
        assertEquals(1, engine.boostCalls) // still exactly one run at the end
        assertEquals(1, historyStore.added.size)
    }

    // ------------------------------------------------------------------ cancel path

    @Test
    fun cancel_finalizesAsCancelled_andPersistsTheHonestPartialReport() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED, gate = gate) // never completes on its own
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.RUNNING }
        vm.cancelBoost()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BoostPhase.FINISHED, state.phase)
        assertFalse(state.isRunning)
        assertEquals(BoostStatus.CANCELLED, state.status)
        // The CancellationException path persisted the partial report inside NonCancellable.
        assertEquals(1, historyStore.added.size)
        val persisted = historyStore.added.single()
        assertEquals(BoostStatus.CANCELLED, persisted.status)
        assertEquals(6, persisted.checks.size) // real pre-checks survive the cancel
        assertTrue(persisted.actions.isEmpty()) // the engine never finished an action
        assertEquals(state.report?.id, persisted.id)
    }

    // ------------------------------------------------------------------- retry gate

    @Test
    fun retry_afterPartialSuccess_runsAgain() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val engine = FakeBoostEngine(BoostStatus.PARTIAL_SUCCESS)
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals(1, engine.boostCalls)

        vm.retry()
        vm.uiState.first { historyStore.added.size == 2 } // second run finalized
        advanceUntilIdle()
        assertEquals(2, engine.boostCalls)
        assertEquals(BoostStatus.PARTIAL_SUCCESS, vm.uiState.value.status)
    }

    @Test
    fun retry_afterCompleted_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED)
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        vm.retry() // a completed boost has nothing to retry
        runCurrent()
        assertEquals(1, engine.boostCalls)
        assertEquals(1, historyStore.added.size)
    }

    @Test
    fun retry_whileStillRunning_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED, gate = gate)
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.RUNNING }
        vm.retry() // not FINISHED yet — must not start a second engine run
        assertEquals(1, engine.boostCalls) // the original, still-running invocation

        gate.complete(Unit)
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        advanceUntilIdle()
        assertEquals(1, engine.boostCalls)
    }

    // ------------------------------------------------- honest unavailable pre-checks

    @Test
    fun unavailablePreChecks_surfaceReasons_notZeros_andHintBatterySettings() =
        runTest(mainDispatcherRule.testDispatcher) {
            awaitLibrarySeeded()
            monitoringRepo.nextSnapshot = testSnapshot(
                battery = BatterySnapshot(
                    availability = MetricAvailability.Unavailable(UnavailableReasons.NO_READING),
                    levelPercent = null,
                    charging = null,
                ),
                memory = MemorySnapshot(
                    availability = MetricAvailability.Unavailable(UnavailableReasons.API_ERROR),
                    totalMb = null,
                    availableMb = null,
                ),
            )
            val engine = FakeBoostEngine(BoostStatus.COMPLETED)
            val vm = makeVm(engine)

            vm.startBoost()
            vm.uiState.first { it.phase == BoostPhase.FINISHED }
            advanceUntilIdle()

            val checks = vm.uiState.value.checks
            val batteryLevel = checks.first { it.labelKey == BoostCheckKeys.BATTERY_LEVEL }
            assertNull("unavailable battery must NOT become a zero value", batteryLevel.valueText)
            assertEquals(UnavailableReasons.NO_READING, batteryLevel.availabilityReason)
            assertNull(batteryLevel.levelPercent)
            val charging = checks.first { it.labelKey == BoostCheckKeys.BATTERY_CHARGING }
            assertEquals(UnavailableReasons.NO_READING, charging.availabilityReason)
            val memory = checks.first { it.labelKey == BoostCheckKeys.MEMORY_AVAILABLE }
            assertEquals(UnavailableReasons.API_ERROR, memory.availabilityReason)
            assertNull(memory.valueText)
            // Unavailable battery is exactly what enables the battery-settings hint.
            assertTrue(vm.uiState.value.canOpenBatterySettings)
        }

    // -------------------------------------------------- user-explicit actions (consent)

    @Test
    fun launchReadyGame_appendsOutcome_andUpsertsTheSameReport() =
        runTest(mainDispatcherRule.testDispatcher) {
            awaitLibrarySeeded()
            val engine = FakeBoostEngine(
                status = BoostStatus.PARTIAL_SUCCESS,
                onBoost = { session ->
                    session.record(
                        BoostActionIds.PREPARE_GAME_LAUNCH,
                        BoostActionOutcome.Applied(BoostDetailKeys.READY_TO_LAUNCH),
                    )
                },
            )
            val vm = makeVm(engine)

            vm.startBoost()
            vm.uiState.first { it.phase == BoostPhase.FINISHED }
            advanceUntilIdle()
            assertEquals("com.game.one", vm.uiState.value.readyToLaunchGame)
            assertEquals("com.game.one", vm.uiState.value.readyToLaunchGameName)

            explicitActions.nextOutcome = BoostActionOutcome.Applied()
            vm.launchReadyGame()
            advanceUntilIdle()

            // The explicit outcome was appended to the CURRENT report and re-persisted
            // via the store's upsert semantics (two add calls, still ONE report).
            assertEquals(2, historyStore.added.size)
            assertEquals(1, historyStore.boostHistory.value.size)
            val persisted = historyStore.boostHistory.value.single()
            assertEquals(
                listOf(BoostActionIds.PREPARE_GAME_LAUNCH, BoostActionIds.LAUNCH_GAME),
                persisted.actions.map { it.actionId },
            )
            assertTrue(persisted.actions.last().outcome is BoostActionOutcome.Applied)
            assertEquals(listOf("launchGame", "com.game.one"), explicitActions.calls + explicitActions.packages)
            assertEquals(
                listOf(BoostActionIds.PREPARE_GAME_LAUNCH, BoostActionIds.LAUNCH_GAME),
                vm.uiState.value.report?.actions?.map { it.actionId },
            )
        }

    @Test
    fun launchReadyGame_withoutAPreparedGame_isANoOp() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val engine = FakeBoostEngine(BoostStatus.COMPLETED) // no prepare applied
        val vm = makeVm(engine)

        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        advanceUntilIdle()
        assertNull(vm.uiState.value.readyToLaunchGame)

        // Launch AND details are both gated on a prepared game — neither may fire.
        vm.launchReadyGame()
        vm.openGameDetails()
        runCurrent()
        assertEquals(emptyList<String>(), explicitActions.calls)
    }
}

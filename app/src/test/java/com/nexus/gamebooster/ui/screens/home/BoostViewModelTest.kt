package com.nexus.gamebooster.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.games.InstalledApp
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.BoostPrecheckCollector
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.optimizer.DefaultBoostEngine
import com.nexus.gamebooster.optimizer.actions.ApplyProfileAction
import com.nexus.gamebooster.optimizer.actions.PrepareGameLaunchAction
import com.nexus.gamebooster.optimizer.actions.SessionMonitoringAction
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.FakeBoostEngine
import com.nexus.gamebooster.testing.FakeBoostExplicitActions
import com.nexus.gamebooster.testing.FakeBoostHistoryStore
import com.nexus.gamebooster.testing.FakeGameLauncher
import com.nexus.gamebooster.testing.FakeInstalledAppSource
import com.nexus.gamebooster.testing.FakeMonitoringRepository
import com.nexus.gamebooster.testing.FakeSelectedGamesStore
import com.nexus.gamebooster.testing.MainDispatcherRule
import com.nexus.gamebooster.testing.testReport
import com.nexus.gamebooster.testing.testSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * [BoostViewModel] end-to-end over fakes plus the REAL [BoostPrecheckCollector] and a
 * REAL [GameRepository]: the full run (6 real checks, report persisted once), the
 * duplicate-start guard, Cancel → CANCELLED persisted inside NonCancellable, retry
 * after PARTIAL_SUCCESS, blocks after COMPLETED / while RUNNING, the user-explicit
 * launch append (upsert semantics), the no-prepared-game no-op, and unavailable
 * pre-checks surfacing stable reason keys — never a zero. All awaits are
 * `uiState.first { ... }` — no sleeps anywhere.
 */
class BoostViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var profileStore: FakeActiveProfileStore
    private lateinit var gamesStore: FakeSelectedGamesStore
    private lateinit var installedAppSource: FakeInstalledAppSource
    private lateinit var gameLauncher: FakeGameLauncher
    private lateinit var gameRepository: GameRepository
    private lateinit var monitoringRepository: FakeMonitoringRepository
    private lateinit var historyStore: FakeBoostHistoryStore
    private lateinit var explicitActions: FakeBoostExplicitActions
    private lateinit var sessionController: SessionMonitorController

    @Before
    fun setUp() {
        profileStore = FakeActiveProfileStore(ProfileIds.BALANCED)
        gamesStore = FakeSelectedGamesStore(initialPackages = setOf("com.cool.game"))
        installedAppSource = FakeInstalledAppSource(
            listOf(InstalledApp(packageName = "com.cool.game", label = "Cool Game", isGame = true, category = 0))
        )
        gameLauncher = FakeGameLauncher(
            launchResults = mapOf("com.cool.game" to GameLaunchResult.Launched),
            resolveResults = mapOf("com.cool.game" to GameLaunchResult.Ready),
        )
        gameRepository = GameRepository(installedAppSource, gameLauncher, gamesStore)
        // 15 % on battery power → the session-monitoring action APPLIES (efficient mode)
        // → the real three-action engine reports COMPLETED.
        monitoringRepository = FakeMonitoringRepository(testSnapshot(levelPercent = 15, charging = false))
        historyStore = FakeBoostHistoryStore()
        explicitActions = FakeBoostExplicitActions()
        sessionController = SessionMonitorController()
    }

    /** Waits for the repository's own seed coroutine (runs on Dispatchers.IO). */
    private suspend fun awaitLibrarySeeded() {
        gameRepository.games.first { it.isNotEmpty() }
    }

    private fun realEngineFactory(): (String?) -> BoostEngine = { packageName ->
        DefaultBoostEngine(
            autoActions = listOf(
                ApplyProfileAction(StaticProfileRepository(), profileStore),
                SessionMonitoringAction(sessionController),
                PrepareGameLaunchAction(gameLauncher, packageName),
            )
        )
    }

    private fun newViewModel(
        engineFactory: (String?) -> BoostEngine = realEngineFactory(),
        gameRepository: GameRepository = this.gameRepository,
    ): BoostViewModel = BoostViewModel(
        engineFactory = engineFactory,
        precheckCollector = BoostPrecheckCollector(
            monitoringRepository = monitoringRepository,
            profileRepository = StaticProfileRepository(),
            activeProfileStore = profileStore,
            gameRepository = gameRepository,
        ),
        historyStore = historyStore,
        gameRepository = gameRepository,
        explicitActions = explicitActions,
    )

    @Test
    fun fullRun_endToEnd_persistsReportOnce() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        gameRepository.refreshInstalledApps()
        val vm = newViewModel()

        vm.startBoost()
        val state = vm.uiState.first { it.phase == BoostPhase.FINISHED }

        assertEquals(BoostStatus.COMPLETED, state.status)
        // 6 real pre-checks in stable order, incl. the real game name.
        assertEquals(6, state.checks.size)
        assertEquals(
            listOf(
                BoostCheckKeys.BATTERY_LEVEL,
                BoostCheckKeys.BATTERY_CHARGING,
                BoostCheckKeys.MEMORY_AVAILABLE,
                BoostCheckKeys.THERMAL_STATUS,
                BoostCheckKeys.PROFILE,
                BoostCheckKeys.GAME,
            ),
            state.checks.map { it.labelKey },
        )
        assertEquals("Cool Game", state.checks.last().valueText)
        // Exactly one precheck collect and exactly one persist.
        assertEquals(1, monitoringRepository.refreshCount)
        assertEquals(1, historyStore.addedReports.size)
        assertEquals(1, historyStore.boostHistory.first().size)

        val report = state.report!!
        assertEquals(
            listOf(
                BoostActionIds.APPLY_PROFILE,
                BoostActionIds.SESSION_MONITORING,
                BoostActionIds.PREPARE_GAME_LAUNCH,
            ),
            report.actions.map { it.actionId },
        )
        assertTrue(report.actions[0].outcome is BoostActionOutcome.AlreadyApplied)
        assertTrue(report.actions[1].outcome is BoostActionOutcome.Applied)
        assertEquals(
            BoostDetailKeys.READY_TO_LAUNCH,
            (report.actions[2].outcome as BoostActionOutcome.Applied).detailKey,
        )
        assertEquals("com.cool.game", state.readyToLaunchGame)
        assertEquals("Cool Game", state.readyToLaunchGameName)
        // 15 % is at/below the low-battery hint threshold — the honest hint shows.
        assertTrue(state.canOpenBatterySettings)
        assertTrue(state.showResultDialog)
        assertFalse(state.isRunning)
        // The real session-monitoring action switched this app to efficient monitoring.
        assertEquals(SessionMonitorMode.EFFICIENT, sessionController.mode.value)
    }

    @Test
    fun duplicateStart_whileRunning_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(gate = gate)
        val vm = newViewModel(engineFactory = { engine })

        vm.startBoost()
        // Await RUNNING before the duplicate tap — the guard is only meaningful once
        // a run is actually active (the prechecks run on the real Default pool).
        vm.uiState.first { it.phase == BoostPhase.RUNNING }
        vm.startBoost() // second tap while running → no-op
        assertEquals(1, engine.invocations)
        assertTrue(vm.uiState.value.isRunning)

        gate.complete(Unit)
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals(1, engine.invocations)
        assertEquals(1, historyStore.addedReports.size)
    }

    @Test
    fun cancel_persistsCancelledReport_insideNonCancellable() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(gate = gate)
        val vm = newViewModel(engineFactory = { engine })

        vm.startBoost()
        // The prechecks hop through the REAL Dispatchers.Default pool — never assume
        // RUNNING synchronously; await it through the state flow (Task 14-d rule).
        vm.uiState.first { it.phase == BoostPhase.RUNNING }
        assertTrue(vm.uiState.value.isRunning)
        vm.cancelBoost()

        val state = vm.uiState.first {
            it.phase == BoostPhase.FINISHED && it.status == BoostStatus.CANCELLED
        }
        assertFalse(state.isRunning)
        // The honest partial report survived the cancellation path.
        assertEquals(1, historyStore.addedReports.size)
        val persisted = historyStore.addedReports.single()
        assertEquals(BoostStatus.CANCELLED, persisted.status)
        assertTrue(persisted.actions.isEmpty())
        assertTrue(persisted.checks.isNotEmpty())
    }

    @Test
    fun retry_afterPartialSuccess_runsAgain() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        // 80 % battery → session-monitoring skips (battery_healthy) → PARTIAL_SUCCESS.
        monitoringRepository.snapshot = testSnapshot(levelPercent = 80, charging = false)
        val vm = newViewModel()

        vm.startBoost()
        val first = vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals(BoostStatus.PARTIAL_SUCCESS, first.status)
        assertEquals(1, historyStore.addedReports.size)

        vm.retry()
        val second = vm.uiState.first {
            it.phase == BoostPhase.FINISHED && it.report != null && it.report != first.report
        }
        assertEquals(BoostStatus.PARTIAL_SUCCESS, second.status)
        assertEquals(2, historyStore.addedReports.size)
        assertFalse(vm.uiState.value.isRunning)
    }

    @Test
    fun retry_afterCompleted_isBlocked() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        // Phase 10.5 (network-tools update): the boost targets the library's single
        // LAUNCHABLE game — the catalog must be hydrated first (same pattern as the
        // other real-engine test below), otherwise launchable is still false.
        gameRepository.refreshInstalledApps()
        val vm = newViewModel()
        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals(BoostStatus.COMPLETED, vm.uiState.value.status)

        vm.retry() // a completed boost has nothing to retry

        assertEquals(1, historyStore.addedReports.size)
        assertFalse(vm.uiState.value.isRunning)
        assertEquals(BoostPhase.FINISHED, vm.uiState.value.phase)
    }

    @Test
    fun secondStartAndHistoryView_whileRunning_areBlocked() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeBoostEngine(gate = gate)
        val vm = newViewModel(engineFactory = { engine })

        vm.startBoost()
        // Reach RUNNING before the second attempt — with a StandardTestDispatcher the
        // VM coroutine does not advance until a suspension point pumps it, and the
        // duplicate-start guard is only meaningful once a run is actually active.
        vm.uiState.first { it.phase == BoostPhase.RUNNING }
        vm.startBoost() // blocked while RUNNING
        vm.viewHistoryReport(testReport("historical", BoostStatus.COMPLETED, completedAtMillis = 123L))
        assertEquals(1, engine.invocations)
        assertFalse(vm.uiState.value.viewingHistoryReport)

        gate.complete(Unit)
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals(1, engine.invocations)
        assertEquals(1, historyStore.addedReports.size)
    }

    @Test
    fun launchReadyGame_appendsAndUpsertsTheReport() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        gameRepository.refreshInstalledApps()
        val vm = newViewModel()
        vm.startBoost()
        vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertEquals("com.cool.game", vm.uiState.value.readyToLaunchGame)

        vm.launchReadyGame()
        vm.uiState.first { it.report?.actions?.size == 4 }

        assertEquals(listOf("com.cool.game"), explicitActions.launchCalls)
        // Upsert, NOT duplicate: two persist calls, still ONE history entry.
        assertEquals(2, historyStore.addedReports.size)
        assertEquals(1, historyStore.boostHistory.first().size)
        val appended = vm.uiState.value.report!!.actions.last()
        assertEquals(BoostActionIds.LAUNCH_GAME, appended.actionId)
        assertTrue(appended.outcome is BoostActionOutcome.Applied)
    }

    @Test
    fun explicitActions_noOpWithoutPreparedGame() = runTest(mainDispatcherRule.testDispatcher) {
        // Empty library: nothing selected → the prepare action honestly skips.
        val emptyStore = FakeSelectedGamesStore()
        val emptyRepository = GameRepository(installedAppSource, gameLauncher, emptyStore)
        val vm = newViewModel(gameRepository = emptyRepository)

        vm.startBoost()
        val state = vm.uiState.first { it.phase == BoostPhase.FINISHED }
        assertNull(state.readyToLaunchGame)
        assertNull(state.readyToLaunchGameName)

        vm.launchReadyGame()
        vm.openGameDetails()
        assertTrue(explicitActions.launchCalls.isEmpty())
        assertTrue(explicitActions.detailsCalls.isEmpty())
    }

    @Test
    fun unavailablePrechecks_surfaceReasonKeys_neverZero() = runTest(mainDispatcherRule.testDispatcher) {
        awaitLibrarySeeded()
        monitoringRepository.snapshot = testSnapshot(
            levelPercent = null,
            batteryAvailability = MetricAvailability.Unavailable(UnavailableReasons.API_ERROR),
        )
        val vm = newViewModel()

        vm.startBoost()
        val state = vm.uiState.first { it.phase == BoostPhase.FINISHED }

        val batteryCheck = state.checks.first { it.labelKey == BoostCheckKeys.BATTERY_LEVEL }
        assertNull(batteryCheck.valueText)
        assertNull(batteryCheck.levelPercent)
        assertEquals(UnavailableReasons.API_ERROR, batteryCheck.availabilityReason)
        val chargingCheck = state.checks.first { it.labelKey == BoostCheckKeys.BATTERY_CHARGING }
        assertNull(chargingCheck.valueText)
        assertEquals(UnavailableReasons.API_ERROR, chargingCheck.availabilityReason)
        // The battery needs attention → the report suggests the battery-settings button.
        assertTrue(state.canOpenBatterySettings)
        assertEquals(BoostStatus.PARTIAL_SUCCESS, state.status)
    }
}

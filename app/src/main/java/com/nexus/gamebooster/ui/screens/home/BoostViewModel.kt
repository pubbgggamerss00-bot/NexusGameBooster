package com.nexus.gamebooster.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.R
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostPrecheckCollector
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostRequest
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.optimizer.actions.BoostExplicitActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.Dispatchers

/** Boost run phase. [FINISHED] covers completed / partial / cancelled / failed runs. */
enum class BoostPhase { IDLE, CHECKING, RUNNING, FINISHED }

/**
 * One boost-related line for the Home activity log. [messageRes] carries the sentence;
 * the status argument of "Boost finished: %1$s" is a string RESOURCE ([argRes]) so the
 * localized status name resolves at render time.
 */
data class BoostLogEvent(
    val messageRes: Int,
    val arg: String? = null,
    val argRes: Int? = null,
)

data class BoostUiState(
    val phase: BoostPhase = BoostPhase.IDLE,
    val status: BoostStatus? = null,
    /** The finished (or running) report; non-null whenever a run produced results. */
    val report: BoostReport? = null,
    /** Pre-checks captured for the current/most recent run — shown live while running. */
    val checks: List<BoostCheck> = emptyList(),
    val isRunning: Boolean = false,
    /** Persisted boost reports (newest first) from [BoostHistoryStore]. */
    val history: List<BoostReport> = emptyList(),
    /**
     * Package of the game the PrepareGameLaunchAction verified as launchable ("ready to
     * launch"); null when no game was prepared. Launch/Details buttons show only when set.
     */
    val readyToLaunchGame: String? = null,
    /** Display name matching [readyToLaunchGame], for honest button labels. */
    val readyToLaunchGameName: String? = null,
    /**
     * True when the battery pre-check was unavailable or at/below the low threshold —
     * the reason the report UI suggests the (always available) Battery settings button.
     */
    val canOpenBatterySettings: Boolean = false,
    /** True while the boost result/history dialog is visible. */
    val showResultDialog: Boolean = false,
    /** True when the dialog currently shows a HISTORICAL report (read-only — no actions). */
    val viewingHistoryReport: Boolean = false,
)

/**
 * Phase 5 Safe Boost Engine ViewModel. Owns the WHOLE boost flow end-to-end:
 *
 *  1. `startBoost()` — duplicate-guarded (a running job blocks a second start), builds
 *     the [BoostRequest] from the REAL library (first game that has a package), runs the
 *     [BoostPrecheckCollector] on Dispatchers.Default, then the engine. Every final
 *     report (completed / partial / cancelled / failed) is persisted through
 *     [BoostHistoryStore.addBoostReport] inside `NonCancellable`, so even a cancelled or
 *     dying ViewModel records what ACTUALLY happened.
 *  2. `cancelBoost()` — cancels the engine job; the CancellationException path
 *     finalizes the run as [BoostStatus.CANCELLED] (in `NonCancellable`) and persists
 *     the honest partial report.
 *  3. `retry()` — allowed from FINISHED while the status is not COMPLETED.
 *  4. Explicit user-requested actions (`openBatterySettings`, `openGameDetails`,
 *     `launchReadyGame`) — executed on tap only, appended to the CURRENT report and
 *     re-persisted via the store's upsert semantics; the boost status itself is NOT
 *     recomputed (it reflects the auto-run at boost time).
 *
 * Honesty: the state carries REAL outcomes only — no percentages, no before/after, no
 * invented values. Unavailable pre-checks surface as stable reason keys.
 */
class BoostViewModel(
    private val engineFactory: (String?) -> BoostEngine,
    private val precheckCollector: BoostPrecheckCollector,
    private val historyStore: BoostHistoryStore,
    private val gameRepository: GameRepository,
    private val explicitActions: BoostExplicitActions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoostUiState())
    val uiState: StateFlow<BoostUiState> = _uiState.asStateFlow()

    private val _logEvents = MutableSharedFlow<BoostLogEvent>(extraBufferCapacity = 16)
    val logEvents: SharedFlow<BoostLogEvent> = _logEvents.asSharedFlow()

    private var boostJob: Job? = null

    /** Package chosen for the current run (kept for the ready-to-launch buttons). */
    private var targetPackageName: String? = null

    /** The session of the most recent run — explicit actions append to ITS report. */
    private var currentSession: BoostSession? = null

    init {
        // Persisted history → state (the dialog and the Home history card read from here).
        viewModelScope.launch {
            historyStore.boostHistory.collect { reports ->
                _uiState.update { it.copy(history = reports) }
            }
        }
    }

    /**
     * Starts one boost run. A second start while a run is active is a no-op (duplicate
     * guard: active job OR `isRunning` flag).
     */
    fun startBoost() {
        if (boostJob?.isActive == true || _uiState.value.isRunning) return
        boostJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = BoostPhase.CHECKING,
                    isRunning = true,
                    status = null,
                    report = null,
                    checks = emptyList(),
                    readyToLaunchGame = null,
                    readyToLaunchGameName = null,
                    showResultDialog = true,
                    viewingHistoryReport = false,
                )
            }
            log(R.string.activity_boost_started)

            // Never pick an arbitrary game. Boost only when the library has exactly
            // one launchable package; otherwise avoid running game-specific actions
            // against a random app. A future selector can pass the chosen package here.
            val games = gameRepository.games.value
            val launchableGames = games.filter { !it.packageName.isNullOrBlank() && it.launchable }
            val targetGame = launchableGames.singleOrNull()
            targetPackageName = targetGame?.packageName
            val request = BoostRequest(gameId = targetGame?.id, gameName = targetGame?.name)

            val checks = withContext(Dispatchers.Default) {
                precheckCollector.collect(request)
            }
            val session = BoostSession(
                checks = checks,
                profileId = checks.firstOrNull { it.labelKey == BoostCheckKeys.PROFILE }?.valueText,
                gameName = request.gameName,
                startedAtMillis = System.currentTimeMillis(),
            )
            currentSession = session
            _uiState.update { it.copy(phase = BoostPhase.RUNNING, checks = checks) }

            try {
                val engine = engineFactory(targetGame?.packageName)
                val status = engine.boost(session, request)
                finalizeRun(status, session)
            } catch (ce: CancellationException) {
                // Prompt-cancel contract: finalize + persist INSIDE NonCancellable so the
                // honest partial report survives even if this ViewModel is being destroyed.
                withContext(NonCancellable) {
                    finalizeRun(BoostStatus.CANCELLED, session)
                }
                throw ce
            }
        }
    }

    /** Cancels the running boost (Cancel button). No-op when nothing runs. */
    fun cancelBoost() {
        boostJob?.cancel()
    }

    /**
     * Runs the boost again. Allowed only from FINISHED when the previous run was not a
     * full success (a completed boost has nothing to retry).
     */
    fun retry() {
        val state = _uiState.value
        if (state.phase != BoostPhase.FINISHED) return
        if (state.status == BoostStatus.COMPLETED) return
        startBoost()
    }

    /** Closes the result/history dialog. */
    fun dismissDialog() {
        _uiState.update { it.copy(showResultDialog = false) }
    }

    /** Opens the result dialog for a HISTORICAL report (read-only — no action buttons). */
    fun viewHistoryReport(report: BoostReport) {
        if (boostJob?.isActive == true || _uiState.value.isRunning) return
        _uiState.update {
            it.copy(
                showResultDialog = true,
                viewingHistoryReport = true,
                report = report,
                status = report.status,
                checks = report.checks,
                phase = BoostPhase.FINISHED,
            )
        }
    }

    /** USER-EXPLICIT: opens the system battery settings screen (consent = the tap itself). */
    fun openBatterySettings() {
        val session = currentSession ?: return
        viewModelScope.launch {
            val outcome = explicitActions.openBatterySettings()
            appendExplicitAction(session, BoostActionIds.OPEN_BATTERY_SETTINGS, outcome)
        }
    }

    /** USER-EXPLICIT: opens the system App-Info screen for the prepared game. */
    fun openGameDetails() {
        val session = currentSession ?: return
        val packageName = _uiState.value.readyToLaunchGame ?: return
        viewModelScope.launch {
            val outcome = explicitActions.openGameDetails(packageName)
            appendExplicitAction(session, BoostActionIds.OPEN_GAME_DETAILS, outcome)
        }
    }

    /** USER-EXPLICIT: launches the prepared game (only offered when it verified ready). */
    fun launchReadyGame() {
        val session = currentSession ?: return
        val packageName = _uiState.value.readyToLaunchGame ?: return
        viewModelScope.launch {
            val outcome = explicitActions.launchGame(packageName)
            appendExplicitAction(session, BoostActionIds.LAUNCH_GAME, outcome)
        }
    }

    // -------------------------------------------------------------- internals

    /**
     * Builds the final [BoostReport] from the session, persists it (upsert) and moves
     * the UI to [BoostPhase.FINISHED]. Called inside `NonCancellable` on the cancel
     * path; the persist itself is additionally guarded so VM death cannot lose it.
     */
    private suspend fun finalizeRun(status: BoostStatus, session: BoostSession) {
        val completedAt = System.currentTimeMillis()
        val report = BoostReport(
            id = UUID.randomUUID().toString(),
            startedAtMillis = session.startedAtMillis,
            completedAtMillis = completedAt,
            profileId = session.profileId,
            gameName = session.gameName,
            checks = session.checks,
            actions = session.actions,
            status = status,
        )
        withContext(NonCancellable) {
            historyStore.addBoostReport(report)
        }

        val prepareApplied = session.actions.any {
            it.actionId == BoostActionIds.PREPARE_GAME_LAUNCH &&
                it.outcome is BoostActionOutcome.Applied
        }
        val battery = session.checks.firstOrNull { it.labelKey == BoostCheckKeys.BATTERY_LEVEL }
        val batteryNeedsAttention = battery?.availabilityReason != null ||
            (battery?.levelPercent?.let { it <= LOW_BATTERY_HINT_THRESHOLD } == true)

        _uiState.update {
            it.copy(
                phase = BoostPhase.FINISHED,
                isRunning = false,
                status = status,
                report = report,
                checks = session.checks,
                readyToLaunchGame = if (prepareApplied) targetPackageName else null,
                readyToLaunchGameName = if (prepareApplied) session.gameName else null,
                canOpenBatterySettings = batteryNeedsAttention,
                showResultDialog = true,
                viewingHistoryReport = false,
            )
        }
        when (status) {
            BoostStatus.CANCELLED -> log(R.string.activity_boost_cancelled)
            else -> log(R.string.activity_boost_completed, argRes = statusLabelRes(status))
        }
    }

    /**
     * Appends a user-explicit action outcome to the CURRENT report and re-persists it
     * via the store's UPSERT semantics (same id → replaced). Design choice (documented):
     * the boost status is NOT recomputed — it reflects the auto-run at boost time; the
     * appended record is honest extra history of what the user did from the report.
     */
    private suspend fun appendExplicitAction(
        session: BoostSession,
        actionId: String,
        outcome: BoostActionOutcome,
    ) {
        session.record(actionId, outcome)
        val updated = currentReport()?.copy(actions = session.actions) ?: return
        withContext(NonCancellable) {
            historyStore.addBoostReport(updated)
        }
        _uiState.update { it.copy(report = updated) }
        _logEvents.emit(BoostLogEvent(R.string.activity_boost_action_recorded))
    }

    private fun currentReport(): BoostReport? = _uiState.value.report

    private fun log(messageRes: Int, arg: String? = null, argRes: Int? = null) {
        _logEvents.tryEmit(BoostLogEvent(messageRes, arg, argRes))
    }

    companion object {
        /** Battery level at/below which the report UI hints at the battery settings button. */
        const val LOW_BATTERY_HINT_THRESHOLD = 20

        fun statusLabelRes(status: BoostStatus): Int = when (status) {
            BoostStatus.COMPLETED -> R.string.boost_status_completed
            BoostStatus.PARTIAL_SUCCESS -> R.string.boost_status_partial
            BoostStatus.CANCELLED -> R.string.boost_status_cancelled
            BoostStatus.FAILED -> R.string.boost_status_failed
        }
    }
}

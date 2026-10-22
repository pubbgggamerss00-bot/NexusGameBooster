package com.nexus.gamebooster.optimizer

import com.nexus.gamebooster.monitoring.UnavailableReasons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stable action ids recorded in [BoostActionRecord.actionId] and mapped to localized
 * action labels by the UI. The first three run automatically inside the engine; the
 * last three are USER-EXPLICIT only — they never run inside [DefaultBoostEngine] and
 * execute only when the user taps the corresponding button in the report UI (consent).
 */
object BoostActionIds {
    const val APPLY_PROFILE = "apply_profile"
    const val SESSION_MONITORING = "session_monitoring"
    const val PREPARE_GAME_LAUNCH = "prepare_game_launch"
    const val OPEN_BATTERY_SETTINGS = "open_battery_settings"
    const val OPEN_GAME_DETAILS = "open_game_details"
    const val LAUNCH_GAME = "launch_game"

    // Phase 9 — real aggressive actions (require Shizuku)
    const val FORCE_STOP_BACKGROUND = "force_stop_background"
    const val PROTECT_GAME = "protect_game"
    const val FREE_MEMORY = "free_memory"
    const val COMPILE_GAME = "compile_game"
    const val SYSTEM_TWEAKS = "system_tweaks"
}

/**
 * Stable label keys for [BoostCheck.labelKey]. The UI resolves each key to a localized
 * label (see `boostCheckLabel` in `core/UiMessage.kt`); actions additionally locate the
 * checks they need by these keys.
 */
object BoostCheckKeys {
    const val BATTERY_LEVEL = "battery_level"
    const val BATTERY_CHARGING = "battery_charging"
    const val MEMORY_AVAILABLE = "memory_available"
    const val THERMAL_STATUS = "thermal_status"
    const val PROFILE = "profile"
    const val GAME = "game"
}

/**
 * Machine value keys for the battery-charging check's [BoostCheck.valueText] (the
 * boolean itself is additionally carried in the typed [BoostCheck.charging] payload).
 * The UI resolves them to localized text — the raw keys are never shown.
 */
object BoostCheckValues {
    const val CHARGING_YES = "charging_yes"
    const val CHARGING_NO = "charging_no"
}

/**
 * Stable detail keys for [BoostActionOutcome.Applied.detailKey] — localized by the UI
 * (see `boostDetailLabel`). Null detailKey means "applied, nothing more to say".
 */
object BoostDetailKeys {
    const val READY_TO_LAUNCH = "ready_to_launch"
}

/**
 * Outcome of ONE boost action. Every variant is honest:
 *
 *  - [Applied]: the action actually performed its in-app work this time.
 *  - [AlreadyApplied]: the wanted state was already in place, so nothing was written.
 *    This is an HONEST skipped-success — it counts towards COMPLETED, and the report
 *    says so instead of pretending fresh work happened.
 *  - [Skipped]: the action decided (from real data) that running it would not help.
 *  - [Unsupported]: the action cannot work on this device/OS (permanent).
 *  - [Failed]: the action tried and failed; [message] carries a short technical cause.
 *
 * The sealed hierarchy is @Serializable (polymorphic via @SerialName discriminators)
 * so whole reports persist to DataStore as JSON.
 */
@Serializable
sealed class BoostActionOutcome {

    /** The action really did its (in-app) work. [detailKey] optionally adds a stable detail. */
    @Serializable
    @SerialName("applied")
    data class Applied(val detailKey: String? = null) : BoostActionOutcome()

    /**
     * The wanted state already held — nothing was written this run. An honest
     * skipped-success: counts as success for the boost status, never claimed as new work.
     */
    @Serializable
    @SerialName("already_applied")
    data class AlreadyApplied(val detailKey: String? = null) : BoostActionOutcome()

    /** Deliberately not run; [reasonKey] is a stable [com.nexus.gamebooster.monitoring.UnavailableReasons] key. */
    @Serializable
    @SerialName("skipped")
    data class Skipped(val reasonKey: String) : BoostActionOutcome()

    /** Cannot work on this device/OS; [reasonKey] is a stable machine key. */
    @Serializable
    @SerialName("unsupported")
    data class Unsupported(val reasonKey: String) : BoostActionOutcome()

    /** Ran and failed; [reasonKey] is stable, [message] is a short technical cause (may be null). */
    @Serializable
    @SerialName("failed")
    data class Failed(val reasonKey: String, val message: String? = null) : BoostActionOutcome()
}

/** One recorded action outcome with the REAL wall-clock time it happened. */
@Serializable
data class BoostActionRecord(
    val actionId: String,
    val outcome: BoostActionOutcome,
    val atMillis: Long,
)

/**
 * One pre-check reading captured before/while the boost runs.
 *
 * HONESTY CONTRACT: [valueText] is a raw formatted value built by the collector with
 * explicit units where applicable (e.g. "87" for battery percent, "1842" for available
 * MB — the UI attaches the localized unit/label); [availabilityReason] is a stable
 * [com.nexus.gamebooster.monitoring.UnavailableReasons] key used INSTEAD of a value when
 * the metric could not be read. A missing metric is NEVER rendered or stored as zero.
 *
 * [levelPercent] / [charging] are optional typed payloads used ONLY by actions to make
 * decisions without parsing display strings; the UI still renders [valueText].
 */
@Serializable
data class BoostCheck(
    val labelKey: String,
    val valueText: String? = null,
    val availabilityReason: String? = null,
    val capturedAtMillis: Long,
    /** Typed battery level (percent) for action decisions; null when unavailable/not applicable. */
    val levelPercent: Int? = null,
    /** Typed charging flag for action decisions; null when unavailable/not applicable. */
    val charging: Boolean? = null,
)

/** Final status of a boost run. Never a percentage, never a before/after comparison. */
enum class BoostStatus {
    /** Every auto-run action applied (or honestly [BoostActionOutcome.AlreadyApplied]). */
    COMPLETED,

    /** No action failed, but at least one was skipped or unsupported. */
    PARTIAL_SUCCESS,

    /** The user cancelled the run; the honest partial report is still persisted. */
    CANCELLED,

    /** At least one action failed. */
    FAILED,
}

/**
 * Persisted report of one boost run.
 *
 * HONESTY: a report contains ONLY what actually happened — real pre-check values (or
 * availability reasons), typed action outcomes with real timestamps, and the real start/
 * completion times. It contains NO invented percentages and NO before/after comparisons.
 */
@Serializable
data class BoostReport(
    val id: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val profileId: String? = null,
    val gameName: String? = null,
    val checks: List<BoostCheck> = emptyList(),
    val actions: List<BoostActionRecord> = emptyList(),
    val status: BoostStatus,
)

/**
 * Request for one boost run. [gameId]/[gameName] are optional: null means a general
 * session boost with no game launch preparation (that action will honestly skip).
 */
data class BoostRequest(
    val gameId: String? = null,
    val gameName: String? = null,
)

/**
 * Persistence seam for boost reports (implemented by `SettingsRepository` on the app's
 * existing DataStore). Newest-first by [BoostReport.completedAtMillis].
 *
 * `addBoostReport` is an UPSERT: a report with the same id replaces its previous
 * version. The BoostViewModel uses this to append user-explicit action outcomes to an
 * already-persisted report without duplicating it (documented design choice).
 */
interface BoostHistoryStore {
    /** Persisted reports, newest first. Corrupt entries are skipped, never crash. */
    val boostHistory: Flow<List<BoostReport>>

    /** Inserts (or replaces, same id) the report and prunes the history to its cap. */
    suspend fun addBoostReport(report: BoostReport)

    /** Removes every persisted report. */
    suspend fun clearBoostHistory()
}

/**
 * The running boost's shared state. Created by the ViewModel per run; it carries the
 * pre-checks plus the game/profile context, and collects action records AS THEY HAPPEN
 * — so a CANCELLED run can still persist an honest partial report.
 *
 * The action log is guarded by a synchronized list ([record] may be called from the
 * engine coroutine and from explicit user-action launches).
 */
class BoostSession(
    val checks: List<BoostCheck>,
    val profileId: String?,
    val gameName: String?,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {

    private val recordedActions = mutableListOf<BoostActionRecord>()

    /** Immutable snapshot of all recorded action outcomes, in execution order. */
    val actions: List<BoostActionRecord>
        get() = synchronized(recordedActions) { recordedActions.toList() }

    /** Records one action outcome with the REAL current wall-clock time. */
    fun record(actionId: String, outcome: BoostActionOutcome) {
        synchronized(recordedActions) {
            recordedActions.add(
                BoostActionRecord(
                    actionId = actionId,
                    outcome = outcome,
                    atMillis = System.currentTimeMillis(),
                )
            )
        }
    }
}

/** One runnable boost action. Implementations must be safe to call from any coroutine. */
interface BoostAction {
    /** Stable id from [BoostActionIds]; used as the record key in [BoostSession.actions]. */
    val id: String

    /**
     * Executes the action. May suspend. MUST throw [kotlinx.coroutines.CancellationException]
     * untouched if the caller's coroutine is cancelled; every OTHER failure is returned
     * as [BoostActionOutcome.Failed] (or thrown — the engine converts unexpected
     * throwables to Failed records).
     */
    suspend fun execute(session: BoostSession): BoostActionOutcome
}

/**
 * Contract for the boost orchestration layer (the Phase 5 replacement of the Phase 1
 * no-op stub). Implementations run the auto-run action list and compute the final
 * [BoostStatus] from ACTUAL outcomes only.
 */
interface BoostEngine {
    suspend fun boost(session: BoostSession, request: BoostRequest): BoostStatus
}

/**
 * Default engine: runs [autoActions] sequentially, honestly, and never crashes on an
 * action.
 *
 * CANCELLATION CONTRACT (exact): [kotlinx.coroutines.ensureActive] is called BEFORE EACH
 * action, so a cancelled caller stops promptly — the thrown CancellationException
 * propagates OUT of [boost] and is never recorded as a Failed outcome. The caller
 * (BoostViewModel) then finalizes the run as [BoostStatus.CANCELLED] inside
 * `NonCancellable` and persists the honest partial report from the session.
 *
 * FAILURE CONTRACT: each action's `execute` runs inside a try/catch — an unexpected
 * Throwable is recorded as [BoostActionOutcome.Failed] with the [com.nexus.gamebooster.monitoring.UnavailableReasons.API_ERROR]
 * key and the run CONTINUES with the next action (one broken action cannot abort the
 * report). A CancellationException is rethrown, never swallowed.
 *
 * STATUS RULE (locked design): any Failed → [BoostStatus.FAILED]; else any
 * Skipped/Unsupported → [BoostStatus.PARTIAL_SUCCESS]; else [BoostStatus.COMPLETED]
 * (AlreadyApplied counts as success).
 */
class DefaultBoostEngine(
    private val autoActions: List<BoostAction>,
) : BoostEngine {

    override suspend fun boost(session: BoostSession, request: BoostRequest): BoostStatus {
        var sawFailed = false
        var sawSkipped = false
        for (action in autoActions) {
            // Prompt cancellation BETWEEN actions — and never mid-record. The thrown
            // CancellationException propagates; the caller finalizes as CANCELLED.
            currentCoroutineContext().ensureActive()
            val outcome = try {
                action.execute(session)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // An action crashing must not crash the engine: record an honest failure
                // and continue with the remaining actions.
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = t.message ?: t.javaClass.simpleName,
                )
            }
            session.record(action.id, outcome)
            when (outcome) {
                is BoostActionOutcome.Failed -> sawFailed = true
                is BoostActionOutcome.Skipped, is BoostActionOutcome.Unsupported -> sawSkipped = true
                is BoostActionOutcome.Applied, is BoostActionOutcome.AlreadyApplied -> Unit
            }
        }
        return when {
            sawFailed -> BoostStatus.FAILED
            sawSkipped -> BoostStatus.PARTIAL_SUCCESS
            else -> BoostStatus.COMPLETED
        }
    }
}

package com.nexus.gamebooster.testing

import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostActionRecord
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostCheckValues
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostRequest
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.optimizer.BoostStatus
import kotlinx.coroutines.CompletableDeferred

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * Boost-engine test doubles: a gateable fake [BoostEngine], simple stub/recording
 * [BoostAction]s, and builders for checks, battery-shaped sessions and reports.
 */

/**
 * Fake [BoostEngine]: counts invocations, captures sessions/requests, runs an optional
 * [onBoost] hook, and (when [gate] is set) suspends on it before returning [status] —
 * used to observe BoostViewModel states WHILE a run is in flight (duplicate-start
 * guard, cancel path, blocked-while-running).
 */
class FakeBoostEngine(
    private val status: BoostStatus = BoostStatus.COMPLETED,
    private val gate: CompletableDeferred<Unit>? = null,
    private val onBoost: ((BoostSession, BoostRequest) -> Unit)? = null,
) : BoostEngine {

    var invocations: Int = 0
        private set

    val sessions = mutableListOf<BoostSession>()
    val requests = mutableListOf<BoostRequest>()

    override suspend fun boost(session: BoostSession, request: BoostRequest): BoostStatus {
        invocations++
        sessions += session
        requests += request
        onBoost?.invoke(session, request)
        gate?.await()
        return status
    }
}

/** A [BoostAction] that records every execution (order, sessions) and returns [outcome]. */
class RecordingAction(override val id: String) : BoostAction {

    var executed: Boolean = false
        private set

    val sessions = mutableListOf<BoostSession>()

    var outcome: BoostActionOutcome = BoostActionOutcome.Applied()

    override suspend fun execute(session: BoostSession): BoostActionOutcome =
        outcome.also {
            executed = true
            sessions += session
        }
}

/** Builds a one-shot [BoostAction] stub with a fixed [outcome] and optional hook. */
fun stubAction(
    id: String,
    outcome: BoostActionOutcome,
    onExecute: (BoostSession) -> Unit = {},
): BoostAction = object : BoostAction {
    override val id: String = id
    override suspend fun execute(session: BoostSession): BoostActionOutcome =
        outcome.also { onExecute(session) }
}

/** Builds a [BoostCheck] with the stable label keys and typed payloads. */
fun boostCheck(
    labelKey: String,
    valueText: String? = null,
    availabilityReason: String? = null,
    capturedAtMillis: Long = 0L,
    levelPercent: Int? = null,
    charging: Boolean? = null,
): BoostCheck = BoostCheck(
    labelKey = labelKey,
    valueText = valueText,
    availabilityReason = availabilityReason,
    capturedAtMillis = capturedAtMillis,
    levelPercent = levelPercent,
    charging = charging,
)

/**
 * Builds a [BoostSession] whose checks look exactly like what the REAL
 * BoostPrecheckCollector attaches for battery reads: typed [levelPercent] /
 * [charging] payloads (nullable — an unavailable read must stay null, never zero).
 */
fun batterySession(
    levelPercent: Int? = null,
    charging: Boolean? = null,
    profileId: String? = null,
    gameName: String? = null,
): BoostSession = BoostSession(
    checks = listOf(
        boostCheck(
            labelKey = BoostCheckKeys.BATTERY_LEVEL,
            valueText = levelPercent?.toString(),
            levelPercent = levelPercent,
        ),
        boostCheck(
            labelKey = BoostCheckKeys.BATTERY_CHARGING,
            valueText = when (charging) {
                true -> BoostCheckValues.CHARGING_YES
                false -> BoostCheckValues.CHARGING_NO
                null -> null
            },
            charging = charging,
        ),
    ),
    profileId = profileId,
    gameName = gameName,
)

/** Builds a persisted-report-shaped [BoostReport] for history assertions. */
fun testReport(
    id: String,
    status: BoostStatus,
    completedAtMillis: Long,
    profileId: String? = null,
    gameName: String? = null,
    checks: List<BoostCheck> = emptyList(),
    actions: List<BoostActionRecord> = emptyList(),
): BoostReport = BoostReport(
    id = id,
    startedAtMillis = completedAtMillis - 1_000,
    completedAtMillis = completedAtMillis,
    profileId = profileId,
    gameName = gameName,
    checks = checks,
    actions = actions,
    status = status,
)

/** Convenience: the three auto-run action ids in their fixed engine order. */
val defaultAutoActionIds: List<String> = listOf(
    BoostActionIds.APPLY_PROFILE,
    BoostActionIds.SESSION_MONITORING,
    BoostActionIds.PREPARE_GAME_LAUNCH,
)

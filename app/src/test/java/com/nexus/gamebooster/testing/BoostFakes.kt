package com.nexus.gamebooster.testing

import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostActionRecord
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostRequest
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.optimizer.BoostStatus
import kotlinx.coroutines.CompletableDeferred

/**
 * Boost-specific fakes and builders for the engine / action / ViewModel tests.
 *
 * These tests are executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d.
 */

/**
 * Fake [BoostEngine]: counts invocations, optionally hands the session to [onBoost]
 * (so a test can record outcomes into the session exactly like real actions do),
 * and optionally suspends on [gate] so the caller (test) controls when the run
 * completes — which is how duplicate-run prevention and Cancel are exercised
 * deterministically (no sleeps; cancellation arrives while the engine is parked).
 */
class FakeBoostEngine(
    private val status: BoostStatus = BoostStatus.COMPLETED,
    private val gate: CompletableDeferred<Unit>? = null,
    private val onBoost: (BoostSession) -> Unit = {},
) : BoostEngine {

    var boostCalls = 0
        private set

    val sessions = mutableListOf<BoostSession>()

    override suspend fun boost(session: BoostSession, request: BoostRequest): BoostStatus {
        boostCalls++
        sessions += session
        onBoost(session)
        gate?.await()
        return status
    }
}

/** Builds a [BoostAction] whose execute runs [block] (and records nothing on its own). */
fun stubAction(
    id: String,
    block: suspend (BoostSession) -> BoostActionOutcome = { BoostActionOutcome.Applied() },
): BoostAction = object : BoostAction {
    override val id: String = id
    override suspend fun execute(session: BoostSession): BoostActionOutcome = block(session)
}

/** A [BoostAction] that only records its id in [executed] and applies. */
fun recordingAction(id: String, executed: MutableList<String>): BoostAction = stubAction(id) {
    executed += id
    BoostActionOutcome.Applied()
}

/** A pre-check list element matching what [BoostPrecheckCollector] really produces. */
fun boostCheck(
    labelKey: String,
    valueText: String? = null,
    availabilityReason: String? = null,
    levelPercent: Int? = null,
    charging: Boolean? = null,
    capturedAtMillis: Long = 0L,
): BoostCheck = BoostCheck(
    labelKey = labelKey,
    valueText = valueText,
    availabilityReason = availabilityReason,
    capturedAtMillis = capturedAtMillis,
    levelPercent = levelPercent,
    charging = charging,
)

/**
 * Builds a [BoostSession] with the battery pre-checks [SessionMonitoringAction] reads.
 * [level] == null produces an UNAVAILABLE level check (availability reason set, no
 * typed payload) — the honest "battery could not be read" shape.
 */
fun batterySession(
    level: Int?,
    charging: Boolean?,
    profileId: String? = "balanced",
): BoostSession {
    val levelCheck = if (level == null) {
        boostCheck(
            BoostCheckKeys.BATTERY_LEVEL,
            availabilityReason = com.nexus.gamebooster.monitoring.UnavailableReasons.NO_READING,
        )
    } else {
        boostCheck(BoostCheckKeys.BATTERY_LEVEL, valueText = level.toString(), levelPercent = level)
    }
    val chargingCheck = if (charging == null) {
        boostCheck(
            BoostCheckKeys.BATTERY_CHARGING,
            availabilityReason = com.nexus.gamebooster.monitoring.UnavailableReasons.NO_READING,
        )
    } else {
        boostCheck(BoostCheckKeys.BATTERY_CHARGING, charging = charging)
    }
    return BoostSession(
        checks = listOf(levelCheck, chargingCheck),
        profileId = profileId,
        gameName = null,
    )
}

/** Minimal valid [BoostReport] for history-store tests. */
fun testReport(
    id: String = "report-1",
    completedAtMillis: Long = 100L,
    status: BoostStatus = BoostStatus.COMPLETED,
    checks: List<BoostCheck> = emptyList(),
    actions: List<com.nexus.gamebooster.optimizer.BoostActionRecord> = emptyList(),
): BoostReport = BoostReport(
    id = id,
    startedAtMillis = completedAtMillis - 5,
    completedAtMillis = completedAtMillis,
    profileId = "balanced",
    gameName = null,
    checks = checks,
    actions = actions,
    status = status,
)

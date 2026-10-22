package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameLauncher
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.optimizer.BoostSession

/**
 * Auto-run boost action #3: verifies — WITHOUT launching anything — that the boost's
 * target game still resolves to a launchable activity, so the report can honestly say
 * "ready to launch" before the user decides to tap Launch.
 *
 * The request-scoped package is carried by THIS action's constructor (set by the
 * ViewModel when it builds the engine for a specific run) — the engine stays
 * game-agnostic.
 *
 * Outcomes:
 *  - `packageName == null` → `Skipped(no_game_selected)` (nothing selected to prepare).
 *  - [GameLaunchResult.Ready] → [BoostActionOutcome.Applied] with detail `ready_to_launch`.
 *  - [GameLaunchResult.NoLaunchIntent] → `Unsupported(no_launch_intent)`.
 *  - [GameLaunchResult.NotFound] → `Skipped(game_not_found)` (uninstalled since added,
 *    or a manual entry whose package no longer exists).
 *  - [GameLaunchResult.Error] → `Failed(api_error, technical reason)`.
 *
 * NO startActivity happens here — the actual launch stays an explicit user action.
 */
class PrepareGameLaunchAction(
    private val gameLauncher: GameLauncher,
    private val packageName: String?,
) : BoostAction {

    override val id: String = BoostActionIds.PREPARE_GAME_LAUNCH

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (packageName.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }
        return when (val resolved = gameLauncher.resolve(packageName)) {
            is GameLaunchResult.Ready ->
                BoostActionOutcome.Applied(detailKey = BoostDetailKeys.READY_TO_LAUNCH)

            is GameLaunchResult.NoLaunchIntent ->
                BoostActionOutcome.Unsupported(UnavailableReasons.NO_LAUNCH_INTENT)

            is GameLaunchResult.NotFound ->
                BoostActionOutcome.Skipped(UnavailableReasons.GAME_NOT_FOUND)

            is GameLaunchResult.Launched ->
                // A resolve() must never launch; if an implementation confused the two
                // verbs, record it honestly as a failure rather than claim readiness.
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = "resolve() unexpectedly launched the game",
                )

            is GameLaunchResult.Error ->
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = resolved.reason,
                )
        }
    }
}

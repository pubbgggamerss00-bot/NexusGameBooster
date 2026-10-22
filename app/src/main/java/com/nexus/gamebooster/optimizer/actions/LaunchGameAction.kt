package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameLauncher
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession

/**
 * USER-EXPLICIT action (never part of [com.nexus.gamebooster.optimizer.DefaultBoostEngine]'s
 * auto list): actually launches the prepared game via the existing [GameLauncher] — the
 * same honest path the Library screen uses (`startActivity`, `FLAG_ACTIVITY_NEW_TASK`).
 *
 * CONSENT: run ONLY when the user taps "Launch" in the boost report UI, and only when a
 * previous PrepareGameLaunchAction reported the game `ready_to_launch`.
 *
 * Outcomes: [GameLaunchResult.Launched] → `Applied`; [GameLaunchResult.NoLaunchIntent] →
 * `Unsupported(no_launch_intent)`; [GameLaunchResult.NotFound] → `Skipped(game_not_found)`;
 * [GameLaunchResult.Error] → `Failed(api_error, reason)`.
 */
class LaunchGameAction(
    private val gameLauncher: GameLauncher,
    private val packageName: String,
) : BoostAction {

    override val id: String = BoostActionIds.LAUNCH_GAME

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        return when (val result = gameLauncher.launch(packageName)) {
            is GameLaunchResult.Launched -> BoostActionOutcome.Applied()
            is GameLaunchResult.NoLaunchIntent ->
                BoostActionOutcome.Unsupported(UnavailableReasons.NO_LAUNCH_INTENT)
            is GameLaunchResult.NotFound ->
                BoostActionOutcome.Skipped(UnavailableReasons.GAME_NOT_FOUND)
            is GameLaunchResult.Ready ->
                // launch() never returns Ready; if an implementation did, treat it as
                // "prepared but not started" — honest, and never a fake success.
                BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED)
            is GameLaunchResult.Error ->
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = result.reason,
                )
        }
    }
}

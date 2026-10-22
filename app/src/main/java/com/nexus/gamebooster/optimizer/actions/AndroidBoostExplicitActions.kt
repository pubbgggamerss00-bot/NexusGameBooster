package com.nexus.gamebooster.optimizer.actions

import android.content.Context
import com.nexus.gamebooster.games.GameLauncher
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession

/**
 * Android implementation of [BoostExplicitActions]: constructs the corresponding real
 * action (`OpenBatterySettingsAction` / `OpenGameDetailsAction` / `LaunchGameAction`)
 * and executes it on the caller's context.
 *
 * The actions' `execute(session)` signatures ignore the session (they do not read
 * pre-checks), so a lightweight throwaway session is passed — documented at each call
 * site. The [context] MUST be an application context: the actions call `startActivity`
 * with `FLAG_ACTIVITY_NEW_TASK` and never need an Activity.
 */
class AndroidBoostExplicitActions(
    private val appContext: Context,
    private val gameLauncher: GameLauncher,
) : BoostExplicitActions {

    override suspend fun openBatterySettings(): BoostActionOutcome =
        OpenBatterySettingsAction(appContext).execute(newSession())

    override suspend fun openGameDetails(packageName: String?): BoostActionOutcome =
        OpenGameDetailsAction(appContext, packageName).execute(newSession())

    override suspend fun launchGame(packageName: String): BoostActionOutcome =
        LaunchGameAction(gameLauncher, packageName).execute(newSession())

    /** Explicit actions do not read pre-checks; they only need a session to satisfy the contract. */
    private fun newSession(): BoostSession =
        BoostSession(checks = emptyList(), profileId = null, gameName = null)
}

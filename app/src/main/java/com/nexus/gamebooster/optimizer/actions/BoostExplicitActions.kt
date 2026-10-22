package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.optimizer.BoostActionOutcome

/**
 * The three USER-EXPLICIT boost actions (battery settings / game details / launch),
 * behind an interface so [com.nexus.gamebooster.ui.screens.home.BoostViewModel] can be
 * exercised on the JVM with fakes — the Android implementation needs a Context and is
 * wired in `ServiceLocator`.
 *
 * Each call runs the corresponding BoostAction once, right now, on an explicit user tap
 * (consent), and returns the honest outcome for the ViewModel to record into the
 * current report. None of these ever run inside the engine's auto list.
 */
interface BoostExplicitActions {
    /** Opens the system battery settings screen (`ACTION_BATTERY_SAVER_SETTINGS`). */
    suspend fun openBatterySettings(): BoostActionOutcome

    /** Opens the system App-Info screen for [packageName] (null → honest skip). */
    suspend fun openGameDetails(packageName: String?): BoostActionOutcome

    /** Launches [packageName] via the existing [com.nexus.gamebooster.games.GameLauncher]. */
    suspend fun launchGame(packageName: String): BoostActionOutcome
}

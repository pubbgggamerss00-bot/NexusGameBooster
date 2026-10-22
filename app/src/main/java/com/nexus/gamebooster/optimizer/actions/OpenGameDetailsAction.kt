package com.nexus.gamebooster.optimizer.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession

/**
 * USER-EXPLICIT action (never part of [com.nexus.gamebooster.optimizer.DefaultBoostEngine]'s
 * auto list): opens the system App-Info screen for the boost's target game
 * (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
 *
 * CONSENT: run ONLY when the user taps "Game details" in the boost report UI. The app
 * never opens system screens on its own, and nothing on the detail screen is read,
 * written or changed by this app.
 *
 * Outcomes: no game package → `Skipped(no_game_selected)`; started → `Applied`; the
 * settings screen does not exist / no handler → `Unsupported(no_settings_screen)`.
 */
class OpenGameDetailsAction(
    private val context: Context,
    private val packageName: String?,
) : BoostAction {

    override val id: String = BoostActionIds.OPEN_GAME_DETAILS

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (packageName.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            BoostActionOutcome.Applied()
        } catch (_: ActivityNotFoundException) {
            BoostActionOutcome.Unsupported(UnavailableReasons.NO_SETTINGS_SCREEN)
        } catch (t: Throwable) {
            BoostActionOutcome.Failed(
                reasonKey = UnavailableReasons.API_ERROR,
                message = t.message ?: t.javaClass.simpleName,
            )
        }
    }
}

package com.nexus.gamebooster.optimizer.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession

/**
 * USER-EXPLICIT action (never part of [com.nexus.gamebooster.optimizer.DefaultBoostEngine]'s
 * auto list): opens the system battery settings screen
 * (`Settings.ACTION_BATTERY_SAVER_SETTINGS`).
 *
 * CONSENT: run ONLY when the user taps the "Battery settings" button in the boost report
 * UI — the app never opens system screens on its own. This is a plain user-visible
 * navigation to a standard system screen: no setting is read, written or toggled.
 *
 * Outcomes: started → `Applied`; no activity handles the intent (rare vendor builds) →
 * `Unsupported(no_settings_screen)`.
 */
class OpenBatterySettingsAction(
    private val context: Context,
) : BoostAction {

    override val id: String = BoostActionIds.OPEN_BATTERY_SETTINGS

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
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

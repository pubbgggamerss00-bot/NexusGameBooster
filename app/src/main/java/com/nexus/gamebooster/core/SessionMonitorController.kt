package com.nexus.gamebooster.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app session monitoring mode.
 *
 *  - [NORMAL]: the Monitor screen follows the active profile's [com.nexus.gamebooster.profiles.MonitoringPolicy].
 *  - [EFFICIENT]: the boost engine's session-monitoring action decided (from REAL battery
 *    pre-checks) that the device is on a low battery, so this app conserves power for the
 *    rest of the session: auto refresh is off and the live battery stream is paused
 *    (manual refresh always stays available).
 */
enum class SessionMonitorMode { NORMAL, EFFICIENT }

/**
 * Process-scoped holder for the current [SessionMonitorMode].
 *
 * HONESTY + SCOPE: this is a plain in-memory [StateFlow] — no persistence, no DataStore
 * key, no system change of any kind. Setting EFFICIENT affects ONLY how NexusGameBooster
 * itself monitors on the Monitor screen for the CURRENT app session; it never touches the
 * device, other apps, frequencies, thermal limits or battery settings. The mode resets to
 * [SessionMonitorMode.NORMAL] on process death (deliberate: it is a session decision).
 *
 * A single instance lives in `ServiceLocator`; the boost engine writes it (via the
 * session-monitoring action) and `MonitorViewModel` reads it to combine the profile
 * policy with the session decision. Tests construct their own instances.
 */
class SessionMonitorController {

    private val _mode = MutableStateFlow(SessionMonitorMode.NORMAL)
    val mode: StateFlow<SessionMonitorMode> = _mode.asStateFlow()

    /** Switches this app's monitoring to the power-efficient session mode. */
    fun setEfficient() {
        _mode.value = SessionMonitorMode.EFFICIENT
    }

    /** Returns to the normal session mode (used by tests; process death also resets). */
    fun reset() {
        _mode.value = SessionMonitorMode.NORMAL
    }
}

package com.nexus.gamebooster.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexus.gamebooster.R
import com.nexus.gamebooster.monitoring.ChargingLabels
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.MetricRowKeys
import com.nexus.gamebooster.monitoring.PluggedLabels
import com.nexus.gamebooster.monitoring.ThermalLabels
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostCheckValues
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.optimizer.actions.BoostDetailKeysExtra

/**
 * A pending transient UI message (snackbar). [messageRes] is a string resource and
 * [arg] an optional display argument; [id] makes every emission distinct so the same
 * message can be shown twice in a row. The data layer never sees this class —
 * repositories emit stable machine keys, the UI layer wraps them here.
 */
data class UiMessage(
    val id: Long,
    val messageRes: Int,
    val arg: String? = null,
)

@Composable
fun uiMessageText(message: UiMessage): String =
    if (message.arg != null) {
        stringResource(message.messageRes, message.arg)
    } else {
        stringResource(message.messageRes)
    }

/**
 * UI-side mapping of stable machine-readable keys (repository layer) to localized text.
 * Keeping reasons as constants keeps the data layer free of Android resources while
 * every user-visible sentence stays translatable.
 */

@Composable
fun availabilityLabel(availability: MetricAvailability): String = when (availability) {
    is MetricAvailability.Available -> stringResource(R.string.chip_ok)
    is MetricAvailability.Unavailable -> unavailableReasonLabel(availability.reason)
}

@Composable
fun unavailableReasonLabel(reason: String): String = when (reason) {
    UnavailableReasons.PHASE_3 -> stringResource(R.string.reason_phase_3)
    UnavailableReasons.REQUIRES_ANDROID_10 -> stringResource(R.string.reason_requires_android_10)
    UnavailableReasons.REQUIRES_ANDROID_11 -> stringResource(R.string.reason_requires_android_11)
    UnavailableReasons.HEADROOM_NOT_REPORTED -> stringResource(R.string.reason_headroom_not_reported)
    UnavailableReasons.NO_READING -> stringResource(R.string.reason_no_reading)
    UnavailableReasons.API_ERROR -> stringResource(R.string.reason_api_error)
    UnavailableReasons.SCAN_FAILED -> stringResource(R.string.reason_scan_failed)
    UnavailableReasons.PROFILE_SAVE_FAILED -> stringResource(R.string.profile_save_failed)
    // Phase 5 — Safe Boost Engine keys.
    UnavailableReasons.ALREADY_APPLIED -> stringResource(R.string.reason_already_applied)
    UnavailableReasons.NOT_NEEDED -> stringResource(R.string.reason_not_needed)
    UnavailableReasons.BATTERY_HEALTHY -> stringResource(R.string.reason_battery_healthy)
    UnavailableReasons.BATTERY_UNAVAILABLE -> stringResource(R.string.reason_battery_unavailable)
    UnavailableReasons.ALREADY_EFFICIENT -> stringResource(R.string.reason_already_efficient)
    UnavailableReasons.NO_GAME_SELECTED -> stringResource(R.string.reason_no_game_selected)
    UnavailableReasons.GAME_NOT_FOUND -> stringResource(R.string.reason_game_not_found)
    UnavailableReasons.NO_LAUNCH_INTENT -> stringResource(R.string.reason_no_launch_intent)
    UnavailableReasons.NO_SETTINGS_SCREEN -> stringResource(R.string.reason_no_settings_screen)
    UnavailableReasons.SHIZUKU_REQUIRED -> stringResource(R.string.reason_shizuku_required)
    else -> reason // Unknown key: show the raw value rather than inventing an explanation.
}

/** Maps a stable thermal label key to localized text; unknown keys fall back to "Unknown". */
@Composable
fun thermalStatusLabel(label: String?): String? = when (label) {
    ThermalLabels.NONE -> stringResource(R.string.thermal_none)
    ThermalLabels.LIGHT -> stringResource(R.string.thermal_light)
    ThermalLabels.MODERATE -> stringResource(R.string.thermal_moderate)
    ThermalLabels.SEVERE -> stringResource(R.string.thermal_severe)
    ThermalLabels.CRITICAL -> stringResource(R.string.thermal_critical)
    ThermalLabels.EMERGENCY -> stringResource(R.string.thermal_emergency)
    ThermalLabels.SHUTDOWN -> stringResource(R.string.thermal_shutdown)
    ThermalLabels.UNKNOWN -> stringResource(R.string.thermal_unknown)
    null -> null
    else -> label
}

/** Maps a stable plugged-power key to localized text; null means "not reported". */
@Composable
fun pluggedLabel(label: String?): String? = when (label) {
    PluggedLabels.AC -> stringResource(R.string.battery_plugged_ac)
    PluggedLabels.USB -> stringResource(R.string.battery_plugged_usb)
    PluggedLabels.WIRELESS -> stringResource(R.string.battery_plugged_wireless)
    null -> null
    else -> label
}

/** Maps a stable charging-state key to localized text (Phase 3 battery rows). */
@Composable
fun chargingStateLabel(key: String): String = when (key) {
    ChargingLabels.CHARGING -> stringResource(R.string.battery_charging)
    ChargingLabels.DISCHARGING -> stringResource(R.string.battery_discharging)
    ChargingLabels.NOT_REPORTED -> stringResource(R.string.battery_not_reported)
    else -> key
}

/**
 * Resolves the stable label key of a [com.nexus.gamebooster.monitoring.MetricRowUi]
 * (produced by the pure monitoring mappers) to a localized string. Unknown keys are
 * shown raw rather than invented.
 */
@Composable
fun metricRowLabel(labelKey: String): String = when (labelKey) {
    MetricRowKeys.BATTERY_LEVEL -> stringResource(R.string.battery_level)
    MetricRowKeys.BATTERY_CHARGING_STATE -> stringResource(R.string.battery_charging_state)
    MetricRowKeys.BATTERY_POWER_SOURCE -> stringResource(R.string.battery_power_source)
    MetricRowKeys.MEMORY_AVAILABLE -> stringResource(R.string.memory_available)
    MetricRowKeys.MEMORY_TOTAL -> stringResource(R.string.memory_total)
    MetricRowKeys.MEMORY_IN_USE_CALCULATED -> stringResource(R.string.memory_in_use_calculated)
    MetricRowKeys.MEMORY_THIS_APP -> stringResource(R.string.memory_this_app)
    MetricRowKeys.THERMAL_STATUS -> stringResource(R.string.status_thermal)
    MetricRowKeys.THERMAL_HEADROOM -> stringResource(R.string.thermal_headroom)
    MetricRowKeys.DEVICE_MODEL -> stringResource(R.string.device_model)
    MetricRowKeys.DEVICE_NAME -> stringResource(R.string.device_device_name)
    MetricRowKeys.DEVICE_ANDROID_VERSION -> stringResource(R.string.device_android_version)
    MetricRowKeys.DEVICE_SDK -> stringResource(R.string.device_sdk)
    MetricRowKeys.DEVICE_SECURITY_PATCH -> stringResource(R.string.device_security_patch)
    else -> labelKey
}

// ---------------------------------------------------------------------------
// Phase 5 — Safe Boost Engine key resolvers (labels/values/actions/details).
// ---------------------------------------------------------------------------

/** Localized label for a [BoostCheckKeys] pre-check key; unknown keys are shown raw. */
@Composable
fun boostCheckLabel(labelKey: String): String = when (labelKey) {
    BoostCheckKeys.BATTERY_LEVEL -> stringResource(R.string.battery_level)
    BoostCheckKeys.BATTERY_CHARGING -> stringResource(R.string.battery_charging_state)
    BoostCheckKeys.MEMORY_AVAILABLE -> stringResource(R.string.memory_available)
    BoostCheckKeys.THERMAL_STATUS -> stringResource(R.string.status_thermal)
    BoostCheckKeys.PROFILE -> stringResource(R.string.boost_check_profile)
    BoostCheckKeys.GAME -> stringResource(R.string.boost_check_game)
    else -> labelKey
}

/**
 * Resolves a [BoostCheck.valueText] to displayable text: machine keys (charging yes/no,
 * thermal labels) are localized, everything else (numbers, names) is shown as-is.
 */
@Composable
fun boostCheckValueText(labelKey: String, valueText: String): String = when (labelKey) {
    BoostCheckKeys.BATTERY_CHARGING -> when (valueText) {
        BoostCheckValues.CHARGING_YES -> stringResource(R.string.boost_check_charging_yes)
        BoostCheckValues.CHARGING_NO -> stringResource(R.string.boost_check_charging_no)
        else -> valueText
    }
    BoostCheckKeys.THERMAL_STATUS -> thermalStatusLabel(valueText) ?: valueText
    else -> valueText
}

/** Localized label for a [BoostActionIds] action id; unknown ids are shown raw. */
@Composable
fun boostActionLabel(actionId: String): String = when (actionId) {
    BoostActionIds.APPLY_PROFILE -> stringResource(R.string.boost_action_apply_profile)
    BoostActionIds.SESSION_MONITORING -> stringResource(R.string.boost_action_session_monitoring)
    BoostActionIds.PREPARE_GAME_LAUNCH -> stringResource(R.string.boost_action_prepare_game_launch)
    BoostActionIds.OPEN_BATTERY_SETTINGS -> stringResource(R.string.boost_action_open_battery_settings)
    BoostActionIds.OPEN_GAME_DETAILS -> stringResource(R.string.boost_action_open_game_details)
    BoostActionIds.LAUNCH_GAME -> stringResource(R.string.boost_action_launch_game)
    BoostActionIds.FORCE_STOP_BACKGROUND -> stringResource(R.string.boost_action_force_stop_background)
    BoostActionIds.PROTECT_GAME -> stringResource(R.string.boost_action_protect_game)
    BoostActionIds.FREE_MEMORY -> stringResource(R.string.boost_action_free_memory)
    BoostActionIds.COMPILE_GAME -> stringResource(R.string.boost_action_compile_game)
    BoostActionIds.SYSTEM_TWEAKS -> stringResource(R.string.boost_action_system_tweaks)
    else -> actionId
}

/**
 * Localized detail text for a [BoostDetailKeys] / Phase-9 detail key;
 * null/unknown → null (nothing extra shown — never an invented explanation).
 */
@Composable
fun boostDetailLabel(detailKey: String?): String? = when {
    detailKey == null -> null
    detailKey == BoostDetailKeys.READY_TO_LAUNCH ->
        stringResource(R.string.boost_detail_ready_to_launch)
    detailKey == BoostDetailKeysExtra.NONE_TO_STOP ->
        stringResource(R.string.boost_detail_none_to_stop)
    detailKey == BoostDetailKeysExtra.PARTIAL_PROTECT ->
        stringResource(R.string.boost_detail_partial_protect)
    // Phase 9 ForceStopBackgroundAction encodes the real stop count as "stopped_<n>".
    detailKey.startsWith("stopped_") ->
        detailKey.removePrefix("stopped_").toIntOrNull()?.let { count ->
            stringResource(R.string.boost_detail_stopped_apps, count)
        }
    else -> null
}

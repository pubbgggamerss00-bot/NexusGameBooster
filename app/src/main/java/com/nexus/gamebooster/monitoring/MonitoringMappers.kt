package com.nexus.gamebooster.monitoring

/**
 * Phase 3 display mappers — PURE Kotlin (no Android imports; `DeviceFacts` is a plain
 * data class). They turn monitoring snapshots into display ROWS made of stable machine
 * keys, exactly like `core/UiMessage` does for reason keys: the data layer never sees
 * Android resources and the UI layer resolves every key to localized text (EN + FA).
 *
 * These functions are deterministic and side-effect free, so they are unit-test-ready
 * without Android (see README "Suggested unit tests" — the standing project constraint
 * is to ship no test files, so the seams are documented instead).
 */

/**
 * Stable label keys for [MetricRowUi]. A closed set — the UI resolves each key to a
 * localized string (see `metricRowLabel` in core/UiMessage.kt).
 */
object MetricRowKeys {
    const val BATTERY_LEVEL = "battery_level"
    const val BATTERY_CHARGING_STATE = "battery_charging_state"
    const val BATTERY_POWER_SOURCE = "battery_power_source"
    const val MEMORY_AVAILABLE = "memory_available"
    const val MEMORY_TOTAL = "memory_total"
    const val MEMORY_IN_USE_CALCULATED = "memory_in_use_calculated"
    const val MEMORY_THIS_APP = "memory_this_app"
    const val THERMAL_STATUS = "thermal_status"
    const val THERMAL_HEADROOM = "thermal_headroom"
    const val DEVICE_MODEL = "device_model"
    const val DEVICE_NAME = "device_name"
    const val DEVICE_ANDROID_VERSION = "device_android_version"
    const val DEVICE_SDK = "device_sdk"
    const val DEVICE_SECURITY_PATCH = "device_security_patch"
}

/**
 * One display row produced by the mappers.
 *
 * Field semantics (kept simple and deterministic):
 *  - [labelKey] — stable [MetricRowKeys] key; the UI resolves it to a localized label.
 *  - [value] — EITHER an already-formatted display value (digits/units produced with
 *    locale-independent arithmetic, e.g. "87%", "2048 MB", or a raw Build string) OR a
 *    stable machine key whose semantics are fixed by [labelKey] (e.g. "charging" /
 *    "discharging" / "not_reported" for BATTERY_CHARGING_STATE, thermal label keys for
 *    THERMAL_STATUS). Machine keys are resolved to localized text by the UI; formatted
 *    values are shown as-is. The two namespaces never collide (keys are snake_case
 *    words, values are numbers/units/Build strings).
 *  - [availabilityKey] — when non-null the metric could NOT be read: carries a stable
 *    [UnavailableReasons] key and the UI renders a status chip instead of [value].
 *  - [derived] — true when the value is arithmetic over other REAL values
 *    (e.g. in-use memory = total − available). The UI labels such rows "(calculated)";
 *    they are never presented as measured.
 *  - [rawValue] — raw Float for values the UI must format with locale-aware
 *    formatting (currently only thermal headroom, formatted "%1$.2f" via a string
 *    resource). When set, [value] is unused.
 */
data class MetricRowUi(
    val labelKey: String,
    val value: String,
    val availabilityKey: String? = null,
    val derived: Boolean = false,
    val rawValue: Float? = null,
)

/** Battery rows: level %, charging state key, and plugged source key when present. */
fun batteryRows(snapshot: BatterySnapshot): List<MetricRowUi> {
    val level = snapshot.levelPercent
    if (level == null) {
        val reason = (snapshot.availability as? MetricAvailability.Unavailable)?.reason
            ?: UnavailableReasons.NO_READING
        return listOf(
            MetricRowUi(
                labelKey = MetricRowKeys.BATTERY_LEVEL,
                value = "",
                availabilityKey = reason,
            ),
        )
    }
    val rows = mutableListOf(
        MetricRowUi(labelKey = MetricRowKeys.BATTERY_LEVEL, value = "$level%"),
        MetricRowUi(
            labelKey = MetricRowKeys.BATTERY_CHARGING_STATE,
            value = when (snapshot.charging) {
                true -> ChargingLabels.CHARGING
                false -> ChargingLabels.DISCHARGING
                // Status extra missing from the broadcast — honest "not reported".
                null -> ChargingLabels.NOT_REPORTED
            },
        ),
    )
    snapshot.pluggedLabel?.let { plugged ->
        rows += MetricRowUi(labelKey = MetricRowKeys.BATTERY_POWER_SOURCE, value = plugged)
    }
    return rows
}

/**
 * Memory rows: Available and Total from the real MemoryInfo values, a DERIVED
 * "in use (calculated)" row (total − available — arithmetic on real values, never
 * presented as measured), and this app's own heap when reported. No usage percentage
 * is ever derived from availMem.
 */
fun memoryRows(snapshot: MemorySnapshot): List<MetricRowUi> {
    val total = snapshot.totalMb
    val available = snapshot.availableMb
    if (total == null || available == null) {
        val reason = (snapshot.availability as? MetricAvailability.Unavailable)?.reason
            ?: UnavailableReasons.NO_READING
        return listOf(
            MetricRowUi(
                labelKey = MetricRowKeys.MEMORY_AVAILABLE,
                value = "",
                availabilityKey = reason,
            ),
        )
    }
    val rows = mutableListOf(
        MetricRowUi(labelKey = MetricRowKeys.MEMORY_AVAILABLE, value = mb(available)),
        MetricRowUi(labelKey = MetricRowKeys.MEMORY_TOTAL, value = mb(total)),
        MetricRowUi(
            labelKey = MetricRowKeys.MEMORY_IN_USE_CALCULATED,
            value = mb(total - available),
            derived = true,
        ),
    )
    snapshot.appHeapUsedMb?.let { heapMb ->
        rows += MetricRowUi(labelKey = MetricRowKeys.MEMORY_THIS_APP, value = mb(heapMb))
    }
    return rows
}

/**
 * Thermal rows: the status label key (or its availability key), plus a headroom row
 * ONLY when the platform actually reported headroom — otherwise a headroom row with
 * the availability key, so the UI can distinguish REQUIRES_ANDROID_11 (permanent:
 * unsupported OS) from HEADROOM_NOT_REPORTED / API_ERROR (temporary). [headroom] may
 * be null (e.g. callers that did not request headroom) — then no headroom row at all.
 */
fun thermalRows(
    status: ThermalSnapshot,
    headroom: ThermalHeadroomSnapshot?,
): List<MetricRowUi> {
    val rows = mutableListOf<MetricRowUi>()
    val label = status.statusLabel
    if (label != null) {
        rows += MetricRowUi(labelKey = MetricRowKeys.THERMAL_STATUS, value = label)
    } else {
        val reason = (status.availability as? MetricAvailability.Unavailable)?.reason
            ?: UnavailableReasons.NO_READING
        rows += MetricRowUi(
            labelKey = MetricRowKeys.THERMAL_STATUS,
            value = "",
            availabilityKey = reason,
        )
    }
    if (headroom != null) {
        val seconds = headroom.headroomSeconds
        if (seconds != null) {
            rows += MetricRowUi(
                labelKey = MetricRowKeys.THERMAL_HEADROOM,
                value = "",
                rawValue = seconds, // UI formats with "%1$.2f" (locale-aware).
            )
        } else {
            val reason = (headroom.availability as? MetricAvailability.Unavailable)?.reason
                ?: UnavailableReasons.HEADROOM_NOT_REPORTED
            rows += MetricRowUi(
                labelKey = MetricRowKeys.THERMAL_HEADROOM,
                value = "",
                availabilityKey = reason,
            )
        }
    }
    return rows
}

/**
 * Device rows from real Build/Settings facts. Null facts produce NO row — a missing
 * value is never rendered as "unknown" text. Android version and SDK level are two
 * rows with raw values so the UI labels them in the active language.
 */
fun deviceRows(facts: DeviceFacts): List<MetricRowUi> {
    val rows = mutableListOf(
        MetricRowUi(
            labelKey = MetricRowKeys.DEVICE_MODEL,
            value = "${facts.manufacturer} ${facts.model}",
        ),
    )
    facts.deviceName?.let { name ->
        rows += MetricRowUi(labelKey = MetricRowKeys.DEVICE_NAME, value = name)
    }
    rows += MetricRowUi(labelKey = MetricRowKeys.DEVICE_ANDROID_VERSION, value = facts.androidVersion)
    rows += MetricRowUi(labelKey = MetricRowKeys.DEVICE_SDK, value = facts.sdkLevel.toString())
    facts.securityPatch?.let { patch ->
        rows += MetricRowUi(labelKey = MetricRowKeys.DEVICE_SECURITY_PATCH, value = patch)
    }
    return rows
}

/** Deterministic, locale-independent megabyte formatting used by the memory rows. */
private fun mb(value: Long): String = "$value MB"

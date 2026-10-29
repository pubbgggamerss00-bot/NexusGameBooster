package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.chargingStateLabel
import com.nexus.gamebooster.core.metricRowLabel
import com.nexus.gamebooster.core.pluggedLabel
import com.nexus.gamebooster.core.thermalStatusLabel
import com.nexus.gamebooster.core.unavailableReasonLabel
import com.nexus.gamebooster.monitoring.MetricRowKeys
import com.nexus.gamebooster.monitoring.MetricRowUi
import com.nexus.gamebooster.ui.theme.NgbOutline

/**
 * One labeled metric line: leading icon + label (+ optional supporting note) and a
 * trailing real value OR a status chip. Exactly one of [value] / ([statusVariant] +
 * [statusText]) is rendered — the UI never shows both or invents a value.
 *
 * v1.2.1 re-skin: rows wear the mockup `.kv` dashed hairline at the bottom
 * (`border-bottom:1px dashed var(--line)`) so metric lists read as ledger rows.
 */
@Composable
fun MetricRow(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    statusVariant: StatusChipVariant? = null,
    statusText: String? = null,
    supporting: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Mockup `.kv` dashed bottom hairline (var(--line) #1E3A2A).
                drawLine(
                    color = NgbOutline,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        } else if (statusVariant != null && statusText != null) {
            StatusChip(text = statusText, variant = statusVariant)
        }
    }
}

/**
 * Renders one [MetricRowUi] produced by the pure monitoring mappers
 * (`monitoring/MonitoringMappers.kt`). This is the SINGLE row-building path shared by
 * the Monitor and Home screens, so both always display the same data the same way:
 *
 *  - availabilityKey set → status chip; OS-version reasons use the muted UNSUPPORTED
 *    variant, transient failures the warning-style UNAVAILABLE variant.
 *  - rawValue set → locale-aware formatting (thermal headroom, "%1$.2f") plus the
 *    honest unit hint (the PowerManager headroom abstraction, not a temperature).
 *  - otherwise → the row value; rows whose value is itself a machine key (charging
 *    state, plugged source, thermal status) are resolved through the same core
 *    resolvers used everywhere else, so localized text is never duplicated.
 *  - derived rows carry an explicit "(calculated)" honesty note under the label.
 */
@Composable
fun MapperMetricRow(
    row: MetricRowUi,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val label = metricRowLabel(row.labelKey)
    val availabilityKey = row.availabilityKey
    val rawValue = row.rawValue
    when {
        availabilityKey != null -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            statusVariant = chipVariantForReason(availabilityKey),
            statusText = unavailableReasonLabel(availabilityKey),
        )

        rawValue != null -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = stringResource(R.string.thermal_headroom_value, rawValue),
            supporting = stringResource(R.string.thermal_headroom_unit_hint),
        )

        row.labelKey == MetricRowKeys.BATTERY_CHARGING_STATE -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = chargingStateLabel(row.value),
        )

        row.labelKey == MetricRowKeys.BATTERY_POWER_SOURCE -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = pluggedLabel(row.value) ?: row.value,
        )

        row.labelKey == MetricRowKeys.THERMAL_STATUS -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = thermalStatusLabel(row.value) ?: row.value,
        )

        row.derived -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = row.value,
            supporting = stringResource(R.string.derived_value_note),
        )

        else -> MetricRow(
            label = label,
            icon = icon,
            modifier = modifier,
            value = row.value,
        )
    }
}

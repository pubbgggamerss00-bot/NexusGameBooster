package com.nexus.gamebooster.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.boostActionLabel
import com.nexus.gamebooster.core.boostCheckLabel
import com.nexus.gamebooster.core.boostCheckValueText
import com.nexus.gamebooster.core.boostDetailLabel
import com.nexus.gamebooster.core.unavailableReasonLabel
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.components.StatusChip
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.chipVariantForReason
import java.text.DateFormat
import java.util.Date

/**
 * The honest boost result dialog (Phase 5). Renders ONLY what actually happened:
 *
 *  - status header + honest subtitle (no percentages, no before/after);
 *  - pre-check rows: real values (localized with units) or availability chips — an
 *    unavailable metric is never shown as zero;
 *  - action results: one typed row per [com.nexus.gamebooster.optimizer.BoostActionRecord]
 *    (Applied = neon OK, AlreadyApplied = muted OK, Skipped/Unsupported = neutral chip,
 *    Failed = error chip with its technical cause);
 *  - report meta (short id, real timestamps, profile title, game name);
 *  - buttons: Cancel while running; Retry for FAILED/CANCELLED; the explicit
 *    user-requested actions (Battery settings always — honest secondary; Game details +
 *    Launch only when a game verified ready); Close when finished.
 *
 * A HISTORICAL report ([viewingHistoryReport]) is read-only: no action buttons.
 */
@Composable
fun BoostResultDialog(
    state: BoostUiState,
    profiles: List<PerformanceProfile>,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenGameDetails: () -> Unit,
    onLaunchReadyGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isRunning = state.isRunning
    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = { Text(stringResource(R.string.boost_report_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.report?.let { report ->
                    ReportHeader(report = report, viewingHistory = state.viewingHistoryReport)
                }
                if (isRunning) {
                    RunningSection(phase = state.phase, checks = state.checks)
                } else {
                    state.report?.let { report ->
                        ChecksSection(report = report)
                        Spacer(Modifier.height(12.dp))
                        ActionsSection(report = report)
                        Spacer(Modifier.height(12.dp))
                        MetaSection(report = report, profiles = profiles)
                    }
                }
                // Honest secondary suggestion when the battery was low/unreadable.
                if (!isRunning && !state.viewingHistoryReport && state.canOpenBatterySettings) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.boost_battery_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isRunning) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                } else if (!state.viewingHistoryReport) {
                    if (state.status == BoostStatus.FAILED || state.status == BoostStatus.CANCELLED) {
                        // Secondary CTA — restrained outlined styling for the retry.
                        NgbOutlinedButton(onClick = onRetry) {
                            Text(stringResource(R.string.boost_retry))
                        }
                    }
                    TextButton(onClick = onOpenBatterySettings) {
                        Text(stringResource(R.string.boost_open_battery_settings))
                    }
                    if (state.readyToLaunchGame != null) {
                        TextButton(onClick = onOpenGameDetails) {
                            Text(stringResource(R.string.boost_open_game_details))
                        }
                        // The dialog's single primary CTA.
                        NgbButton(onClick = onLaunchReadyGame) {
                            Text(stringResource(R.string.boost_launch_game))
                        }
                    }
                }
                if (!isRunning) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ReportHeader(report: BoostReport, viewingHistory: Boolean) {
    val statusText = stringResource(BoostViewModel.statusLabelRes(report.status))
    val subtitle = when (report.status) {
        BoostStatus.COMPLETED -> R.string.boost_report_subtitle_completed
        BoostStatus.PARTIAL_SUCCESS -> R.string.boost_report_subtitle_partial
        BoostStatus.CANCELLED -> R.string.boost_report_subtitle_cancelled
        BoostStatus.FAILED -> R.string.boost_report_subtitle_failed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatusChip(
            text = statusText,
            variant = when (report.status) {
                BoostStatus.COMPLETED -> StatusChipVariant.OK
                BoostStatus.PARTIAL_SUCCESS -> StatusChipVariant.OK_MUTED
                BoostStatus.CANCELLED -> StatusChipVariant.OFFLINE
                BoostStatus.FAILED -> StatusChipVariant.UNAVAILABLE
            },
        )
        if (viewingHistory) {
            Spacer(Modifier.width(8.dp))
            StatusChip(
                text = stringResource(R.string.boost_history_view_chip),
                variant = StatusChipVariant.UNSUPPORTED,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Live section while the run is active: real captured pre-checks + honest progress note. */
@Composable
private fun RunningSection(phase: BoostPhase, checks: List<BoostCheck>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (phase == BoostPhase.CHECKING) R.string.boost_checking else R.string.boost_running
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (checks.isNotEmpty()) {
        Text(
            text = stringResource(R.string.boost_prechecks_title),
            style = MaterialTheme.typography.titleSmall,
        )
        checks.forEach { check -> CheckRow(check = check) }
    }
}

/** Pre-check rows: real value (localized) or an availability chip — never a zero. */
@Composable
private fun ChecksSection(report: BoostReport) {
    Text(
        text = stringResource(R.string.boost_prechecks_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    report.checks.forEach { check -> CheckRow(check = check) }
}

@Composable
private fun CheckRow(check: BoostCheck) {
    val label = boostCheckLabel(check.labelKey)
    val reason = check.availabilityReason
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (reason != null || check.valueText == null) {
            StatusChip(
                text = unavailableReasonLabel(reason ?: UnavailableReasons.NO_READING),
                variant = chipVariantForReason(reason ?: UnavailableReasons.NO_READING),
            )
        } else {
            CheckValueText(check = check)
        }
    }
}

/**
 * Per-check value formatting: the battery level gets the localized "%1$d%%" pattern,
 * available memory the localized MB unit, everything else resolves through
 * [boostCheckValueText] (machine keys localized, names/numbers as-is).
 */
@Composable
private fun CheckValueText(check: BoostCheck) {
    val value = when (check.labelKey) {
        BoostCheckKeys.BATTERY_LEVEL ->
            check.valueText?.toIntOrNull()?.let { stringResource(R.string.battery_level_value, it) }
                ?: check.valueText

        BoostCheckKeys.MEMORY_AVAILABLE ->
            check.valueText?.let { raw -> stringResource(R.string.boost_check_memory_value, raw) }

        BoostCheckKeys.PROFILE -> check.valueText // raw id; profile title shown in meta row
        else -> check.valueText?.let { boostCheckValueText(check.labelKey, it) }
    }
    Text(
        text = value ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )
}

/** One typed row per recorded action outcome — the core honesty surface. */
@Composable
private fun ActionsSection(report: BoostReport) {
    Text(
        text = stringResource(R.string.boost_actions_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    if (report.actions.isEmpty()) {
        Text(
            text = stringResource(R.string.boost_actions_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    report.actions.forEach { record ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = boostActionLabel(record.actionId),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            OutcomeChip(outcome = record.outcome)
        }
        OutcomeDetail(outcome = record.outcome)
    }
}

@Composable
private fun OutcomeChip(outcome: BoostActionOutcome) {
    when (outcome) {
        is BoostActionOutcome.Applied -> StatusChip(
            text = stringResource(R.string.chip_ok),
            variant = StatusChipVariant.OK,
        )

        is BoostActionOutcome.AlreadyApplied -> StatusChip(
            text = stringResource(R.string.chip_ok),
            variant = StatusChipVariant.OK_MUTED,
        )

        is BoostActionOutcome.Skipped -> StatusChip(
            text = unavailableReasonLabel(outcome.reasonKey),
            variant = StatusChipVariant.UNSUPPORTED,
        )

        is BoostActionOutcome.Unsupported -> StatusChip(
            text = unavailableReasonLabel(outcome.reasonKey),
            variant = StatusChipVariant.UNSUPPORTED,
        )

        is BoostActionOutcome.Failed -> StatusChip(
            text = unavailableReasonLabel(outcome.reasonKey),
            variant = StatusChipVariant.UNAVAILABLE,
        )
    }
}

/** Supporting line for outcomes that carry more information than the chip can show. */
@Composable
private fun OutcomeDetail(outcome: BoostActionOutcome) {
    val detail: String? = when (outcome) {
        is BoostActionOutcome.Applied -> boostDetailLabel(outcome.detailKey)
        is BoostActionOutcome.AlreadyApplied -> boostDetailLabel(outcome.detailKey)
        is BoostActionOutcome.Failed -> outcome.message
        else -> null
    }
    if (!detail.isNullOrBlank()) {
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

/** Report meta: short id, REAL start/completion times, profile title, game name. */
@Composable
private fun MetaSection(report: BoostReport, profiles: List<PerformanceProfile>) {
    val startedAt = rememberTime(report.startedAtMillis)
    val completedAt = rememberTime(report.completedAtMillis)
    val profileTitle = profiles
        .firstOrNull { it.id == report.profileId }
        ?.let { stringResource(it.titleRes) }
    Text(
        text = stringResource(R.string.boost_report_meta),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    MetaRow(label = stringResource(R.string.boost_report_id), value = report.id.take(8))
    MetaRow(label = stringResource(R.string.boost_report_started), value = startedAt)
    MetaRow(label = stringResource(R.string.boost_report_completed), value = completedAt)
    profileTitle?.let { MetaRow(label = stringResource(R.string.boost_check_profile), value = it) }
    report.gameName?.let { MetaRow(label = stringResource(R.string.boost_check_game), value = it) }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.boost_report_honesty_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun rememberTime(millis: Long): String =
    remember(millis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }

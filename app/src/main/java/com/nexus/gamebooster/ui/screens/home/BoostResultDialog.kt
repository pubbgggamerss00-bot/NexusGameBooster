package com.nexus.gamebooster.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.boostActionLabel
import com.nexus.gamebooster.core.boostCheckLabel
import com.nexus.gamebooster.core.boostCheckValueText
import com.nexus.gamebooster.core.boostDetailLabel
import com.nexus.gamebooster.core.unavailableReasonLabel
import com.nexus.gamebooster.games.GameTuningAdvisor
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.ui.arcade.ArcadeBurst
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeKvRow
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeSectionLabel
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.StatusChip
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.chipVariantForReason
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbHudFont
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The honest boost result, restyled as the reference's ARCADE HUD bottom sheet
 * (`.dim` + `.panel.hot.sheet`): a 70%-dimmed overlay with the report in a HOT
 * chamfered panel sliding up from the bottom edge. NOTHING about the content
 * contract changed — this renders ONLY what actually happened:
 *
 *  - status header + honest subtitle (no percentages, no before/after);
 *  - the score ring animates ONLY to a REAL report stat: recorded actions that
 *    applied (or were honestly already applied) out of the actions actually run —
 *    the app has NO freed-memory number, so none is ever invented (the ring is
 *    omitted entirely when no action was recorded);
 *  - pre-check rows: real values (localized with units, HUD font) or availability
 *    chips — an unavailable metric is never shown as zero;
 *  - action results: one typed row per [com.nexus.gamebooster.optimizer.BoostActionRecord];
 *  - report meta (short id, real timestamps, profile title, game name);
 *  - buttons: Cancel while running; Retry for FAILED/CANCELLED; the explicit
 *    user-requested actions (Battery settings always — honest secondary; Game details +
 *    Launch only when a game verified ready); Close when finished.
 *  - one [ArcadeBurst] celebration when a COMPLETED (non-history) report appears.
 *
 * A HISTORICAL report ([BoostUiState.viewingHistoryReport]) stays read-only: no
 * action buttons. Entrance fade/slide and the burst are motion-gated.
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
    val motion = LocalNgbMotionEnabled.current

    // Celebration burst — fires once per fresh COMPLETED report (never for history).
    val burstKey = remember { mutableIntStateOf(0) }
    LaunchedEffect(state.report?.id, state.status) {
        if (!state.viewingHistoryReport && state.status == BoostStatus.COMPLETED) {
            burstKey.intValue++
        }
    }

    // Entrance: dim fades in, sheet slides up (both static when motion is off).
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    val slide = remember { Animatable(if (motion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (motion) {
            launch { appear.animateTo(1f, tween(250)) }
            launch {
                slide.animateTo(0f, tween(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)))
            }
        }
    }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    Dialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value },
        ) {
            // The dim (`.dim`): 70% ink wash over the screen; tapping outside = dismiss.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NgbArcadeColors.Ink.copy(alpha = 0.7f)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeightPx = it.height }
                    .graphicsLayer {
                        translationY = slide.value * (if (sheetHeightPx > 0) sheetHeightPx.toFloat() else 0f)
                    }
                    .navigationBarsPadding()
                    .heightIn(max = maxSheetHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                ArcadePanel(
                    modifier = Modifier.fillMaxWidth(),
                    variant = ArcadePanelVariant.HOT,
                    cut = 22.dp,
                    contentPadding = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
                    ) {
                        state.report?.let { report ->
                            ReportHeader(report = report, viewingHistory = state.viewingHistoryReport)
                        }
                        if (isRunning) {
                            RunningSection(phase = state.phase, checks = state.checks)
                        } else {
                            state.report?.let { report ->
                                Spacer(Modifier.height(10.dp))
                                ScoreRing(
                                    report = report,
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                )
                                Spacer(Modifier.height(10.dp))
                                ChecksSection(report = report)
                                Spacer(Modifier.height(12.dp))
                                ActionsSection(report = report)
                                Spacer(Modifier.height(12.dp))
                                MetaSection(report = report, profiles = profiles)
                                GameAdviceSection(gameName = report.gameName)
                            }
                        }
                        // Honest secondary suggestion when the battery was low/unreadable.
                        if (!isRunning && !state.viewingHistoryReport && state.canOpenBatterySettings) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.boost_battery_hint),
                                fontSize = 11.sp,
                                color = NgbArcadeColors.Muted,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        SheetButtons(
                            state = state,
                            isRunning = isRunning,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onOpenGameDetails = onOpenGameDetails,
                            onLaunchReadyGame = onLaunchReadyGame,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
            // The celebration burst (drawn over the dim; purely decorative, non-touch).
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ArcadeBurst(burstKey = burstKey.intValue, modifier = Modifier.size(320.dp))
            }
        }
    }
}

/** Sheet header: honest status chip (+ history chip), title and muted subtitle. */
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
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.boost_report_title),
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(subtitle),
        fontSize = 12.sp,
        color = NgbArcadeColors.Muted,
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
            color = NgbArcadeColors.Neon,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (phase == BoostPhase.CHECKING) R.string.boost_checking else R.string.boost_running,
            ),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    if (checks.isNotEmpty()) {
        ArcadeSectionLabel(text = stringResource(R.string.boost_prechecks_title))
        Spacer(Modifier.height(4.dp))
        checks.forEach { check -> CheckRow(check = check) }
    }
}

/**
 * The score ring (reference `.score`): a 128 dp dashed-track ring whose arc animates
 * ONLY to the REAL share of recorded actions that applied (or were honestly already
 * applied) — the app carries no freed-memory number, so nothing is invented and no
 * percentage of "performance" is claimed. Omitted when no action was recorded.
 */
@Composable
private fun ScoreRing(report: BoostReport, modifier: Modifier = Modifier) {
    val total = report.actions.size
    if (total == 0) return
    val applied = report.actions.count {
        it.outcome is BoostActionOutcome.Applied || it.outcome is BoostActionOutcome.AlreadyApplied
    }
    val motion = LocalNgbMotionEnabled.current
    val ring = remember { Animatable(0f) }
    LaunchedEffect(applied, total) {
        ring.snapTo(0f)
        ring.animateTo(
            applied.toFloat() / total.toFloat(),
            if (motion) {
                tween(1400, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
            } else {
                tween(0)
            },
        )
    }
    Box(
        modifier = modifier
            .size(128.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 128f
            val stroke = 9f * u
            val r = 54f * u
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = NgbArcadeColors.Line,
                radius = r,
                center = c,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 4f * u)),
                ),
            )
            val sweep = 360f * ring.value
            for (i in 2 downTo 1) {
                drawArc(
                    color = NgbArcadeColors.Neon.copy(alpha = 0.10f * i),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = stroke + i * 2f * u),
                )
            }
            drawArc(
                color = NgbArcadeColors.Neon,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$applied/$total",
                fontFamily = NgbHudFont,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = NgbArcadeColors.Neon,
                style = TextStyle(
                    shadow = Shadow(
                        color = NgbArcadeColors.Neon.copy(alpha = 0.7f),
                        offset = Offset.Zero,
                        blurRadius = 18f,
                    ),
                ),
            )
            Text(
                text = stringResource(R.string.boost_actions_title),
                fontSize = 11.sp,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}

/** Pre-check rows: real value (localized, HUD font) or an availability chip — never a zero. */
@Composable
private fun ChecksSection(report: BoostReport) {
    ArcadeSectionLabel(text = stringResource(R.string.boost_prechecks_title))
    Spacer(Modifier.height(4.dp))
    report.checks.forEach { check -> CheckRow(check = check) }
}

@Composable
private fun CheckRow(check: BoostCheck) {
    val label = boostCheckLabel(check.labelKey)
    val reason = check.availabilityReason
    if (reason != null || check.valueText == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = NgbArcadeColors.Muted,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = unavailableReasonLabel(reason ?: UnavailableReasons.NO_READING),
                variant = chipVariantForReason(reason ?: UnavailableReasons.NO_READING),
            )
        }
    } else {
        ArcadeKvRow(label = label, value = checkValueText(check = check))
    }
}

/**
 * Per-check value formatting: the battery level gets the localized "%1$d%%" pattern,
 * available memory the localized MB unit, everything else resolves through
 * [boostCheckValueText] (machine keys localized, names/numbers as-is).
 */
@Composable
private fun checkValueText(check: BoostCheck): String = when (check.labelKey) {
    BoostCheckKeys.BATTERY_LEVEL ->
        check.valueText?.toIntOrNull()?.let { stringResource(R.string.battery_level_value, it) }
            ?: check.valueText.orEmpty()

    BoostCheckKeys.MEMORY_AVAILABLE ->
        check.valueText?.let { raw -> stringResource(R.string.boost_check_memory_value, raw) }
            .orEmpty()

    BoostCheckKeys.PROFILE -> check.valueText.orEmpty() // raw id; profile title shown in meta
    else -> check.valueText?.let { boostCheckValueText(check.labelKey, it) }.orEmpty()
}

/** One typed row per recorded action outcome — the core honesty surface. */
@Composable
private fun ActionsSection(report: BoostReport) {
    ArcadeSectionLabel(text = stringResource(R.string.boost_actions_title))
    Spacer(Modifier.height(4.dp))
    if (report.actions.isEmpty()) {
        Text(
            text = stringResource(R.string.boost_actions_empty),
            fontSize = 12.5.sp,
            color = NgbArcadeColors.Muted,
        )
        return
    }
    report.actions.forEach { record ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = boostActionLabel(record.actionId),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
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
            fontSize = 11.sp,
            color = NgbArcadeColors.Muted,
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
    ArcadeSectionLabel(text = stringResource(R.string.boost_report_meta))
    Spacer(Modifier.height(4.dp))
    ArcadeKvRow(label = stringResource(R.string.boost_report_id), value = report.id.take(8))
    ArcadeKvRow(label = stringResource(R.string.boost_report_started), value = startedAt)
    ArcadeKvRow(label = stringResource(R.string.boost_report_completed), value = completedAt)
    profileTitle?.let { ArcadeKvRow(label = stringResource(R.string.boost_check_profile), value = it) }
    report.gameName?.let { ArcadeKvRow(label = stringResource(R.string.boost_check_game), value = it) }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.boost_report_honesty_note),
        fontSize = 11.sp,
        color = NgbArcadeColors.Muted,
    )
}

@Composable
private fun rememberTime(millis: Long): String =
    remember(millis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }

/**
 * Sheet buttons — the SAME conditions as the previous dialog, restyled as kit
 * buttons: Cancel (danger outline) while running; Retry (azure outline) only for
 * FAILED/CANCELLED; Battery settings (azure outline, honest secondary); Game
 * details (azure outline) + Launch (the single filled primary) when a game verified
 * ready; Close (neon outline) when finished.
 */
@Composable
private fun SheetButtons(
    state: BoostUiState,
    isRunning: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenGameDetails: () -> Unit,
    onLaunchReadyGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isRunning) {
            ArcadeOutlineButton(
                onClick = onCancel,
                tint = ArcadeTint.DANGER,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        } else {
            if (!state.viewingHistoryReport) {
                if (state.status == BoostStatus.FAILED || state.status == BoostStatus.CANCELLED) {
                    ArcadeOutlineButton(
                        onClick = onRetry,
                        tint = ArcadeTint.AZURE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.boost_retry),
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                ArcadeOutlineButton(
                    onClick = onOpenBatterySettings,
                    tint = ArcadeTint.AZURE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.boost_open_battery_settings),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (state.readyToLaunchGame != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ArcadeOutlineButton(
                            onClick = onOpenGameDetails,
                            tint = ArcadeTint.AZURE,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.boost_open_game_details),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        ArcadeButton(
                            onClick = onLaunchReadyGame,
                            tint = ArcadeTint.NEON,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.boost_launch_game),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
            ArcadeOutlineButton(
                onClick = onDismiss,
                tint = ArcadeTint.NEON,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** Title-specific, manual in-game tuning tips; never represented as applied by the app. */
@Composable
private fun GameAdviceSection(gameName: String?) {
    val title = GameTuningAdvisor.identify(gameName) ?: return
    val heading = when (title) {
        GameTuningAdvisor.Title.PUBG_MOBILE -> R.string.game_advice_pubg_title
        GameTuningAdvisor.Title.CALL_OF_DUTY_MOBILE -> R.string.game_advice_cod_title
    }
    val tips = when (title) {
        GameTuningAdvisor.Title.PUBG_MOBILE -> listOf(
            R.string.game_advice_pubg_tip_1,
            R.string.game_advice_pubg_tip_2,
            R.string.game_advice_common_tip_1,
            R.string.game_advice_common_tip_2,
        )
        GameTuningAdvisor.Title.CALL_OF_DUTY_MOBILE -> listOf(
            R.string.game_advice_cod_tip_1,
            R.string.game_advice_cod_tip_2,
            R.string.game_advice_common_tip_1,
            R.string.game_advice_common_tip_2,
        )
    }
    Spacer(Modifier.height(12.dp))
    ArcadeSectionLabel(text = stringResource(R.string.game_advice_section_title))
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(heading),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = NgbArcadeColors.Neon,
    )
    tips.forEach { tip ->
        Text(
            text = "• " + stringResource(tip),
            fontSize = 12.5.sp,
            color = NgbArcadeColors.Muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Text(
        text = stringResource(R.string.game_advice_disclaimer),
        fontSize = 11.sp,
        color = NgbArcadeColors.Muted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

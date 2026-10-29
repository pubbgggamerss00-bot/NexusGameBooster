package com.nexus.gamebooster.ui.screens.profiles

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.profiles.MonitoringPolicy
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.profiles.ProfileEffect
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadeHead
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadePips
import com.nexus.gamebooster.ui.arcade.ArcadeSectionLabel
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.EmptyState
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.profileIcon
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.neonGlow

/**
 * Profiles screen — v1.2.1 "ARCADE HUD" restyle (`.prof` cards of the redesign
 * reference, `renderProfiles`/`mountProfiles`): each profile is a full-width
 * clickable [ArcadePanel] — PLAIN and slightly dimmed when unselected, HOT with a
 * 1.02 spring scale, a soft neon glow and an "Active" [ArcadeChip] when selected —
 * with the reference's 46 dp hexagon icon tile (`.ico`) and three skewed pip
 * stat bars (`.pips`).
 *
 * HONESTY CONTRACT (unchanged): the three pip bars are NOT the reference's
 * fictional game stats (monitoring speed / battery endurance / staying cool);
 * battery endurance and thermal behavior have NO data source here and are never
 * rendered. The bars visualize ONLY the real per-profile [MonitoringPolicy]
 * values — auto-refresh cadence, live battery stream, keep-screen-on — derived
 * in [refreshPips]/[onOffPips]; a value with no real source would be shown as a
 * plain chip instead. Selection still persists a preference and changes only
 * this app's own monitoring behavior; nothing on the device is modified and no
 * recommendation is ever labeled as applied.
 *
 * Every card keeps the Phase 4 honesty taxonomy (applied / recommended /
 * not-supported effect rows) and the collapsible "Monitoring policy" section
 * (raw policy values, read-only — the screen wires no toggles, so no switch is
 * rendered). The unsupported-explanation panel, the footnote and the custom
 * profiles section carry the same content as before, restyled into panels.
 *
 * Selection feedback is never color alone: HOT edge + glow + scale + the
 * "Active" text chip, and `selectable` exposes the radio state to TalkBack.
 * All motion (scale, alpha, glow fade, pips fill, chevron, size change) is
 * gated by [LocalNgbMotionEnabled], read once per screen.
 */
@Composable
fun ProfilesScreen() {
    val vm: ProfilesViewModel = viewModel(factory = VmFactory { ServiceLocator.profilesViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val motionEnabled = LocalNgbMotionEnabled.current

    NgbScreenContainer {
        ArcadeHead(
            code = "PROFILES",
            title = stringResource(R.string.profiles_headline),
            subtitle = stringResource(R.string.profiles_subtitle),
        )
        Spacer(Modifier.height(16.dp))

        if (state.saveError) {
            SaveErrorPanel(
                onRetry = {
                    vm.selectProfile(state.activeProfileId ?: state.profiles.first().id)
                },
                onDismiss = vm::dismissSaveError,
            )
            Spacer(Modifier.height(16.dp))
        }

        state.profiles.forEach { profile ->
            ArcadeProfileCard(
                profile = profile,
                selected = state.activeProfileId == profile.id,
                onClick = { vm.selectProfile(profile.id) },
                motionEnabled = motionEnabled,
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        UnsupportedPanel()
        Spacer(Modifier.height(12.dp))
        FootnotePanel()
        Spacer(Modifier.height(12.dp))
        CustomProfilesPanel()
    }
}

/**
 * One selectable preset card, restyled as the reference's `.prof` button: the
 * whole card is the touch target (radiogroup row semantics via `selectable`) and
 * always well above the 48 dp minimum; it carries the shared reduced-motion-aware
 * press scale. Unselected = PLAIN panel with 0.85 content alpha (the reference's
 * `saturate(.55) brightness(.85)`, implemented WITHOUT saturation filters so
 * contrast never breaks); selected = HOT panel, 1.02 spring scale, a soft neon
 * glow behind the panel (the reference's 16 px `drop-shadow`) and the "Active"
 * chip at the title row end.
 *
 * The per-profile monitoring-policy rows live in a collapsible section at the
 * bottom (see [ArcadePolicySection]); it defaults to expanded for the ACTIVE
 * profile and collapsed otherwise.
 */
@Composable
private fun ArcadeProfileCard(
    profile: PerformanceProfile,
    selected: Boolean,
    onClick: () -> Unit,
    motionEnabled: Boolean,
) {
    val accent = profileAccent(profile.id)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.65f, stiffness = 360f)
        } else {
            snap()
        },
        label = "profileSelectionScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = if (motionEnabled) tween(durationMillis = 200) else snap(),
        label = "profileContentAlpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 0.45f else 0f,
        animationSpec = if (motionEnabled) tween(durationMillis = 300) else snap(),
        label = "profileGlowAlpha",
    )

    // Collapsible policy details: default expanded ONLY for the active profile.
    // Keyed on (id, selected) so a selection change re-applies that default while
    // manual expand/collapse within a stable selection is preserved.
    var detailsExpanded by rememberSaveable(profile.id, selected) { mutableStateOf(selected) }

    val interactionSource = remember { MutableInteractionSource() }
    val glowModifier: Modifier = if (glowAlpha > 0.01f) {
        Modifier.neonGlow(
            glowColor = NgbArcadeColors.Neon,
            shape = RoundedCornerShape(20.dp),
            blurRadius = 16.dp,
            glowAlpha = glowAlpha,
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(glowModifier)
            .clip(NgbChamferShape(14.dp))
            .ngbPressScale(interactionSource, motionEnabled)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        ArcadePanel(
            variant = if (selected) ArcadePanelVariant.HOT else ArcadePanelVariant.PLAIN,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    HexIconTile(icon = profileIcon(profile.id), accent = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(profile.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                ArcadeChip(
                                    text = stringResource(R.string.profiles_active_chip),
                                    tint = accent,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(profile.descriptionRes),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = NgbArcadeColors.Muted,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ArcadeStatRow(
                    label = refreshStatLabel(profile.monitoringPolicy),
                    filled = refreshPips(profile.monitoringPolicy),
                    tint = accent,
                    animate = selected,
                )
                Spacer(Modifier.height(6.dp))
                ArcadeStatRow(
                    label = stringResource(
                        if (profile.monitoringPolicy.liveBatteryEvents) {
                            R.string.profiles_policy_live_on
                        } else {
                            R.string.profiles_policy_live_off
                        },
                    ),
                    filled = onOffPips(profile.monitoringPolicy.liveBatteryEvents),
                    tint = accent,
                    animate = selected,
                )
                Spacer(Modifier.height(6.dp))
                ArcadeStatRow(
                    label = stringResource(
                        if (profile.monitoringPolicy.keepScreenOnWhileVisible) {
                            R.string.monitor_keep_screen_on_chip
                        } else {
                            R.string.profiles_policy_keep_screen_on_off
                        },
                    ),
                    filled = onOffPips(profile.monitoringPolicy.keepScreenOnWhileVisible),
                    tint = accent,
                    animate = selected,
                )
                ArcadeEffectGroup(
                    label = stringResource(R.string.effect_applied_title),
                    effects = profile.appliedEffects,
                    accent = accent,
                    muted = false,
                )
                ArcadeEffectGroup(
                    label = stringResource(R.string.effect_recommended_title),
                    effects = profile.recommendations,
                    accent = accent,
                    muted = true,
                )
                ArcadeEffectGroup(
                    label = stringResource(R.string.effect_not_supported_title),
                    effects = profile.notSupportedEffects,
                    accent = accent,
                    muted = true,
                )
                Spacer(Modifier.height(4.dp))
                ArcadePolicySection(
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                    profile = profile,
                    motionEnabled = motionEnabled,
                )
            }
        }
    }
}

/**
 * The reference's 46 dp hexagon icon tile (`.ico`, clip-path polygon at
 * 50/93/93/50/7/7/25/75/75/25 percent) filled with `surfaceContainerHigh`; the
 * profile icon is tinted with the profile's accent color. The hexagon is
 * horizontally symmetric, so it needs no RTL mirroring.
 */
@Composable
private fun HexIconTile(icon: ImageVector, accent: Color) {
    val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .size(46.dp)
            .drawBehind {
                val path = Path().apply {
                    moveTo(size.width * 0.50f, 0f)
                    lineTo(size.width * 0.93f, size.height * 0.25f)
                    lineTo(size.width * 0.93f, size.height * 0.75f)
                    lineTo(size.width * 0.50f, size.height)
                    lineTo(size.width * 0.07f, size.height * 0.75f)
                    lineTo(size.width * 0.07f, size.height * 0.25f)
                    close()
                }
                drawPath(path, tileColor)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** One reference stat row (`.stat`): muted label on the start side, pips on the end side. */
@Composable
private fun ArcadeStatRow(
    label: String,
    filled: Int,
    tint: Color,
    animate: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = NgbArcadeColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ArcadePips(
            filled = filled,
            tint = tint,
            animate = animate,
            modifier = Modifier.width(110.dp),
        )
    }
}

/** Per-profile accent color (the reference `--c`): performance = neon, battery = lime, balanced = azure. */
private fun profileAccent(profileId: String): Color = when (profileId) {
    ProfileIds.PERFORMANCE -> NgbArcadeColors.Neon
    ProfileIds.BATTERY_SAVER -> NgbArcadeColors.Lime
    else -> NgbArcadeColors.Azure
}

/**
 * Honest stat label for the refresh-cadence bar, from the REAL policy: the same
 * wording the policy section has always used. No number is ever invented.
 */
@Composable
private fun refreshStatLabel(policy: MonitoringPolicy): String {
    val interval = policy.autoRefreshIntervalMillis
    return if (policy.inAppMonitoringEnabled && interval != null) {
        stringResource(R.string.monitor_auto_refresh_every, (interval / 1000L).toInt())
    } else {
        stringResource(R.string.monitor_auto_refresh_off)
    }
}

/**
 * Pip value for the refresh-cadence bar, DERIVED (not fabricated) from the real
 * interval: 5 s — the fastest shipped cadence — fills 10, 15 s fills 5, off
 * fills 0. A value slower than any shipped cadence (impossible today) would map
 * to the slowest non-zero tier, so the bar stays monotonic in the interval.
 */
private fun refreshPips(policy: MonitoringPolicy): Int {
    val interval = policy.autoRefreshIntervalMillis
    return when {
        interval == null -> 0
        interval <= 5_000L -> 10
        interval <= 15_000L -> 5
        else -> 2
    }
}

/** Binary policies (live battery stream, keep-screen-on) map on = full bar, off = empty bar. */
private fun onOffPips(on: Boolean): Int = if (on) 10 else 0

/** One labeled group of effect rows; muted styling for recommendations/unsupported. */
@Composable
private fun ArcadeEffectGroup(
    label: String,
    effects: List<ProfileEffect>,
    accent: Color,
    muted: Boolean,
) {
    Column(Modifier.padding(top = 10.dp)) {
        ArcadeSectionLabel(label)
        effects.forEach { effect ->
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = if (muted) NgbArcadeColors.Muted else accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(effect.textRes),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (muted) NgbArcadeColors.Muted else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Collapsible "Monitoring policy" section of one profile card: a 48 dp header row
 * (title + animated chevron) above a FLAT [ArcadePanel] holding the raw policy
 * rows (in-app monitoring, refresh interval, live battery events, screen stays
 * on). The rows are read-only model values — the screen wires no toggles, so no
 * switch is rendered here. The container grows/shrinks with [animateContentSize];
 * under reduced motion both the size change and the chevron rotation are instant
 * (snap) — the state itself is always announced to screen readers via
 * [stateDescription] plus an explicit show/hide click label.
 */
@Composable
private fun ArcadePolicySection(
    expanded: Boolean,
    onToggle: () -> Unit,
    profile: PerformanceProfile,
    motionEnabled: Boolean,
) {
    val expandedLabel = stringResource(R.string.a11y_expanded)
    val collapsedLabel = stringResource(R.string.a11y_collapsed)
    val showLabel = stringResource(R.string.profiles_policy_show)
    val hideLabel = stringResource(R.string.profiles_policy_hide)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionEnabled) tween(durationMillis = 200) else snap(),
        label = "policyChevronRotation",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (motionEnabled) tween(durationMillis = 200) else snap(),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .ngbPressScale(interactionSource, motionEnabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClickLabel = if (expanded) hideLabel else showLabel,
                    onClick = onToggle,
                )
                .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel },
        ) {
            Text(
                text = stringResource(R.string.profiles_policy_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NgbArcadeColors.Muted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = NgbArcadeColors.Muted,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        if (expanded) {
            ArcadePanel(
                variant = ArcadePanelVariant.FLAT,
                cut = 10.dp,
                contentPadding = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val policy = profile.monitoringPolicy
                    PolicyRow(
                        text = stringResource(
                            if (policy.inAppMonitoringEnabled) {
                                R.string.profiles_policy_in_app_on
                            } else {
                                R.string.profiles_policy_in_app_off
                            },
                        ),
                    )
                    PolicyRow(text = refreshStatLabel(policy))
                    PolicyRow(
                        text = stringResource(
                            if (policy.liveBatteryEvents) {
                                R.string.profiles_policy_live_on
                            } else {
                                R.string.profiles_policy_live_off
                            },
                        ),
                    )
                    PolicyRow(
                        text = stringResource(
                            if (policy.keepScreenOnWhileVisible) {
                                R.string.monitor_keep_screen_on_chip
                            } else {
                                R.string.profiles_policy_keep_screen_on_off
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** One honest policy value row — plain muted text, matching the effect-row style. */
@Composable
private fun PolicyRow(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "•",
            fontSize = 12.sp,
            color = NgbArcadeColors.Muted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = NgbArcadeColors.Muted,
        )
    }
}

/** Inline save-error panel: the BAD-variant arcade panel with Retry / Close actions. */
@Composable
private fun SaveErrorPanel(onRetry: () -> Unit, onDismiss: () -> Unit) {
    ArcadePanel(variant = ArcadePanelVariant.BAD, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.profile_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcadeButton(onClick = onRetry, small = true) {
                    Text(
                        text = stringResource(R.string.action_retry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ArcadeOutlineButton(onClick = onDismiss, tint = ArcadeTint.DANGER) {
                    Text(
                        text = stringResource(R.string.action_close),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** The "why no system-wide optimization" honesty card, restyled as a WARN panel. */
@Composable
private fun UnsupportedPanel() {
    ArcadePanel(variant = ArcadePanelVariant.WARN, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            ArcadeSectionLabel(stringResource(R.string.profiles_unsupported_title))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.profiles_unsupported_body),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The asterisk footnote about recommendations / not-supported rows, in a FLAT panel. */
@Composable
private fun FootnotePanel() {
    ArcadePanel(
        variant = ArcadePanelVariant.FLAT,
        contentPadding = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.profiles_footnote),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = NgbArcadeColors.Muted,
        )
    }
}

/** The custom-profiles placeholder, restyled into a FLAT panel around [EmptyState]. */
@Composable
private fun CustomProfilesPanel() {
    ArcadePanel(
        variant = ArcadePanelVariant.FLAT,
        contentPadding = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EmptyState(
            icon = Icons.Outlined.Build,
            title = stringResource(R.string.custom_profiles_title),
            body = stringResource(R.string.custom_profiles_empty),
        )
    }
}

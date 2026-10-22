package com.nexus.gamebooster.ui.screens.profiles

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.profiles.ProfileEffect
import com.nexus.gamebooster.ui.components.EmptyState
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.SectionCard
import com.nexus.gamebooster.ui.components.StatusChip
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.profileIcon
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale

/**
 * Phase 4 Profiles screen. Three fixed profiles, each card honestly split into
 *  - "Applied by NexusGameBooster" — what THIS app itself does under the profile,
 *  - "Recommended — needs your action" — device changes only the user can make,
 *  - "Not supported (system-wide)" — what normal apps may never change,
 * plus a collapsible "Monitoring policy" section (Phase 7) listing the raw policy
 * values the active profile drives (refresh interval, live battery events, screen-on,
 * in-app monitoring).
 *
 * Selecting a profile persists a preference and changes only this app's own
 * monitoring behavior; nothing on the device is modified and no recommendation is
 * ever labeled as applied. The explanation card states the system-wide limits openly.
 *
 * Phase 7 polish: selection is animated (container/border color, ~200 ms) and marked
 * by a scaling check icon + an "Active" chip — selection is NEVER conveyed by color
 * alone; the screen adopts the shared centered [NgbScreenContainer]; all motion is
 * gated by [LocalNgbMotionEnabled] (read once per screen).
 */
@Composable
fun ProfilesScreen() {
    val vm: ProfilesViewModel = viewModel(factory = VmFactory { ServiceLocator.profilesViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val motionEnabled = LocalNgbMotionEnabled.current

    NgbScreenContainer {
        Text(
            text = stringResource(R.string.profiles_headline),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.profiles_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // "Current profile" summary — the persisted (normalized) selection, or nothing
        // until it has loaded. The chip marks it as current; the note states the honesty
        // contract: this app's own behavior changes, the device does not.
        state.profiles.firstOrNull { it.id == state.activeProfileId }?.let { current ->
            CurrentProfileCard(profile = current)
            Spacer(Modifier.height(16.dp))
        }

        // Inline save-error surface: the previous profile is still active; the user can
        // retry the same selection (or the default) or dismiss the message.
        if (state.saveError) {
            SaveErrorCard(
                onRetry = {
                    vm.selectProfile(state.activeProfileId ?: state.profiles.first().id)
                },
                onDismiss = vm::dismissSaveError,
            )
            Spacer(Modifier.height(16.dp))
        }

        state.profiles.forEach { profile ->
            ProfileCard(
                profile = profile,
                selected = state.activeProfileId == profile.id,
                onClick = { vm.selectProfile(profile.id) },
                motionEnabled = motionEnabled,
            )
            Spacer(Modifier.height(12.dp))
        }

        SectionCard(
            title = stringResource(R.string.profiles_unsupported_title),
            icon = Icons.Outlined.Info,
        ) {
            Text(
                text = stringResource(R.string.profiles_unsupported_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.profiles_footnote),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        CustomProfilesCard()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CurrentProfileCard(profile: PerformanceProfile) {
    SectionCard(
        title = stringResource(R.string.profiles_current_label),
        icon = profileIcon(profile.id),
        trailing = {
            StatusChip(
                text = stringResource(R.string.profiles_current_label),
                variant = StatusChipVariant.OK,
            )
        },
    ) {
        Text(
            text = stringResource(profile.titleRes),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(profile.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profiles_current_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveErrorCard(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.profile_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.action_retry),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

/**
 * One selectable preset card. The whole card is the touch target (radiogroup row
 * semantics via `selectable` + a visual RadioButton) and is always well above the
 * 48 dp minimum; it carries the shared reduced-motion-aware press scale.
 *
 * Selection feedback: the container and border colors animate (~200 ms, instantly
 * under reduced motion), and the selected state is ALSO marked non-color-wise by a
 * scaling check icon and an "Active" chip (so TalkBack users and color-blind users
 * both get the state; `selectable` additionally exposes the radio selected state).
 *
 * The per-profile monitoring-policy rows live in a collapsible section at the bottom
 * (see [PolicyDetailsSection]); it defaults to expanded for the ACTIVE profile and
 * collapsed otherwise.
 */
@Composable
private fun ProfileCard(
    profile: PerformanceProfile,
    selected: Boolean,
    onClick: () -> Unit,
    motionEnabled: Boolean,
) {
    // Reduced-motion gate: the same tween that animates selection becomes a snap().
    val colorSpec = if (motionEnabled) tween<Color>(durationMillis = 200) else snap()
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = colorSpec,
        label = "profileContainerColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = colorSpec,
        label = "profileBorderColor",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motionEnabled) tween(durationMillis = 200) else snap(),
        label = "profileCheckScale",
    )

    // Collapsible policy details: default expanded ONLY for the active profile.
    // Keyed on (id, selected) so a selection change re-applies that default while
    // manual expand/collapse within a stable selection is preserved.
    var detailsExpanded by rememberSaveable(profile.id, selected) { mutableStateOf(selected) }

    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 2.dp, color = borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .ngbPressScale(interactionSource, motionEnabled)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = profileIcon(profile.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(profile.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    // Non-color selection markers: check icon scales in, chip is text.
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                                alpha = checkScale
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(
                        text = stringResource(R.string.profiles_active_chip),
                        variant = StatusChipVariant.OK,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(profile.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            EffectGroup(
                label = stringResource(R.string.effect_applied_title),
                effects = profile.appliedEffects,
                muted = false,
            )
            EffectGroup(
                label = stringResource(R.string.effect_recommended_title),
                effects = profile.recommendations,
                muted = true,
            )
            EffectGroup(
                label = stringResource(R.string.effect_not_supported_title),
                effects = profile.notSupportedEffects,
                muted = true,
            )
            PolicyDetailsSection(
                expanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded },
                profile = profile,
                motionEnabled = motionEnabled,
            )
        }
    }
}

/**
 * Collapsible "Monitoring policy" section of one profile card: a 48 dp header row
 * (title + animated chevron) above the raw policy rows (in-app monitoring, refresh
 * interval, live battery events, screen stays on). The container grows/shrinks with
 * [animateContentSize]; under reduced motion both the size change and the chevron
 * rotation are instant (snap) — the state itself is always announced to screen
 * readers via [stateDescription] plus an explicit show/hide click label.
 */
@Composable
private fun PolicyDetailsSection(
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
                .clip(RoundedCornerShape(8.dp))
                .ngbPressScale(interactionSource, motionEnabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClickLabel = if (expanded) hideLabel else showLabel,
                    onClick = onToggle,
                )
                .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel }
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.profiles_policy_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                val policy = profile.monitoringPolicy
                PolicyDetailRow(
                    text = stringResource(
                        if (policy.inAppMonitoringEnabled) {
                            R.string.profiles_policy_in_app_on
                        } else {
                            R.string.profiles_policy_in_app_off
                        },
                    ),
                )
                PolicyDetailRow(
                    text = if (policy.inAppMonitoringEnabled && policy.autoRefreshIntervalMillis != null) {
                        stringResource(
                            R.string.monitor_auto_refresh_every,
                            (policy.autoRefreshIntervalMillis / 1000L).toInt(),
                        )
                    } else {
                        stringResource(R.string.monitor_auto_refresh_off)
                    },
                )
                PolicyDetailRow(
                    text = stringResource(
                        if (policy.liveBatteryEvents) {
                            R.string.profiles_policy_live_on
                        } else {
                            R.string.profiles_policy_live_off
                        },
                    ),
                )
                PolicyDetailRow(
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

/** One honest policy value row — plain text, matching the muted effect-row style. */
@Composable
private fun PolicyDetailRow(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One labeled group of effect rows; muted styling for recommendations/unsupported. */
@Composable
private fun EffectGroup(
    label: String,
    effects: List<ProfileEffect>,
    muted: Boolean,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        effects.forEach { effect ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(effect.textRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CustomProfilesCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        EmptyState(
            icon = Icons.Outlined.Build,
            title = stringResource(R.string.custom_profiles_title),
            body = stringResource(R.string.custom_profiles_empty),
        )
    }
}

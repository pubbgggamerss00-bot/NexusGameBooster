package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.ngbPressScale

/** Minimum interactive height for app buttons (accessibility target size). */
private val MinButtonHeight = 48.dp

/**
 * App-standard filled button. Wraps Material3 [Button] with:
 *  - a minimum height of 48.dp, and
 *  - automatic, reduced-motion-aware press feedback (a shared interaction source is
 *    passed to the Material button AND to [ngbPressScale], so the 97% press scale
 *    works with zero per-callsite code and disappears entirely when
 *    [com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled] is false).
 *
 * Keep onClick handlers cheap (view-model calls only) — the press feedback runs on the
 * UI thread while the action executes.
 */
@Composable
fun NgbButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinButtonHeight)
            .ngbPressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * App-standard outlined button — same contract as [NgbButton] but Material3
 * [OutlinedButton] visuals. Use it for restrained/secondary actions (e.g. the
 * destructive-restraint "Reset app data" row on Settings).
 */
@Composable
fun NgbOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinButtonHeight)
            .ngbPressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

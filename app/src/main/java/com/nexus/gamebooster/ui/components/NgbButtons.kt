package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.neonGlow

/** Minimum interactive height for app buttons (accessibility target size). */
private val MinButtonHeight = 48.dp

/** Stadium shape — matches the rounded CTAs in the neon showcase look. */
private val CtaShape = RoundedCornerShape(percent = 50)

/**
 * App-standard filled button. Wraps Material3 [Button] with:
 *  - the neon stadium CTA shape,
 *  - a minimum height of 48.dp,
 *  - a static neon glow behind the button while enabled (single-color accent,
 *    no animation — pre-API 28 devices get the cheap halo fallback), and
 *  - automatic, reduced-motion-aware press feedback (a shared interaction source
 *    is passed to the Material button AND to [ngbPressScale], so the 97% press
 *    scale works with zero per-callsite code and disappears entirely when
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
    // Static glow only while the action is actually available — a disabled CTA
    // must not advertise itself.
    val baseModifier = if (enabled) {
        modifier.neonGlow(
            glowColor = MaterialTheme.colorScheme.primary,
            shape = CtaShape,
            blurRadius = 12.dp,
            glowAlpha = 0.35f,
        )
    } else {
        modifier
    }
    Button(
        onClick = onClick,
        modifier = baseModifier
            .heightIn(min = MinButtonHeight)
            .ngbPressScale(interactionSource),
        enabled = enabled,
        shape = CtaShape,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * App-standard outlined button — same contract as [NgbButton] but Material3
 * [OutlinedButton] visuals (no glow — the restrained sibling). Use it for
 * secondary/destructive-restraint actions (e.g. "Reset app data" on Settings).
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
        shape = CtaShape,
        interactionSource = interactionSource,
        content = content,
    )
}

package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.NgbPrimaryDeep
import com.nexus.gamebooster.ui.theme.neonGlow

/** Minimum interactive height for app buttons (accessibility target size). */
private val MinButtonHeight = 48.dp

/** Stadium shape — matches the rounded CTAs in the neon showcase look. */
private val CtaShape = RoundedCornerShape(percent = 50)

/**
 * The kit's READY-state CTA gradient — the standard look of every enabled
 * app button since the Phase 11.4 "NEON ARENA v1" kit application.
 */
private val KitCtaGradient = Brush.verticalGradient(listOf(NgbPrimaryBright, Color(0xFF27F58A).copy(alpha = 0.999f), NgbPrimaryDeep))

private val KitCtaShape = NgbChamferShape(12.dp)

private fun Modifier.kitCtaSurface(enabled: Boolean): Modifier = drawBehind {
    if (enabled) {
        drawRect(KitCtaGradient)
        // Kit inset highlights: top white lip + bottom dark edge.
        drawRect(Color.White.copy(alpha = 0.65f), topLeft = Offset(0f, 0f), size = size.copy(height = 1.5.dp.toPx()))
        drawRect(Color(0xFF003C1E).copy(alpha = 0.30f), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = size.copy(height = 4.dp.toPx()))
    }
}

/**
 * App-standard filled button — the kit CTA. Wraps Material3 [Button] with:
 *  - the kit's chamfered silhouette (12 dp cut corners, RTL-mirrored),
 *  - the READY-state vertical neon-green gradient with the inset white top
 *    lip and dark bottom edge (disabled buttons fall back to the Material
 *    disabled colors — the kit's ".s-off" intent, restrained),
 *  - a minimum height of 48.dp,
 *  - a static neon glow behind the button while enabled (single-color accent,
 *    no animation — pre-API 28 devices get the cheap halo fallback), and
 *  - the kit's exact press feedback: translateY(2 px) + scale(.985) via the
 *    shared interaction source (reduced-motion aware — [ngbPressScale]).
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
        modifier
            .neonGlow(glowColor = MaterialTheme.colorScheme.primary, shape = KitCtaShape, blurRadius = 12.dp, glowAlpha = 0.35f)
            .kitCtaSurface(enabled = true)
    } else {
        modifier
    }
    Button(
        onClick = onClick,
        modifier = baseModifier
            .heightIn(min = MinButtonHeight)
            .ngbPressScale(interactionSource),
        enabled = enabled,
        shape = KitCtaShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * App-standard outlined button — same contract as [NgbButton] but Material3
 * [OutlinedButton] visuals (no glow — the restrained sibling). Use it for
 * secondary/destructive-restraint actions (e.g. "Reset app data" on Settings).
 * For the kit's gradient-bordered secondary button use [NgbSecondaryButton].
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

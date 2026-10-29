package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape

/** Minimum interactive height for app buttons (accessibility target size). */
private val MinButtonHeight = 48.dp

/**
 * App-standard filled button — now the reference's 3D "lip" arcade key
 * (v1.2.2 theme-only port; identical public API as every previous release).
 *
 * The visual comes straight from the redesign reference's `.gbtn`: a chamfered
 * 12 dp silhouette, a dark lip base showing a 4 dp edge under a neon gradient
 * face, a physical press that sinks the face into the lip, a shine sweep
 * (motion-gated) and a soft neon glow while enabled. Disabled buttons fall
 * back to the restrained flat dark face with muted content.
 *
 * Same accessibility contract as before: 48 dp minimum height, real
 * [Role]-less Material-free surface handled inside [ArcadeButton] (Role.Button).
 */
@Composable
fun NgbButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    ArcadeButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = ArcadeTint.NEON,
        content = content,
    )
}

/**
 * App-standard outlined button — same contract as [NgbButton] but the
 * restrained Material3 [OutlinedButton] visuals, re-cut to the arcade's
 * 12 dp chamfer so the whole button family shares one silhouette.
 * Use it for secondary/destructive-restraint actions (e.g. "Reset app data").
 * For the reference's gradient-bordered secondary button use [NgbSecondaryButton].
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
        shape = NgbChamferShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}

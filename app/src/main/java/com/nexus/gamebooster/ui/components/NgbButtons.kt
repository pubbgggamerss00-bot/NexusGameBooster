package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbOnPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimaryDeep
import com.nexus.gamebooster.ui.theme.neonGlow

/** Minimum interactive height for app buttons (accessibility target size). */
private val MinButtonHeight = 48.dp

/**
 * The kit's 3D lip depth — mockup `.gbtn{padding:0 0 4px}` + `::before{inset:4px 0 0}`:
 * the deep base peeks 4 dp below the face and the face sinks onto it on press.
 */
private val LipDepth = 4.dp

/** Kit CTA chamfer — mockup `.gbtn` clip-path 12px, two opposite corners. */
private val KitCtaShape = NgbChamferShape(12.dp)

/**
 * The kit's face gradient (`.gbtn .face` background): 180° from
 * `color-mix(var(--c) 70%, #fff)` through `var(--c)` at 45% down to
 * `color-mix(var(--c) 80%, var(--d))` — the lighter-top 3D face.
 */
private fun gbtnFaceGradient(base: Color, deep: Color): Brush = Brush.verticalGradient(
    0f to lerp(base, Color.White, 0.30f),
    0.45f to base,
    1f to lerp(base, deep, 0.20f),
)

/**
 * App-standard filled button — the kit's `.gbtn` 3D lip button (v1.2.1 re-skin
 * from the HTML prototype). Wraps Material3 [Button] (semantics/height stay
 * Material) and reproduces the mockup look exactly:
 *  - the chamfered 12 dp two-corner silhouette ([NgbChamferShape]),
 *  - the deep-green base layer (`--d` #16A34A) peeking 4 dp BELOW the face —
 *    the classic "3D lip",
 *  - the 180° face gradient (white-tinted top → neon → deep-tinted bottom),
 *  - the kit sheen (34% white band sweeping every 3.2 s — motion-gated),
 *  - the exact press feedback: the face SINKS 4 dp onto the base
 *    (`.gbtn:active .face{transform:translateY(4px)}`), no scale,
 *  - a static neon glow behind the button while enabled.
 * Disabled buttons render the muted Material disabled container over a greyed
 * base (the mockup's grayscale/brightness(.6) restraint).
 *
 * Keep onClick handlers cheap (view-model calls only) — the press feedback runs
 * on the UI thread while the action executes.
 */
@Composable
fun NgbButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val motion = LocalNgbMotionEnabled.current

    // Animated sink progress 0..1 → translationY 0..4 dp (80 ms, kit .08s).
    val sink by animateFloatAsState(
        targetValue = if (pressed && motion && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "gbtnSink",
    )
    val disabledBase = MaterialTheme.colorScheme.surfaceContainerHighest

    // Outer box: hosts the 3D base layer; the face (Button) sits on top with a
    // 4 dp bottom reserve so the lip stays visible while idle.
    Box(
        modifier = modifier.drawBehind {
            // The base lip (mockup `.gbtn::before`): same chamfer, offset 4 dp down.
            val topPx = LipDepth.toPx()
            val baseSize = Size(size.width, (size.height - topPx).coerceAtLeast(0f))
            if (baseSize.height <= 0f) return@drawBehind
            translate(top = topPx) {
                when (val outline = KitCtaShape.createOutline(baseSize, layoutDirection, this)) {
                    is Outline.Generic -> drawPath(outline.path, if (enabled) NgbPrimaryDeep else disabledBase)
                    else -> drawRect(if (enabled) NgbPrimaryDeep else disabledBase)
                }
            }
        },
    ) {
        // The glow is a filter on the whole button in the mockup — enabled CTAs
        // only (a disabled button must not advertise itself).
        val glowModifier = if (enabled) {
            Modifier.neonGlow(glowColor = NgbPrimary, shape = KitCtaShape, blurRadius = 12.dp, glowAlpha = 0.40f)
        } else {
            Modifier
        }
        val faceModifier = Modifier
            // Reserve the lip strip below the face (mockup `padding:0 0 4px`).
            .padding(bottom = LipDepth)
            .heightIn(min = MinButtonHeight)
            .then(glowModifier)
            // Face sink: the face (gradient + content) moves together.
            .graphicsLayer { translationY = sink * LipDepth.toPx() }
            // The face gradient surface, inside the sinking layer.
            .drawBehind {
                if (enabled) drawRect(gbtnFaceGradient(NgbPrimary, NgbPrimaryDeep))
            }

        Button(
            onClick = onClick,
            modifier = faceModifier,
            enabled = enabled,
            shape = KitCtaShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (enabled) NgbOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            interactionSource = interactionSource,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    content()
                }
                // The kit sheen band rides the face (clipped by the Button shape).
                if (enabled) {
                    NgbKitSheen(modifier = Modifier.matchParentSize())
                }
            }
        }
    }
}

/**
 * App-standard outlined button — same contract as [NgbButton] but Material3
 * [OutlinedButton] visuals (no glow — the restrained sibling). Use it for
 * secondary/destructive-restraint actions (e.g. "Reset app data" on Settings).
 * For the kit's gradient-bordered secondary button use [NgbSecondaryButton].
 * v1.2.1: silhouette follows the kit's two-corner chamfer (8 dp cut).
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
            .heightIn(min = MinButtonHeight),
        enabled = enabled,
        shape = NgbChamferShape(8.dp),
        interactionSource = interactionSource,
        content = content,
    )
}

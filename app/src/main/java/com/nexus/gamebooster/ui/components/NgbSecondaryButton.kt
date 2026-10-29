package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbInkSoft
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright

/**
 * The kit's secondary button — the HTML prototype's `.obtn` (v1.2.1 re-skin):
 * a 10 dp chamfered button wrapped in the mockup's exact 135° ring gradient
 * `linear-gradient(135deg, var(--c), transparent 40%, var(--c))`, the dark
 * `--ink2` inner fill (1.5 dp inset), and the accent-colored label.
 *
 * Press feedback is the mockup's `.obtn:active{transform:scale(.96)}` — an
 * animated 96% scale, motion-gated (Reduce motion renders a static button).
 * The old NEON ARENA corner ticks are retired by this re-skin; the same
 * accessibility contract as [NgbButton] (48 dp minimum height) is kept.
 */
@Composable
fun NgbSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalNgbMotionEnabled.current

    // .obtn:active{transform:scale(.96)} — animated, motion-gated.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && motion) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "obtnPressScale",
    )

    // The 135° accent ring: accent → transparent at 40% → accent.
    val borderBrush = Brush.linearGradient(
        0f to NgbPrimary,
        0.40f to Color.Transparent,
        1f to NgbPrimary,
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = NgbChamferShape(10.dp),
        color = Color.Transparent,
        contentColor = if (enabled) NgbPrimaryBright else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .drawBehind {
                // The ring gradient = the mockup's wrapper background.
                drawRect(brush = borderBrush)
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(NgbChamferShape(9.dp))
                .drawBehind {
                    // var(--ink2) inner fill, no other decoration.
                    drawRect(NgbInkSoft)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

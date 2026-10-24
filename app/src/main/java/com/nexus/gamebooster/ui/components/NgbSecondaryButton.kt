package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.ngbCornerTicks
import com.nexus.gamebooster.ui.theme.NgbVioletDeep
import com.nexus.gamebooster.ui.theme.NgbSurfaceContainerLowest
import com.nexus.gamebooster.ui.theme.NgbPrimary

/**
 * The kit's secondary button (`.b2`): a chamfered button wrapped in a 1.5 dp
 * animated-gradient border (green → violet → green), dark inner surface,
 * green corner ticks and the exact "press" behavior (scale .975 + the inner
 * surface darkens toward the kit's pressed tone).
 *
 * The border gradient sweeps at 120° over a 200% band; the mockup animates
 * the sweep on hover, which does not exist on touch — here it is STATIC at the
 * kit's resting position (a deliberate, documented deviation; the glow shift
 * on press comes from the inner-surface change instead).
 *
 * Same accessibility contract as [NgbButton]: 48 dp minimum height.
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

    val borderBrush = Brush.linearGradient(
        listOf(NgbPrimary, NgbVioletDeep, NgbPrimary),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = NgbChamferShape(11.dp),
        color = Color.Transparent,
        contentColor = if (enabled) NgbPrimaryBright else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .heightIn(min = 48.dp)
            .ngbPressScale(interaction)
            .drawBehind {
                // The 1.5 dp gradient border = the kit's wrapper padding.
                drawRect(brush = borderBrush)
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(NgbChamferShape(10.dp))
                .drawBehind {
                    drawRect(if (pressed) Color(0xFF06231A) else NgbSurfaceContainerLowest)
                    if (enabled) {
                        ngbCornerTicks(
                            color = NgbPrimary.copy(alpha = 0.85f),
                            tick = 7.dp, inset = 5.dp, stroke = 1.5.dp,
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

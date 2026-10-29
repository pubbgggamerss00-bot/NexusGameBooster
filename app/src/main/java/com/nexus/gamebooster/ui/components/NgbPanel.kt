package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbError
import com.nexus.gamebooster.ui.theme.NgbOutline
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbSurface
import com.nexus.gamebooster.ui.theme.NgbSurfaceVariant
import com.nexus.gamebooster.ui.theme.NgbTertiary
import com.nexus.gamebooster.ui.theme.NgbWarning

/**
 * "ROCKET EDITION" panel kit (v1.2.1 re-skin, straight from the HTML prototype's
 * `.panel` CSS): a chamfered box whose EDGE is painted by a 135° accent-gradient
 * "ring", with the dark card gradient inset 1 px inside it — the mockup's exact
 * two-layer trick (outer gradient background + `::before` inset fill):
 *
 * ```
 * .panel{background:linear-gradient(135deg, rgba(neon,.75), rgba(azure,.18) 45%, rgba(neon,.4));
 *        clip-path:polygon(cut 0, ...)}
 * .panel::before{inset:1px; background:linear-gradient(160deg, var(--card2), var(--card) 60%)}
 * ```
 *
 * Ring variants mirror the mockup exactly: [NgbPanelRing.Default] (green/azure),
 * [NgbPanelRing.Hot] (`.hot` — neon→azure→lime), [NgbPanelRing.Warn] (amber),
 * [NgbPanelRing.Bad] (red) and [NgbPanelRing.Flat] (hairline green). Content is
 * padded 14 dp like the mockup (`padding:14px`).
 *
 * Presentation-only: no state, no animation — safe to wrap anything.
 */
enum class NgbPanelRing { Default, Hot, Warn, Bad, Flat }

/** The 135° ring gradient for a panel variant, colors straight from the mockup CSS. */
private fun ringBrush(ring: NgbPanelRing): Brush {
    val stops: Array<Pair<Float, Color>> = when (ring) {
        NgbPanelRing.Default -> arrayOf(
            0f to NgbPrimary.copy(alpha = 0.75f),
            0.45f to NgbTertiary.copy(alpha = 0.18f),
            1f to NgbPrimary.copy(alpha = 0.40f),
        )
        NgbPanelRing.Hot -> arrayOf(
            0f to NgbPrimary,
            0.5f to NgbTertiary,
            1f to NgbSecondary,
        )
        NgbPanelRing.Warn -> arrayOf(
            0f to NgbWarning,
            1f to NgbWarning.copy(alpha = 0.20f),
        )
        NgbPanelRing.Bad -> arrayOf(
            0f to NgbError,
            1f to NgbError.copy(alpha = 0.20f),
        )
        NgbPanelRing.Flat -> arrayOf(
            0f to NgbOutline,
            1f to NgbOutline,
        )
    }
    // 135 deg in CSS = top-left → bottom-right diagonal.
    return Brush.linearGradient(
        *stops,
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
}

/**
 * The kit panel. [cut] is the chamfer size (mockup `--cut: 14px`),
 * [contentPadding] the inner padding (mockup `padding: 14px`).
 */
@Composable
fun NgbPanel(
    modifier: Modifier = Modifier,
    cut: Dp = 14.dp,
    ring: NgbPanelRing = NgbPanelRing.Default,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.clip(NgbChamferShape(cut))) {
        // Layer 1 — the accent ring (the box's own background in the mockup).
        Box(Modifier.matchParentSize().background(ringBrush(ring)))
        // Layer 2 — the dark card gradient inset 1 px (the ::before fill).
        val innerCut = (cut - 1.dp).coerceAtLeast(2.dp)
        Box(
            Modifier
                .matchParentSize()
                .padding(1.dp)
                .clip(NgbChamferShape(innerCut))
                .background(
                    // 160 deg ≈ near-vertical: card2 (#0F301C) → card (#0A2314) at 60%.
                    Brush.verticalGradient(listOf(NgbSurfaceVariant, NgbSurfaceVariant, NgbSurface)),
                ),
        )
        // Content sits above both layers (mockup `.panel > * { position:relative }`).
        // It is the SIZE-DEFINING child: the ring layers use matchParentSize.
        Column(
            modifier = Modifier
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * The kit sheen (`.gbtn .face::after`): a 34%-wide skewed white band sweeping
 * across the element every 3.2 s (`@keyframes sweep`), used on the primary CTA
 * and connect surfaces. Fully gated by [LocalNgbMotionEnabled] — with Reduce
 * motion it renders nothing (the band simply never appears).
 */
@Composable
fun NgbKitSheen(modifier: Modifier = Modifier, bandAlpha: Float = 0.55f) {
    val motion = LocalNgbMotionEnabled.current
    if (!motion) return
    val t = rememberInfiniteTransition(label = "ngbKitSheen")
    val x by t.animateFloat(
        initialValue = -1.6f, targetValue = 4.6f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), repeatMode = androidx.compose.animation.core.RepeatMode.Restart),
        label = "ngbKitSheenX",
    )
    Canvas(modifier) { drawSheenBand(x, bandAlpha) }
}

/** One skewed band pass — parallelogram matching the mockup's skewX(-20deg). */
private fun DrawScope.drawSheenBand(progress: Float, bandAlpha: Float) {
    val bandWidth = size.width * 0.34f
    val skew = size.height * 0.36f // ≈ tan(20°)
    val left = size.width * progress
    translate(left = left) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(bandWidth, 0f)
            lineTo(bandWidth - skew, size.height)
            lineTo(-skew, size.height)
            close()
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = bandAlpha), Color.Transparent),
                startX = 0f, endX = bandWidth,
            ),
        )
    }
}

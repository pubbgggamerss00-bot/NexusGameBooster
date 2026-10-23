package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * "NEON ARENA" kit geometry (Phase 11.4): the mockup's signature cut-corner
 * ("گوشه‌براکتی") silhouette, exported as a real [Shape].
 *
 * The kit's clip-path cuts TWO opposite corners — top-left and bottom-right in
 * the mockup's coordinates. Because every screen of this app is laid out in
 * Persian first (RTL), the cut corners mirror with the layout direction:
 * in RTL they land on top-right / bottom-left, exactly as a mirrored design
 * board would. LTR layouts keep the mockup's original corners.
 *
 * No blur, no bitmap — one Path per outline request, cached by Compose.
 */
class NgbChamferShape(private val cut: Dp) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val c = with(density) { cut.toPx() }
        val w = size.width
        val h = size.height
        // Amount cut from the left/right edge; mirrored in RTL.
        val leftCut = if (layoutDirection == LayoutDirection.Rtl) 0f else c
        val rightCut = if (layoutDirection == LayoutDirection.Rtl) c else 0f
        val path = Path().apply {
            moveTo(leftCut, 0f)
            lineTo(w, 0f)
            lineTo(w, h - rightCut)
            lineTo(w - rightCut, h)
            lineTo(0f, h)
            lineTo(0f, leftCut)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Draws the kit's two small L-shaped corner ticks (`.tk`/`.cn` in the mockup
 * CSS) inside the chamfered element: one at top-end, one at bottom-start —
 * the same opposite-corner rhythm as the shape itself. Ticks mirror with the
 * layout direction exactly like [NgbChamferShape].
 */
fun DrawScope.ngbCornerTicks(
    color: Color,
    tick: Dp = 8.dp,
    inset: Dp = 6.dp,
    stroke: Dp = 2.dp,
) {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val t = tick.toPx()
    val i = inset.toPx()
    val s = stroke.toPx()
    // Top-end tick: horizontal + vertical segment forming an "L" opening inward.
    val topEndX = if (rtl) i else size.width - i
    val botStartX = if (rtl) size.width - i else i
    // horizontal segment
    drawLine(
        color = color,
        start = Offset(topEndX - if (rtl) t else 0f, i),
        end = Offset(topEndX, i),
        strokeWidth = s,
    )
    drawLine(
        color = color,
        start = Offset(topEndX, i),
        end = Offset(topEndX, i + t),
        strokeWidth = s,
    )
    // bottom-start tick
    drawLine(
        color = color,
        start = Offset(botStartX, size.height - i),
        end = Offset(botStartX + if (rtl) 0f else t, size.height - i),
        strokeWidth = s,
    )
    drawLine(
        color = color,
        start = Offset(botStartX, size.height - i),
        end = Offset(botStartX, size.height - i - t),
        strokeWidth = s,
    )
}

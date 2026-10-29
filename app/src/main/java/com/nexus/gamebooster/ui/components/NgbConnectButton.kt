package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbOnRedCta
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.NgbPrimaryDeep
import com.nexus.gamebooster.ui.theme.ngbCornerTicks
import com.nexus.gamebooster.ui.theme.neonGlow
import com.nexus.gamebooster.ui.theme.NgbSurfaceContainerHighest
import com.nexus.gamebooster.ui.theme.NgbSurfaceContainerHigh
import com.nexus.gamebooster.ui.theme.NgbSurfaceContainer
import com.nexus.gamebooster.ui.theme.NgbTextMuted
import com.nexus.gamebooster.ui.theme.NgbOnBackground
import com.nexus.gamebooster.ui.theme.NgbPrimary

/**
 * The five tunnel-CTA states from the kit artboard, mapped 1:1 from the mockup
 * CSS (`.s-off` / `.s-ready` / `.s-pressed` / `.s-connecting` / `.s-on`).
 *
 *  - [LOCKED]: no usable input yet (no .conf imported / no resolver chosen) —
 *    the grey gradient with muted text; not clickable, no glow.
 *  - [READY]: armed and tappable — the neon-green gradient with the pulsing
 *    outer glow (2.2 s cycle) and the 3.2 s sheen sweep.
 *  - [CONNECTING]: work in progress — dark blue gradient, green inner ring,
 *    animated 115° stripes (0.7 s loop) and a spinner; onClick disabled.
 *  - [CONNECTED]: the tunnel is UP — the neon-RED gradient ("press to cut")
 *    with the red pulsing glow and the sheen sweep.
 *
 * Press feedback on the tappable states reproduces the mockup exactly:
 * translateY(2px) + scale(.985) + brightness(.92) (the brightness drop is an
 * 8% black scrim so it composes safely on every GPU path).
 *
 * Motion honesty: every continuous animation (glow pulse, sheen, stripes) is
 * gated behind [LocalNgbMotionEnabled] — with "Reduce motion" on (or system
 * animations disabled) the button is fully STATIC: same colors, same shape,
 * same inset highlights, no loops. Motion is feedback, never information —
 * the state is always readable from the text + colors alone.
 */
enum class NgbConnectState { LOCKED, READY, CONNECTING, CONNECTED }

// v1.2.1: kit-exact 12 dp two-corner chamfer (the mockup `.gbtn` cut).
private val ConnectShape = NgbChamferShape(12.dp)
private val ConnectHexShape = NgbChamferShape(8.dp)

/** Kit CTA vertical gradients, one per state (straight from the mockup CSS). */
private fun stateGradient(state: NgbConnectState): Brush = when (state) {
    NgbConnectState.LOCKED -> Brush.verticalGradient(listOf(NgbSurfaceContainerHighest, NgbSurfaceContainerHigh))
    NgbConnectState.READY -> Brush.verticalGradient(listOf(NgbPrimaryBright, NgbPrimary, NgbPrimaryDeep))
    NgbConnectState.CONNECTING -> Brush.verticalGradient(listOf(Color(0xFF163D24), NgbSurfaceContainer))
    NgbConnectState.CONNECTED -> Brush.verticalGradient(listOf(Color(0xFFFF8CA3), Color(0xFFFF4D6D), Color(0xFFD42A4B)))
}

private fun stateTextColor(state: NgbConnectState): Color = when (state) {
    NgbConnectState.LOCKED -> NgbTextMuted
    NgbConnectState.READY -> Color(0xFF04110A)
    NgbConnectState.CONNECTING -> NgbOnBackground
    NgbConnectState.CONNECTED -> NgbOnRedCta
}

@Composable
fun NgbConnectButton(
    state: NgbConnectState,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDescription: String? = null,
) {
    val motion = LocalNgbMotionEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState().value && state != NgbConnectState.CONNECTING && state != NgbConnectState.LOCKED

    // readyGlow / onGlow — 2.2 s ease-in-out pulse of the drop-shadow strength.
    val glowAlpha: Float
    if (motion && (state == NgbConnectState.READY || state == NgbConnectState.CONNECTED)) {
        val pulse = rememberInfiniteTransition(label = "ngbConnectGlow")
        val g by pulse.animateFloat(
            initialValue = 0.32f, targetValue = 0.80f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "ngbConnectGlowAlpha",
        )
        glowAlpha = g
    } else {
        glowAlpha = when (state) {
            NgbConnectState.READY, NgbConnectState.CONNECTED -> 0.40f
            else -> 0f
        }
    }

    val glowColor = if (state == NgbConnectState.CONNECTED) Color(0xFFFF4D6D) else NgbPrimary
    val baseModifier = modifier
        .heightIn(min = 56.dp)
        .neonGlow(glowColor = glowColor, shape = ConnectShape, blurRadius = 14.dp, glowAlpha = glowAlpha)
        .graphicsLayer {
            if (pressed) {
                translationY = 2.dp.toPx()
                scaleX = 0.985f
                scaleY = 0.985f
            }
        }

    Surface(
        onClick = onClick,
        enabled = state == NgbConnectState.READY || state == NgbConnectState.CONNECTED,
        interactionSource = interaction,
        shape = ConnectShape,
        color = Color.Transparent,
        contentColor = stateTextColor(state),
        modifier = baseModifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(ConnectShape)
                .drawBehind {
                    // state gradient + the kit's inset highlights (top white lip,
                    // bottom dark edge) + corner ticks.
                    drawBehindStateSurface(state)
                    val tickColor = when (state) {
                        NgbConnectState.LOCKED -> Color.White.copy(alpha = 0.14f)
                        else -> Color.Black.copy(alpha = 0.35f)
                    }
                    ngbCornerTicks(color = tickColor, tick = 8.dp, inset = 6.dp, stroke = 2.dp)
                },
        ) {
            // 3.2 s sheen sweep on READY / CONNECTED (motion-gated).
            if (motion && (state == NgbConnectState.READY || state == NgbConnectState.CONNECTED)) {
                SheenOverlay()
            }
            // 115° animated stripes on CONNECTING (motion-gated).
            if (state == NgbConnectState.CONNECTING) {
                if (motion) StripesOverlay() else StaticStripes()
            }
            // pressed brightness(.92) — 8% black scrim, kit-exact effect.
            if (pressed) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.08f)) })
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(ConnectHexShape)
                            .drawBehind {
                                drawRect(
                                    when (state) {
                                        NgbConnectState.CONNECTING -> NgbPrimary.copy(alpha = 0.16f)
                                        NgbConnectState.LOCKED -> Color.White.copy(alpha = 0.06f)
                                        else -> Color.Black.copy(alpha = 0.22f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = iconDescription,
                            tint = if (state == NgbConnectState.CONNECTING) NgbPrimaryBright else stateTextColor(state),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(Modifier.size(12.dp))
                }
                Text(
                    label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                )
                if (state == NgbConnectState.CONNECTING) {
                    Box(Modifier.size(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NgbPrimaryBright,
                    )
                }
            }
        }
    }
}

/** Paints the state gradient plus the two inset lips from the kit CSS. */
private fun DrawScope.drawBehindStateSurface(state: NgbConnectState) {
    drawRect(brush = stateGradient(state))
    when (state) {
        NgbConnectState.READY -> {
            drawRect(Color.White.copy(alpha = 0.65f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
            drawRect(Color(0xFF003C1E).copy(alpha = 0.30f), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
        }
        NgbConnectState.CONNECTED -> {
            drawRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
            drawRect(Color(0xFF5A0014).copy(alpha = 0.30f), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
        }
        NgbConnectState.LOCKED -> {
            drawRect(Color.White.copy(alpha = 0.08f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
        }
        NgbConnectState.CONNECTING -> {
            // inset 0 0 0 1.5px rgba(39,245,138,.55) — inner ring
            val s = 1.5.dp.toPx()
            drawRect(NgbPrimary.copy(alpha = 0.55f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, s))
            drawRect(NgbPrimary.copy(alpha = 0.55f), topLeft = Offset(0f, size.height - s), size = androidx.compose.ui.geometry.Size(size.width, s))
            drawRect(NgbPrimary.copy(alpha = 0.55f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(s, size.height))
            drawRect(NgbPrimary.copy(alpha = 0.55f), topLeft = Offset(size.width - s, 0f), size = androidx.compose.ui.geometry.Size(s, size.height))
        }
    }
}

/** The kit's `sheen` keyframes: a skewed white band sweeping across in 3.2 s. */
@Composable
private fun SheenOverlay() {
    val t = rememberInfiniteTransition(label = "ngbConnectSheen")
    val x by t.animateFloat(
        initialValue = -1.8f, targetValue = 4.8f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing)),
        label = "ngbConnectSheenX",
    )
    Canvas(Modifier.fillMaxSize()) {
        val bandWidth = size.width * 0.26f
        val left = size.width * x
        translate(left = left) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.60f), Color.Transparent),
                    startX = 0f, endX = bandWidth,
                ),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(bandWidth, size.height),
                alpha = 1f,
            )
        }
    }
}

/**
 * The kit's `stripe` overlay for CONNECTING: a repeating 115° green stripe
 * pattern scrolled horizontally (0.7 s loop). Drawn as periodic parallelograms.
 */
@Composable
private fun StripesOverlay() {
    val t = rememberInfiniteTransition(label = "ngbConnectStripes")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "ngbConnectStripePhase",
    )
    Canvas(Modifier.fillMaxSize().drawWithContent { drawContent() }) { drawStripes(phase) }
}

@Composable
private fun StaticStripes() {
    Canvas(Modifier.fillMaxSize()) { drawStripes(0f) }
}

private fun DrawScope.drawStripes(phase: Float) {
    val stripe = 12.dp.toPx()
    val gap = 12.dp.toPx()
    val period = stripe + gap
    val skew = size.height * 0.47f // ≈ tan(25°) for the 115° look
    val scroll = phase * period
    val path = androidx.compose.ui.graphics.Path()
    var x = -period * 2f - skew + scroll
    while (x < size.width + skew) {
        // A skewed parallelogram: bottom edge shifted right by `skew`.
        path.moveTo(x, 0f)
        path.lineTo(x + stripe, 0f)
        path.lineTo(x + stripe + skew, size.height)
        path.lineTo(x + skew, size.height)
        path.close()
        x += period
    }
    drawPath(path, color = NgbPrimary.copy(alpha = 0.22f))
}

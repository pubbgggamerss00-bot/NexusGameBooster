package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.ui.arcade.ArcadeInkText
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeStepBadge
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.arcade.OrbitSpeed
import com.nexus.gamebooster.ui.arcade.arcadeOrbitSpin
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbTagFont
import kotlin.math.cos
import kotlin.math.sin

/** One row of the reactor's honest step list: a real check or a real action outcome. */
data class BoostStepUi(
    val label: String,
    val done: Boolean,
    val active: Boolean,
)

/**
 * THE REACTOR — the Home centerpiece restyled to the v1.2.1 "ARCADE HUD" reference
 * (`.panel.flat` + `.reactor`): a 236 dp square with a spinning dashed orbit ring and
 * lime N/E/S/W ticks, 60 static dial ticks, the base track and a progress arc, a
 * pulsing halo and the two-hexagon core button.
 *
 * HONESTY CONTRACT (unchanged from the previous BoostCard): the arc NEVER carries a
 * fake percentage. The app's boost engine exposes no numeric progress and no
 * freed-memory value, so while a run is active the arc renders as an INDETERMINATE
 * sweeping segment (no number is shown anywhere), and when idle/finished it is absent.
 * The core label/tagline and the step rows are mapped by the owning screen from REAL
 * state only ([BoostStepUi] — pre-checks and recorded action outcomes).
 *
 * Reactor phases are driven by the caller: [accentColor] recolors ring/halo/core,
 * [orbitSpeed] spins the orbit ring (idle slow / checking fast / running cruise),
 * [label] is the localized honest status text, [tagline] is the decorative HUD code
 * ("TAP TO BOOST" / "CHECKING" / "BOOSTING" / "READY" — brand-style English-only tags
 * exactly like the reference, not translatable copy). The button is disabled while a
 * run is active; [onCancel] renders the full-width danger outline cancel button while
 * [inProgress]. Press feedback scales to 0.93 (motion-gated) and the core stays a
 * [Role.Button] whose merged label is the visible status text.
 *
 * All infinite animations (orbit spin via the kit, halo pulse, idle core pulse) are
 * gated by [LocalNgbMotionEnabled] — reduced motion renders the same layout fully
 * static.
 */
@Composable
fun BoostCard(
    label: String,
    tagline: String,
    accentColor: Color,
    enabled: Boolean,
    onBoostClick: () -> Unit,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false,
    orbitSpeed: OrbitSpeed = OrbitSpeed.IDLE,
    steps: List<BoostStepUi> = emptyList(),
    onCancel: (() -> Unit)? = null,
) {
    ArcadePanel(
        modifier = modifier.fillMaxWidth(),
        variant = ArcadePanelVariant.FLAT,
        cut = 22.dp,
        contentPadding = 14.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Reactor(
                accent = accentColor,
                orbitSpeed = orbitSpeed,
                label = label,
                tagline = tagline,
                enabled = enabled,
                inProgress = inProgress,
                onBoostClick = onBoostClick,
            )
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    steps.forEach { step -> StepRow(step = step) }
                }
            }
            if (inProgress && onCancel != null) {
                Spacer(Modifier.height(12.dp))
                ArcadeOutlineButton(
                    onClick = onCancel,
                    tint = ArcadeTint.DANGER,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

/** The 236 dp reactor square: orbit ring, dial ticks, track, arc, halo and hex core. */
@Composable
private fun Reactor(
    accent: Color,
    orbitSpeed: OrbitSpeed,
    label: String,
    tagline: String,
    enabled: Boolean,
    inProgress: Boolean,
    onBoostClick: () -> Unit,
) {
    val motion = LocalNgbMotionEnabled.current
    // Indeterminate arc angle — only used while a run is active, never a percentage.
    val arcSpin = rememberInfiniteTransition(label = "reactorArc")
    val arcAngle by arcSpin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "reactorArcAngle",
    )
    Box(modifier = Modifier.size(ReactorSize), contentAlignment = Alignment.Center) {
        // 1. The spinning orbit group: dashed ring + 4 lime N/E/S/W ticks.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .arcadeOrbitSpin(orbitSpeed),
        ) {
            val u = size.minDimension / 240f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = 114f * u,
                center = c,
                style = Stroke(
                    width = 2f * u,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(2f * u, 10f * u, 40f * u, 10f * u),
                    ),
                ),
            )
            val tick = 14f * u
            val edge = 118f * u
            drawLine(NgbArcadeColors.Lime, Offset(c.x, c.y - edge), Offset(c.x, c.y - edge + tick), strokeWidth = 3f * u)
            drawLine(NgbArcadeColors.Lime, Offset(c.x, c.y + edge), Offset(c.x, c.y + edge - tick), strokeWidth = 3f * u)
            drawLine(NgbArcadeColors.Lime, Offset(c.x - edge, c.y), Offset(c.x - edge + tick, c.y), strokeWidth = 3f * u)
            drawLine(NgbArcadeColors.Lime, Offset(c.x + edge, c.y), Offset(c.x + edge - tick, c.y), strokeWidth = 3f * u)
        }
        // 2. Static dial: 60 tick marks, base track, indeterminate arc while running.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDial(
                accent = accent,
                inProgress = inProgress,
                arcStart = if (motion) arcAngle else 0f,
            )
        }
        // 3. Pulsing halo disc (motion-gated).
        ReactorHalo(accent = accent)
        // 4. The hex core button.
        HexCore(
            accent = accent,
            label = label,
            tagline = tagline,
            enabled = enabled,
            onBoostClick = onBoostClick,
        )
    }
}

/** Static dial paint: 60 ticks at r≈104, the Line track at r=94, the indeterminate arc. */
private fun DrawScope.drawDial(accent: Color, inProgress: Boolean, arcStart: Float) {
    val u = size.minDimension / 240f
    val c = Offset(size.width / 2f, size.height / 2f)
    for (i in 0 until 60) {
        val angle = Math.toRadians((i * 6).toDouble())
        val sinA = sin(angle).toFloat()
        val cosA = cos(angle).toFloat()
        val major = i % 5 == 0
        val len = if (major) 10f * u else 5f * u
        drawLine(
            color = if (major) NgbArcadeColors.Muted else Color(0xFF2B5B3D),
            start = Offset(c.x + sinA * 104f * u, c.y - cosA * 104f * u),
            end = Offset(c.x + sinA * (104f * u - len), c.y - cosA * (104f * u - len)),
            strokeWidth = if (major) 2f * u else 1f * u,
        )
    }
    drawCircle(
        color = NgbArcadeColors.Line,
        radius = 94f * u,
        center = c,
        style = Stroke(width = 6f * u),
    )
    if (inProgress) {
        // Indeterminate sweeping segment — the honest stand-in for progress the engine
        // does not report. No percentage text is ever attached to it.
        drawArc(
            color = accent.copy(alpha = 0.9f),
            startAngle = -90f + arcStart,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(c.x - 94f * u, c.y - 94f * u),
            size = Size(188f * u, 188f * u),
            style = Stroke(width = 6f * u),
        )
    }
}

/** The halo: a tint radial-gradient disc pulsing over 2.2 s (static when motion is off). */
@Composable
private fun ReactorHalo(accent: Color) {
    val motion = LocalNgbMotionEnabled.current
    val pulse = rememberInfiniteTransition(label = "reactorHalo")
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "reactorHaloAlpha",
    )
    Box(
        modifier = Modifier
            .size(168.dp)
            .graphicsLayer { alpha = if (motion) haloAlpha else 0.8f }
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to accent.copy(alpha = 0.40f),
                        0.68f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension / 2f,
                    ),
                )
            },
    )
}

/**
 * The hex core button: two stacked hexagons (clip-path polygon 50/93/93/50/7/7/25/75/25
 * construction) — outer solid tint, inner (4 dp inset) top-lit radial gradient — with
 * the bolt icon, the honest status label and the decorative HUD tagline. Press sinks
 * the whole core to 0.93 (motion-gated); the idle inner face pulses (motion-gated).
 */
@Composable
private fun HexCore(
    accent: Color,
    label: String,
    tagline: String,
    enabled: Boolean,
    onBoostClick: () -> Unit,
) {
    val motion = LocalNgbMotionEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressTarget = if (pressed && enabled) 0.93f else 1f
    val pressScale by animateFloatAsState(
        targetValue = pressTarget,
        animationSpec = tween(120),
        label = "corePress",
    )
    val scale = if (motion) pressScale else 1f
    val idlePulse = rememberInfiniteTransition(label = "coreIdle")
    val idleAlpha by idlePulse.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "coreIdleAlpha",
    )
    val innerAlpha = if (motion && enabled) idleAlpha else 1f
    Box(
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onBoostClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer hexagon: solid tint.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawPath(path = hexagonPath(size.width, size.height, 0f), color = accent)
        }
        // Inner hexagon (4 dp inset): top-lit radial gradient, idle pulse.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = innerAlpha },
        ) {
            drawPath(
                path = hexagonPath(size.width, size.height, 4.dp.toPx()),
                brush = Brush.radialGradient(
                    0f to lerp(accent, Color.White, 0.55f),
                    0.55f to accent,
                    1f to lerp(accent, Color.Black, 0.55f),
                    center = Offset(size.width / 2f, size.height * 0.30f),
                    radius = size.width * 0.85f,
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = ArcadeInkText,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = label,
                color = ArcadeInkText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = tagline,
                color = ArcadeInkText,
                fontFamily = NgbTagFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}

/** The core's hexagonal silhouette: the reference clip-path polygon, as a path. */
private fun hexagonPath(w: Float, h: Float, inset: Float): Path = Path().apply {
    val left = inset
    val right = w - inset
    val top = inset
    val bottom = h - inset
    val q = (bottom - top) * 0.25f
    moveTo(w / 2f, top)
    lineTo(right, top + q)
    lineTo(right, bottom - q)
    lineTo(w / 2f, bottom)
    lineTo(left, bottom - q)
    lineTo(left, top + q)
    close()
}

/** One step row: kit badge + honest label (done fills, active blinks, pending muted). */
@Composable
private fun StepRow(step: BoostStepUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ArcadeStepBadge(active = step.active, done = step.done)
        Spacer(Modifier.width(10.dp))
        Text(
            text = step.label,
            fontSize = 12.5.sp,
            fontWeight = if (step.done || step.active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (step.done || step.active) {
                MaterialTheme.colorScheme.onBackground
            } else {
                NgbArcadeColors.Muted
            },
        )
    }
}

/** The reference's fixed 236×236 reactor square. */
private val ReactorSize = 236.dp

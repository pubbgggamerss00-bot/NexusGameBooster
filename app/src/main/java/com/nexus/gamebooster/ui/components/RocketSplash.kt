package com.nexus.gamebooster.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.BuildConfig
import com.nexus.gamebooster.R
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbBackground
import com.nexus.gamebooster.ui.theme.NgbOnBackground
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbSurfaceVariant
import com.nexus.gamebooster.ui.theme.NgbTextMuted
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// -----------------------------------------------------------------------------
// "Rocket Edition" 3D launch splash (v1.2.0, Phase 18) — a presentation-only
// port of the web prototype's Splash (src/components/nexus/phone-app.tsx):
// twinkling starfield drifting past (the rocket's upward flight), a scanline
// sweep, HUD corner brackets, the app icon floating in a 3D-feel scene inside
// two counter-rotating orbit rings (flattened scaleY ~0.9) over a pulsing
// engine flare, the glowing NEXUSGAMEBOOSTER wordmark, a segmented arcade
// power meter and the version chip.
//
// Honesty: this gate is COSMETIC ONLY. [RocketSplashOverlay] holds for ~2.6 s
// (400 ms with motion off — web parity) and fades out; the real UI is already
// composed underneath it, so no license check, network call or setting load
// waits for the animation. Every infinite animation is gated by
// [LocalNgbMotionEnabled]: with Reduce motion on (or the system animation
// scale at 0) the splash renders its static mid-state with no motion at all.
// -----------------------------------------------------------------------------

/** One star of the splash starfield (drift + twinkle). */
private data class RocketStar(
    val xFraction: Float,
    val yFraction: Float,
    val phase: Float,
    val periodMillis: Long,
    val radiusDp: Float,
    /** Fraction of the screen height the star falls per second (parallax depth). */
    val driftPerSecond: Float,
)

private val RocketStarfield: List<RocketStar> = run {
    val rng = Random(0x3DFF7C) // deterministic field — same sky every launch
    List(40) {
        RocketStar(
            xFraction = rng.nextFloat(),
            yFraction = rng.nextFloat(),
            phase = rng.nextFloat(),
            periodMillis = 2000L + (rng.nextFloat() * 3000f).toLong(),
            radiusDp = 0.7f + rng.nextFloat() * 0.9f,
            driftPerSecond = 0.03f + rng.nextFloat() * 0.05f,
        )
    }
}

/** HUD corner blink phases (web .spl-corner--tl/tr/bl/br delays 0/.4/.8/1.2 s of a 2.2 s cycle). */
private val CornerPhases = listOf(0f, 0.4f / 2.2f, 0.8f / 2.2f, 1.2f / 2.2f)

private fun DrawScope.drawRocketStarfield(
    densityPxPerDp: Float,
    frameNanos: Long,
    motionEnabled: Boolean,
) {
    val nowMillis = frameNanos / 1_000_000L
    val nowSeconds = nowMillis / 1000f
    RocketStarfield.forEach { star ->
        val twinkle = if (motionEnabled) {
            val t = (nowMillis % star.periodMillis).toFloat() / star.periodMillis
            0.5f + 0.5f * sin(2.0 * Math.PI * (t + star.phase).toDouble()).toFloat()
        } else {
            0.55f
        }
        // The rocket climbs: the field drifts DOWN past it (web ngb-starfall).
        val yFraction = if (motionEnabled) {
            (nowSeconds * star.driftPerSecond + star.phase) % 1f
        } else {
            star.yFraction
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.2f + 0.75f * twinkle),
            radius = star.radiusDp * densityPxPerDp * (1f + 0.4f * twinkle),
            center = Offset(star.xFraction * size.width, yFraction * size.height),
        )
    }
}

/**
 * The splash itself. Static-friendly: with motion disabled every infinite
 * animation is replaced by its fixed mid-state (no transitions run at all).
 */
@Composable
fun RocketSplash(modifier: Modifier = Modifier) {
    val motionEnabled = LocalNgbMotionEnabled.current
    var frameNanos by remember { mutableLongStateOf(0L) }
    if (motionEnabled) {
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos = it }
        }
    }
    val pxPerDp = with(LocalDensity.current) { 1.dp.toPx() }
    val launcherDescription = stringResource(R.string.loader_content_description)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NgbBackground)
            .semantics { contentDescription = launcherDescription },
    ) {
        // Parallax starfield.
        Canvas(Modifier.fillMaxSize()) {
            drawRocketStarfield(
                densityPxPerDp = pxPerDp,
                frameNanos = frameNanos,
                motionEnabled = motionEnabled,
            )
        }
        // Scanline sweep (web .spl-scan): one translucent band gliding down the panel.
        if (motionEnabled) {
            val scan by rememberInfiniteTransition(label = "rocketScan").animateFloat(
                initialValue = -0.34f, targetValue = 1.04f,
                animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
                label = "scanY",
            )
            Canvas(Modifier.fillMaxSize()) {
                val bandHeight = size.height * 0.34f
                val top = size.height * scan
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NgbPrimary.copy(alpha = 0.07f),
                            lerp(NgbPrimary, Color.White, 0.5f).copy(alpha = 0.12f),
                            NgbPrimary.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        startY = top,
                        endY = top + bandHeight,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, bandHeight),
                )
            }
        }
        // HUD corner brackets with staggered blink phases (draw-phase state read).
        val hudBlink: State<Float> = if (motionEnabled) {
            rememberInfiniteTransition(label = "rocketHud").animateFloat(
                initialValue = 0.45f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
                label = "hudBlink",
            )
        } else {
            remember { mutableStateOf(0.6f) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val len = 22.dp.toPx()
            val stroke = 2.dp.toPx()
            val w = size.width
            val h = size.height
            // Corners in order: top-left, top-right, bottom-left, bottom-right.
            val x0s = listOf(inset, w - inset, inset, w - inset)
            val y0s = listOf(inset, inset, h - inset, h - inset)
            val dxs = listOf(1f, -1f, 1f, -1f)
            val dys = listOf(1f, 1f, -1f, -1f)
            for (i in 0..3) {
                // Staggered per-corner phase (web .spl-corner delays 0/.4/.8/1.2 s).
                val phaseSway = 0.5f + 0.5f * sin(2.0 * Math.PI * CornerPhases[i].toDouble()).toFloat()
                val a = (if (motionEnabled) hudBlink.value * (0.55f + 0.45f * phaseSway) else 0.6f)
                    .coerceIn(0f, 1f)
                drawLine(
                    color = NgbPrimary.copy(alpha = a),
                    start = Offset(x0s[i], y0s[i]),
                    end = Offset(x0s[i] + dxs[i] * len, y0s[i]),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = NgbPrimary.copy(alpha = a),
                    start = Offset(x0s[i], y0s[i]),
                    end = Offset(x0s[i], y0s[i] + dys[i] * len),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RocketLaunchScene(motionEnabled = motionEnabled)

            Spacer(Modifier.height(20.dp))
            // Glowing wordmark (web .spl-title: text-shadow + slow opacity pulse).
            val glowPulse: Float = if (motionEnabled) {
                val glow by rememberInfiniteTransition(label = "rocketTitle").animateFloat(
                    initialValue = 0.30f, targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
                    label = "titleGlow",
                )
                glow
            } else {
                0.45f
            }
            Text(
                "NEXUSGAMEBOOSTER",
                style = TextStyle(
                    fontSize = 17.sp,
                    letterSpacing = 3.7.sp,
                    fontWeight = FontWeight.Bold,
                    color = NgbOnBackground,
                    shadow = Shadow(color = NgbPrimary.copy(alpha = glowPulse), blurRadius = 28f),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.splash_launching),
                style = MaterialTheme.typography.bodyMedium,
                color = NgbTextMuted,
            )
            Spacer(Modifier.height(16.dp))
            RocketPowerMeter(motionEnabled = motionEnabled)
            Spacer(Modifier.height(18.dp))
            RocketEditionChip()
        }
    }
}

/** Animation state helper: the animated value, or a fixed constant when motion is off. */
@Composable
private fun rocketFloatState(
    motionEnabled: Boolean,
    initialValue: Float,
    targetValue: Float,
    periodMillis: Int,
    label: String,
): State<Float> {
    if (!motionEnabled) {
        return remember { mutableStateOf((initialValue + targetValue) / 2f) }
    }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse),
        label = label,
    )
}

/** The 3D launch scene: engine flare + two counter-rotating orbit rings + the floating app icon. */
@Composable
private fun RocketLaunchScene(motionEnabled: Boolean) {
    // Web .spl-float: rotateZ oscillation around the -8° climb base, gentle lift, 3.4 s.
    val roll = rocketFloatState(motionEnabled, -11f, -5f, 1700, "rocketRoll")
    val lift = rocketFloatState(motionEnabled, 0f, -7f, 1700, "rocketLift")
    // Web .spl-flare: scale .92..1.12, opacity .75..1, 2.1 s.
    val flareScale = rocketFloatState(motionEnabled, 0.92f, 1.12f, 1050, "flareScale")
    val flareAlpha = rocketFloatState(motionEnabled, 0.75f, 1f, 1050, "flareAlpha")
    // Web .spl-orbit-a: 5.4 s clockwise; .spl-orbit--dotted: 8 s counter-clockwise.
    val ringAngle = if (motionEnabled) {
        rememberInfiniteTransition(label = "rocketOrbits").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5400, easing = LinearEasing)),
            label = "ringAngle",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val dotAngle = if (motionEnabled) {
        rememberInfiniteTransition(label = "rocketDots").animateFloat(
            initialValue = 360f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
            label = "dotAngle",
        )
    } else {
        remember { mutableStateOf(45f) }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(268.dp)) {
        // Solid neon ring (web: 216px, 2px stroke, neon 0.55) — flattened for the 3D feel.
        Canvas(
            Modifier
                .size(216.dp)
                .graphicsLayer { scaleY = 0.9f; rotationZ = ringAngle.value },
        ) {
            drawCircle(
                color = NgbPrimary.copy(alpha = 0.55f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        // Dotted lime ring (web: 272px dashed) — counter-rotating.
        Canvas(
            Modifier
                .size(268.dp)
                .graphicsLayer { scaleY = 0.9f; rotationZ = dotAngle.value },
        ) {
            val r = size.minDimension / 2f - 1.dp.toPx()
            repeat(16) { i ->
                val angle = Math.toRadians((i * 22.5).toDouble())
                drawCircle(
                    color = NgbSecondary.copy(alpha = 0.34f),
                    radius = 1.5.dp.toPx(),
                    center = Offset(
                        x = size.width / 2f + (r * cos(angle)).toFloat(),
                        y = size.height / 2f + (r * sin(angle)).toFloat(),
                    ),
                )
            }
        }
        // Engine flare behind the icon (lime → neon radial glow).
        Box(
            Modifier
                .size(190.dp)
                .graphicsLayer {
                    scaleX = flareScale.value
                    scaleY = flareScale.value
                    alpha = flareAlpha.value
                }
                .background(
                    Brush.radialGradient(
                        0f to NgbSecondary.copy(alpha = 0.40f),
                        0.34f to NgbPrimary.copy(alpha = 0.22f),
                        0.68f to Color.Transparent,
                    ),
                    shape = RoundedCornerShape(50),
                ),
        )
        // The app icon itself, floating on its climb (web .spl-rocket: 132px).
        Image(
            painter = painterResource(R.drawable.rocket_splash),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer {
                    rotationZ = roll.value
                    translationY = lift.value.dp.toPx()
                    shadowElevation = 18.dp.toPx()
                },
        )
    }
}

/** Segmented arcade power meter (web .spl-bar): 12 cells filling in a loop, neon → lime. */
@Composable
private fun RocketPowerMeter(motionEnabled: Boolean) {
    val fill = if (motionEnabled) {
        val fillAnim = rememberInfiniteTransition(label = "rocketPower").animateFloat(
            initialValue = 0f, targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "powerFill",
        )
        fillAnim.value
    } else {
        8f
    }
    val lit = fill.toInt().coerceIn(0, 12)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, NgbOnBackground.copy(alpha = 0.14f), RoundedCornerShape(50))
            .background(NgbSurfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(12) { i ->
            Box(
                Modifier
                    .width(9.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        if (i < lit) lerp(NgbPrimary, NgbSecondary, i / 11f)
                        else NgbOnBackground.copy(alpha = 0.08f),
                    ),
            )
        }
    }
}

/** "v<version> · ROCKET EDITION" chip (web .spl-chip). */
@Composable
private fun RocketEditionChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, NgbPrimary.copy(alpha = 0.40f), RoundedCornerShape(50))
            .background(NgbPrimary.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "v" + BuildConfig.VERSION_NAME + " · " + stringResource(R.string.rocket_edition_label),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = NgbPrimary,
        )
    }
}

/**
 * The boot overlay: holds the splash for a beat (web parity: 2.6 s with motion,
 * 0.4 s without), then fades out over 400 ms and is removed. Purely cosmetic —
 * it never delays the app underneath.
 */
@Composable
fun RocketSplashOverlay() {
    val motionEnabled = LocalNgbMotionEnabled.current
    var splashVisible by remember { mutableStateOf(true) }
    LaunchedEffect(motionEnabled) {
        delay(if (motionEnabled) 2600L else 400L)
        splashVisible = false
    }
    AnimatedVisibility(
        visible = splashVisible,
        enter = EnterTransition.None, // already covering the app on the first frame
        exit = fadeOut(tween(400)),
    ) {
        RocketSplash(Modifier.fillMaxSize())
    }
}

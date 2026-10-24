package com.nexus.gamebooster.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbTertiary
import com.nexus.gamebooster.ui.theme.neonGlow
import kotlin.math.sin

// -----------------------------------------------------------------------------
// "NEON ARENA v2" backdrop + loading kit (Phase 11.5), ported from the user's
// NexusGameBooster-Neon-Theme.html mockup:
//  - the page backdrop: three faint radial glows (purple top-left 20%/20%,
//    green bottom-right 80%/80%, cyan dead-center) over the near-black page,
//  - the twinkling star field (60 white dots, opacity 0.2→1 sine twinkle,
//    2–5 s per star, scale 1→1.5 at the twinkle peak),
//  - the entry loader: rotating neon arc + expanding rings around the gradient
//    logo chip, gradient wordmark, and the shine progress bar
//    (.progress-fill: cyan→green gradient sweep).
//
// Motion honesty: the star twinkle and every loader animation are gated by
// [LocalNgbMotionEnabled] — with Reduce motion on (or the system animation
// scale at 0) the backdrop paints STATIC stars at mid opacity and the loader
// shows the same layout with no infinite animation. Nothing here fabricates
// progress: the loader is shown exactly while the gate check runs.
// -----------------------------------------------------------------------------

/** One twinkling star from the mockup's #stars field. */
private data class NgbStar(
    val xFraction: Float,
    val yFraction: Float,
    val phase: Float,
    val periodMillis: Long,
    val radiusDp: Float,
)

private val NgbStarfield: List<NgbStar> = run {
    val rng = kotlin.random.Random(0x00FF9D) // deterministic field — same sky every launch
    List(60) {
        NgbStar(
            xFraction = rng.nextFloat(),
            yFraction = rng.nextFloat(),
            phase = rng.nextFloat(),
            periodMillis = 2000L + (rng.nextFloat() * 3000f).toLong(),
            radiusDp = 0.7f + rng.nextFloat() * 0.8f,
        )
    }
}

private fun DrawScope.drawStarfield(
    densityPxPerDp: Float,
    frameNanos: Long,
    motionEnabled: Boolean,
) {
    val nowMillis = frameNanos / 1_000_000L
    NgbStarfield.forEach { star ->
        val twinkle = if (motionEnabled) {
            val t = (nowMillis % star.periodMillis).toFloat() / star.periodMillis
            0.5f + 0.5f * sin(2.0 * Math.PI * (t + star.phase).toDouble()).toFloat()
        } else {
            0.55f // static mid-oscillation when motion is off
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.2f + 0.8f * twinkle),
            radius = star.radiusDp * densityPxPerDp * (1f + 0.5f * twinkle),
            center = Offset(star.xFraction * size.width, star.yFraction * size.height),
        )
    }
}

/**
 * The full-screen NEON ARENA v2 page backdrop: the three radial glows + the
 * star field. Static glow paint + a draw-phase-only state read for the
 * twinkle, so stars never trigger recomposition — only the draw layer
 * invalidates while the backdrop is on screen.
 */
@Composable
fun NgbNeonBackdrop(modifier: Modifier = Modifier) {
    val motionEnabled = LocalNgbMotionEnabled.current
    var frameNanos by remember { mutableLongStateOf(0L) }
    if (motionEnabled) {
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos = it }
        }
    }
    val pxPerDp = with(LocalDensity.current) { 1.dp.toPx() }
    Canvas(modifier = modifier.fillMaxSize()) {
        // Radial glows (exact mockup body background).
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NgbSecondary.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.2f),
                radius = size.minDimension * 0.75f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NgbPrimary.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.8f, size.height * 0.8f),
                radius = size.minDimension * 0.75f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NgbTertiary.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.minDimension * 0.9f,
            ),
        )
        drawStarfield(
            densityPxPerDp = pxPerDp,
            frameNanos = frameNanos,
            motionEnabled = motionEnabled,
        )
    }
}

/** The gradient logo chip from the mockup top bar (green→cyan rounded square + bolt). */
@Composable
fun NgbLogoChip(modifier: Modifier = Modifier, chipSize: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(chipSize)
            .clip(RoundedCornerShape(chipSize / 3.5f))
            .background(Brush.linearGradient(listOf(NgbPrimary, NgbTertiary)))
            .neonGlow(glowColor = NgbPrimary, shape = RoundedCornerShape(chipSize / 3.5f), blurRadius = 14.dp, glowAlpha = 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = Color(0xFF03150C),
            modifier = Modifier.size(chipSize * 0.6f),
        )
    }
}

/**
 * The app-entry loader (gate "checking" state): logo chip inside two expanding
 * rings + a rotating neon sweep arc, the gradient wordmark, an optional status
 * line and the shine progress bar. Shown exactly while the license session
 * check runs — it never fabricates progress beyond "in progress".
 */
@Composable
fun NgbNeonLoader(modifier: Modifier = Modifier, statusText: String?, loadingContentDescription: String) {
    val motionEnabled = LocalNgbMotionEnabled.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = loadingContentDescription },
    ) {
        NgbNeonBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Two staggered expanding rings (mockup .ring-expand).
                val ringTransition = rememberInfiniteTransition(label = "ngbRings")
                val ring1 by ringTransition.animateFloat(
                    initialValue = 0.9f, targetValue = 1.45f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
                    label = "ring1",
                )
                val ring2 by ringTransition.animateFloat(
                    initialValue = 0.9f, targetValue = 1.45f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart, StartOffset(400)),
                    label = "ring2",
                )
                val ringAlpha1 by ringTransition.animateFloat(
                    initialValue = 0.7f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
                    label = "ringAlpha1",
                )
                val ringAlpha2 by ringTransition.animateFloat(
                    initialValue = 0.7f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart, StartOffset(400)),
                    label = "ringAlpha2",
                )
                // Rings live on a 230 dp canvas so the expanded radius never clips.
                Canvas(Modifier.size(230.dp)) {
                    if (motionEnabled) {
                        drawCircle(
                            color = NgbPrimary.copy(alpha = ringAlpha1),
                            radius = size.minDimension / 2f * ring1,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        drawCircle(
                            color = NgbTertiary.copy(alpha = ringAlpha2),
                            radius = size.minDimension / 2f * ring2,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    } else {
                        // Static halo pair — the loader reads identical without motion.
                        drawCircle(color = NgbPrimary.copy(alpha = 0.35f), radius = size.minDimension / 2f * 1.1f, style = Stroke(width = 2.dp.toPx()))
                        drawCircle(color = NgbTertiary.copy(alpha = 0.22f), radius = size.minDimension / 2f * 1.25f, style = Stroke(width = 1.dp.toPx()))
                    }
                }
                // Rotating neon sweep arc (green→cyan→purple→green).
                val spin = rememberInfiniteTransition(label = "ngbSpin")
                val angle by spin.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                    label = "spinAngle",
                )
                Canvas(Modifier.size(110.dp)) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NgbPrimary, NgbTertiary, NgbSecondary, NgbPrimary)),
                        startAngle = if (motionEnabled) angle else 0f,
                        sweepAngle = 100f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                NgbLogoChip()
            }
            Spacer(Modifier.height(24.dp))
            val wordmarkBrush = Brush.linearGradient(listOf(NgbPrimary, NgbTertiary))
            Text(
                "NEXUS GAME BOOSTER",
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = wordmarkBrush,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            if (statusText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))
            NgbShineBar(width = 180.dp)
        }
    }
}

/**
 * The mockup's shine progress bar (.progress-bar/.progress-fill): dark track,
 * cyan→green gradient fill whose width sweeps in a loop. Static half-fill when
 * motion is disabled. Purely a visual "working" indicator — never a fraction.
 */
@Composable
fun NgbShineBar(modifier: Modifier = Modifier, width: Dp = 180.dp) {
    val motionEnabled = LocalNgbMotionEnabled.current
    val sweep: Float
    if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "ngbShine")
        val animated by transition.animateFloat(
            initialValue = 0.08f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart, StartOffset(200)),
            label = "shineSweep",
        )
        sweep = animated
    } else {
        sweep = 0.55f
    }
    Box(
        modifier = modifier
            .width(width)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black.copy(alpha = 0.4f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(sweep)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(NgbTertiary, NgbPrimary))),
        )
    }
}

/**
 * Small indeterminate neon spinner (button busy states): a rotating arc with
 * the green→cyan sweep. With motion disabled it draws a static 270° arc.
 */
@Composable
fun NgbNeonSpinner(modifier: Modifier = Modifier, spinnerSize: Dp = 18.dp) {
    val motionEnabled = LocalNgbMotionEnabled.current
    val spin = rememberInfiniteTransition(label = "ngbSpinner")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(spinnerSize)) {
        drawArc(
            brush = Brush.sweepGradient(listOf(NgbPrimaryBright, NgbTertiary, NgbPrimaryBright)),
            startAngle = if (motionEnabled) angle else 0f,
            sweepAngle = if (motionEnabled) 110f else 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Mockup entrance animation (`.screen` fadeIn + `.game-card` slideIn): the
 * child fades in and rises 1/3 of its height, staggered ~70 ms per [index].
 * With motion disabled the content renders immediately, unchanged.
 */
@Composable
fun NgbStaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionEnabled = LocalNgbMotionEnabled.current
    if (!motionEnabled) {
        Box(modifier) { content() }
        return
    }
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val delayMillis = (index * 70).coerceAtMost(350)
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(400, delayMillis)) +
            slideInVertically(tween(400, delayMillis)) { it / 3 },
    ) {
        Box { content() }
    }
}

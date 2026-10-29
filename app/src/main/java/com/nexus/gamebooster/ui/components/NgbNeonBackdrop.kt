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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
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
import com.nexus.gamebooster.ui.arcade.ArcadeDriftBlob
import com.nexus.gamebooster.ui.arcade.ArcadeFloorGrid
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbTertiary
import com.nexus.gamebooster.ui.theme.neonGlow
import kotlin.math.sin

// -----------------------------------------------------------------------------
// v1.2.1 "ARCADE HUD" page backdrop + loading kit. The backdrop is the exact
// ambience of the v1.2.1 redesign reference: two drifting radial glow blobs
// (neon top-end, azure bottom-start) above the retro PERSPECTIVE FLOOR GRID
// that scrolls forever — the arcade room the app floats in. The Phase-11.5
// entry loader (rings + arc + wordmark + shine bar) is kept unchanged for the
// session-check gate.
//
// Motion honesty: blob drift, floor scroll and every loader animation are
// gated by [LocalNgbMotionEnabled] — with Reduce motion on (or the system
// animation scale at 0) the backdrop paints STATIC blobs and a STILL grid.
// -----------------------------------------------------------------------------

/**
 * The full-screen ARCADE HUD page backdrop (v1.2.1): two drifting radial glow
 * blobs (neon top-end 420 dp @ 22 %, azure bottom-start 380 dp @ 14 %) plus
 * the perspective floor grid pinned to the bottom 46 % of the screen with a
 * fade-out mask toward the top — the exact `.bg` + `.floor` of the reference.
 * All motion is gated by [LocalNgbMotionEnabled] (static paint when off).
 */
@Composable
fun NgbNeonBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // `.bg::before` — neon blob, top-end.
        ArcadeDriftBlob(
            color = com.nexus.gamebooster.ui.arcade.NgbArcadeColors.Neon.copy(alpha = 0.22f),
            diameter = 420.dp,
            periodMillis = 14_000,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-80).dp),
        )
        // `.bg::after` — azure blob, bottom-start.
        ArcadeDriftBlob(
            color = com.nexus.gamebooster.ui.arcade.NgbArcadeColors.Azure.copy(alpha = 0.14f),
            diameter = 380.dp,
            periodMillis = 18_000,
            reverse = true,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = (-30).dp),
        )
        // `.floor` — the retro perspective grid, bottom 46 %, faded to the top.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.46f)) {
            ArcadeFloorGrid(Modifier.fillMaxSize())
            // Fade mask: opaque page color at the top of the floor fading out.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.background, Color.Transparent),
                        ),
                    ),
            )
        }
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
            tint = Color(0xFF04110A),
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

package com.nexus.gamebooster.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * True when press/transition motion is allowed. NgbApp provides it from the persisted
 * "Reduce motion" setting AND the system's animator duration scale (0 = animations
 * disabled at OS level). Defaults to true so Compose previews behave normally.
 * Consumers MUST respect it: motion is feedback, never information.
 */
val LocalNgbMotionEnabled = staticCompositionLocalOf { true }

/** Pressed scale factor — the kit's exact value (.985); never an overshooting spring. */
private const val PRESSED_SCALE = 0.985f

/** Pressed vertical drop — the kit's translateY(2px) sink on press. */
private val PRESSED_LIFT = 2.dp

/** Duration of the press animation in milliseconds. */
private const val PRESS_ANIMATION_MILLIS = 150

/**
 * Kit press feedback ("NEON ARENA v1", Phase 11.4): the element sinks 2 dp and
 * scales to 98.5% while pressed and returns on release, via a plain [tween]
 * (no spring overshoot). Attach the SAME [MutableInteractionSource] to the
 * underlying clickable/pressable component so the press state is observed —
 * see NgbButton/NgbConnectButton/NgbSecondaryButton for the wired versions.
 *
 * When [enabled] is false (Reduce motion on, or system animations off) the receiver is
 * returned UNCHANGED: no animation, no graphicsLayer, no visual difference.
 */
@Composable
fun Modifier.ngbPressScale(
    source: MutableInteractionSource,
    enabled: Boolean = LocalNgbMotionEnabled.current,
): Modifier {
    if (!enabled) return this
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = PRESS_ANIMATION_MILLIS),
        label = "ngbPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = if (pressed) PRESSED_LIFT.toPx() else 0f
    }
}

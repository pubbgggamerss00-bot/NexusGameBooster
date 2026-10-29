package com.nexus.gamebooster.ui.arcade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbHudFont
import com.nexus.gamebooster.ui.theme.NgbTagFont
import com.nexus.gamebooster.ui.theme.neonGlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// =============================================================================
// v1.2.1 "ARCADE HUD" DESIGN KIT — the Compose port of the v1.2.1 redesign
// reference (Nexus Game Booster.html): chamfered panels with neon gradient
// edges, 3D "lip" buttons, skewed pips, segmented controls, key fields,
// HUD gauges, sparklines, particle bursts, the scan sweep and the retro
// perspective floor grid.
//
// CONTRACTS (kept from every previous phase):
//  - Colors come from [NgbArcadeColors] (the reference CSS tokens) or
//    MaterialTheme roles — screens never hardcode hex values.
//  - Every animation is gated by [LocalNgbMotionEnabled] (Reduce motion +
//    system animator scale): when motion is off the SAME layout renders with
//    static paint only — nothing hides, nothing moves.
//  - Presentation only: no boost logic, no invented numbers.
// =============================================================================

/** Ink text on bright arcade fills (the kit's `--t` on green/lime/azure faces). */
val ArcadeInkText = Color(0xFF04110A)

/** Text color on the danger face (the kit's `.danger { --t:#1C0309 }`). */
val ArcadeOnDanger = Color(0xFF1C0309)

/** Kit-level color tokens — the exact palette of the v1.2.1 reference CSS. */
object NgbArcadeColors {
    val Neon = Color(0xFF3DFF7C)
    val NeonDeep = Color(0xFF16A34A)
    val Lime = Color(0xFFC9FF4A)
    val Azure = Color(0xFF56C9F0)
    val Warn = Color(0xFFFFC46B)
    val Danger = Color(0xFFFF4D6D)
    val Ink = Color(0xFF04110A)
    val Ink2 = Color(0xFF071A10)
    val Card = Color(0xFF0A2314)
    val Card2 = Color(0xFF0F301C)
    val Line = Color(0xFF1E3A2A)
    val Muted = Color(0xFF8FB79D)
}

// -----------------------------------------------------------------------------
// PANELS — the chamfered `.panel` with its gradient EDGE and inset card fill
// -----------------------------------------------------------------------------

enum class ArcadePanelVariant {
    /** Default: neon→azure→neon diagonal edge (`.panel`). */
    PLAIN,

    /** Full neon→azure→lime edge (`.panel.hot`) — hero / selected. */
    HOT,

    /** Amber edge (`.panel.warn`) — honesty/attention cards. */
    WARN,

    /** Danger edge (`.panel.bad`) — error/required-action cards. */
    BAD,

    /** Hairline edge (`.panel.flat`) — embedded reactors/canvas areas. */
    FLAT,
}

/** Edge brush for [ArcadePanel], straight from the reference CSS color stops. */
private fun panelEdgeBrush(variant: ArcadePanelVariant): Brush = when (variant) {
    ArcadePanelVariant.PLAIN -> Brush.linearGradient(
        0f to NgbArcadeColors.Neon.copy(alpha = 0.75f),
        0.45f to NgbArcadeColors.Azure.copy(alpha = 0.18f),
        1f to NgbArcadeColors.Neon.copy(alpha = 0.40f),
    )
    ArcadePanelVariant.HOT -> Brush.linearGradient(
        0f to NgbArcadeColors.Neon,
        0.5f to NgbArcadeColors.Azure,
        1f to NgbArcadeColors.Lime,
    )
    ArcadePanelVariant.WARN -> Brush.linearGradient(
        0f to NgbArcadeColors.Warn,
        1f to NgbArcadeColors.Warn.copy(alpha = 0.20f),
    )
    ArcadePanelVariant.BAD -> Brush.linearGradient(
        0f to NgbArcadeColors.Danger,
        1f to NgbArcadeColors.Danger.copy(alpha = 0.20f),
    )
    ArcadePanelVariant.FLAT -> Brush.linearGradient(0f to NgbArcadeColors.Line, 1f to NgbArcadeColors.Line)
}

/**
 * The chamfered arcade panel: a full-bleed gradient "edge" layer clipped to
 * [NgbChamferShape] with a 1.25 dp inset dark card fill on top — the exact
 * `.panel` / `.panel::before` construction of the reference. Variants recolor
 * the edge only; content always sits on the dark fill.
 */
@Composable
fun ArcadePanel(
    modifier: Modifier = Modifier,
    variant: ArcadePanelVariant = ArcadePanelVariant.PLAIN,
    cut: Dp = 14.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val innerCut = (cut - 2.dp).coerceAtLeast(6.dp)
    val fillBrush = Brush.linearGradient(
        0f to NgbArcadeColors.Card2,
        0.60f to NgbArcadeColors.Card,
    )
    Box(
        modifier = modifier
            .clip(NgbChamferShape(cut))
            .drawBehind { drawRect(panelEdgeBrush(variant)) },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.25.dp)
                .clip(NgbChamferShape(innerCut))
                .drawBehind { drawRect(fillBrush) }
                .padding(contentPadding),
            content = content,
        )
    }
}

// -----------------------------------------------------------------------------
// BUTTONS — the 3D "lip" `.gbtn` and the gradient-outlined `.obtn`
// -----------------------------------------------------------------------------

/** Arcade button tints (the reference's `.gbtn` color variants). */
enum class ArcadeTint(
    val color: Color,
    val light: Color,
    val dark: Color,
    val lip: Color,
    val onColor: Color,
) {
    NEON(
        color = NgbArcadeColors.Neon,
        light = Color(0xFF77FFA3),
        dark = Color(0xFF31CC63),
        lip = NgbArcadeColors.NeonDeep,
        onColor = ArcadeInkText,
    ),
    AZURE(
        color = NgbArcadeColors.Azure,
        light = Color(0xFF88D9F4),
        dark = Color(0xFF46A1C0),
        lip = Color(0xFF1D7F9F),
        onColor = ArcadeInkText,
    ),
    LIME(
        color = NgbArcadeColors.Lime,
        light = Color(0xFFD8FF6D),
        dark = Color(0xFFA1CC3B),
        lip = Color(0xFF86A81C),
        onColor = ArcadeInkText,
    ),
    DANGER(
        color = NgbArcadeColors.Danger,
        light = Color(0xFFFF8999),
        dark = Color(0xFFCC3D57),
        lip = Color(0xFFA3203A),
        onColor = ArcadeOnDanger,
    ),
}

/**
 * The 3D "lip" arcade button (`.gbtn`): a dark [tint]-colored base layer shows
 * as a 4 dp lip under a gradient face; pressing pushes the face DOWN into the
 * lip (translateY 4 dp) for a physical key feel. An animated shine sweep rides
 * across the face every ~3.2 s (motion-gated), and the enabled button carries
 * a soft single-color glow. Minimum touch target 48 dp (40 dp for [small]).
 */
@Composable
fun ArcadeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: ArcadeTint = ArcadeTint.NEON,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = LocalNgbMotionEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "arcadePress",
    )
    val faceShift = if (motion) press else 0f
    val lipDepth = 4.dp
    val faceVertical = if (small) 8.dp else 12.dp
    val faceHorizontal = if (small) 14.dp else 22.dp

    // Shine sweep position (-160% → +260%), only when motion is allowed.
    val shine = rememberInfiniteTransition(label = "shine")
    val shineX by shine.animateFloat(
        initialValue = -1.6f,
        targetValue = 2.6f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "shineX",
    )
    val showShine = enabled && motion

    val glow: Modifier = if (enabled) {
        Modifier.neonGlow(
            glowColor = tint.color,
            shape = NgbChamferShape(12.dp),
            blurRadius = 12.dp,
            glowAlpha = 0.35f,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .heightIn(if (small) 40.dp else 48.dp)
            .clip(NgbChamferShape(12.dp))
            .then(glow)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        // Lip (the dark base the face sinks into).
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 1.dp)
                .clip(NgbChamferShape(12.dp))
                .background(if (enabled) tint.lip else tint.lip.copy(alpha = 0.35f)),
        )
        // Face.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = lipDepth)
                .graphicsLayer { translationY = faceShift * lipDepth.toPx() }
                .clip(NgbChamferShape(12.dp))
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to tint.light,
                            0.45f to tint.color,
                            1f to tint.dark,
                        ),
                    )
                    if (enabled) {
                        // Top inner highlight (the face's glassy edge).
                        drawRect(
                            Color.White.copy(alpha = 0.35f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    }
                },
        ) {
            if (showShine) {
                // The traveling shine band.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(34.dp)
                        .graphicsLayer { translationX = shineX * size.width }
                        .drawBehind {
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            )
                        },
                )
            }
            CompositionLocalProvider(LocalContentColor provides tint.onColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = faceHorizontal, vertical = faceVertical),
                    content = content,
                )
            }
        }
    }
}

/**
 * The outlined gradient-border button (`.obtn`): a tint→transparent→tint
 * diagonal gradient frame with a 1.5 dp ink inset — no 3D lip, subtle lift.
 */
@Composable
fun ArcadeOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: ArcadeTint = ArcadeTint.NEON,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = LocalNgbMotionEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (motion) {
        animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "obtnScale").value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .heightIn(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed && motion) (-2).dp.toPx() else 0f
            }
            .clip(NgbChamferShape(10.dp))
            .background(
                Brush.linearGradient(
                    0f to tint.color,
                    0.40f to Color.Transparent,
                    1f to tint.color,
                ),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(1.5.dp)
                .clip(NgbChamferShape(9.dp))
                .background(NgbArcadeColors.Ink2),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) tint.color else tint.color.copy(alpha = 0.4f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    content = content,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// CHIPS / PIPS / SWITCHES / SEGMENTED / FIELDS
// -----------------------------------------------------------------------------

/** Chamfered arcade chip (`.chip`): 12 % tint fill + 45 % tint border. */
@Composable
fun ArcadeChip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = NgbArcadeColors.Neon,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(NgbChamferShape(6.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.45f), NgbChamferShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = tint,
        )
    }
}

/** Pulsing status dot (`.dot`): 8 dp glowing circle — motion-gated pulse. */
@Composable
fun ArcadeStatusDot(color: Color, modifier: Modifier = Modifier) {
    val motion = LocalNgbMotionEnabled.current
    val pulse = rememberInfiniteTransition(label = "dotPulse")
    val p by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    val glow: Modifier = if (motion) {
        Modifier.neonGlow(color, CircleShape, 8.dp, glowAlpha = p)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .then(glow)
            .background(color, CircleShape),
    )
}

/**
 * The skewed 10-segment stat bars (`.pips`). Filled segments glow in [tint];
 * with [animate] they fill left-to-right over ~0.4 s (motion-gated).
 */
@Composable
fun ArcadePips(
    filled: Int,
    modifier: Modifier = Modifier,
    tint: Color = NgbArcadeColors.Neon,
    total: Int = 10,
    animate: Boolean = false,
) {
    val motion = LocalNgbMotionEnabled.current
    val fillProgress = remember { Animatable(if (animate && motion) 0f else 1f) }
    LaunchedEffect(filled, animate, motion) {
        if (animate && motion) {
            fillProgress.snapTo(0f)
            fillProgress.animateTo(1f, tween((filled * 40 + 200).coerceAtMost(700)))
        } else {
            fillProgress.snapTo(1f)
        }
    }
    val progress = fillProgress.value
    Row(
        modifier = modifier
            .height(8.dp)
            .clearAndSetSemantics { contentDescription = "$filled/$total" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { i ->
            val on = i < filled && progress * total > i
            val pipModifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    // skewX(-20deg) parallelogram, hand-built as a path.
                    val skew = size.height * 0.36f
                    val path = Path().apply {
                        moveTo(skew, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width - skew, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    clipPath(path) {
                        drawRect(if (on) tint else NgbArcadeColors.Line)
                    }
                }
            Box(pipModifier)
        }
    }
}

/** Chamfered arcade switch (`.tgl`) with the square sliding knob. */
@Composable
fun ArcadeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motion = LocalNgbMotionEnabled.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pos by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = 0.55f, stiffness = 640f) else tween(0),
        label = "tglPos",
    )
    val knobColor = when {
        !enabled -> NgbArcadeColors.Muted.copy(alpha = 0.5f)
        checked -> NgbArcadeColors.Neon
        else -> NgbArcadeColors.Muted
    }
    val knobGlow: Modifier = if (checked && enabled) {
        Modifier.neonGlow(NgbArcadeColors.Neon, NgbChamferShape(5.dp), 8.dp, 0.5f)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(width = 54.dp, height = 28.dp)
            .clip(NgbChamferShape(8.dp))
            .background(if (checked) NgbArcadeColors.Neon.copy(alpha = 0.35f) else NgbArcadeColors.Line)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = if (rtl) (4 + 26 * (1 - pos)).dp else (4 + 26 * pos).dp)
                .size(20.dp)
                .then(knobGlow)
                .clip(NgbChamferShape(5.dp))
                .background(knobColor),
        )
    }
}

/** One segmented option: value id + visible label. */
data class ArcadeSegment(val id: String, val label: String)

/** Chamfered segmented control (`.seg`) — the settings language/theme/tab selector. */
@Composable
fun ArcadeSegmented(
    options: List<ArcadeSegment>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(NgbChamferShape(6.dp))
            .background(NgbArcadeColors.Ink2)
            .border(1.dp, NgbArcadeColors.Line, NgbChamferShape(6.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isOn = option.id == selected
            val selectedPaint: Modifier = if (isOn) {
                Modifier
                    .drawBehind {
                        drawRect(Brush.verticalGradient(listOf(NgbArcadeColors.Neon, NgbArcadeColors.NeonDeep)))
                    }
                    .neonGlow(NgbArcadeColors.Neon, NgbChamferShape(6.dp), 10.dp, 0.45f)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(NgbChamferShape(6.dp))
                    .then(selectedPaint)
                    .clickable(role = Role.RadioButton) { onSelect(option.id) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOn) ArcadeInkText else NgbArcadeColors.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Arcade text field (`.field`): chamfered ink container, HUD font, focus glow.
 * [isSearch] renders the RTL-start `.field.srch` body variant instead of the
 * centered LTR key-entry variant.
 */
@Composable
fun ArcadeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    isSearch: Boolean = false,
    maxLength: Int? = null,
    isPassword: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) NgbArcadeColors.Neon else NgbArcadeColors.Line
    var fieldModifier = modifier
        .fillMaxWidth()
        .heightIn(48.dp)
        .clip(NgbChamferShape(10.dp))
        .background(NgbArcadeColors.Ink2)
        .border(1.dp, borderColor, NgbChamferShape(10.dp))
    if (focused) {
        fieldModifier = fieldModifier.neonGlow(NgbArcadeColors.Neon, NgbChamferShape(10.dp), 12.dp, 0.30f)
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            onValueChange(if (maxLength != null) next.take(maxLength) else next)
        },
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = if (isSearch) MaterialTheme.typography.bodyMedium.fontFamily else NgbHudFont,
            fontWeight = FontWeight.Bold,
            fontSize = if (isSearch) 14.sp else 15.sp,
            letterSpacing = if (isSearch) 0.sp else 1.8.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (isSearch) TextAlign.Start else TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Ascii,
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = remember { Brush.verticalGradient(listOf(NgbArcadeColors.Neon, NgbArcadeColors.Lime)) },
        modifier = fieldModifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .semantics { if (hint.isNotEmpty()) contentDescription = hint },
                contentAlignment = if (isSearch) Alignment.CenterStart else Alignment.Center,
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        color = NgbArcadeColors.Muted,
                        fontSize = if (isSearch) 14.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isSearch) TextAlign.Start else TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

// -----------------------------------------------------------------------------
// HEADERS / SECTION LABELS / KV ROWS
// -----------------------------------------------------------------------------

/**
 * Screen header (`.head`): the lime `// CODE` HUD tag, the heavy title and the
 * muted subtitle. The code tag is always LTR HUD/Orbitron text.
 */
@Composable
fun ArcadeHead(
    code: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (code.isNotEmpty()) {
            Text(
                text = "// $code",
                fontFamily = NgbTagFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                letterSpacing = 2.4.sp,
                color = NgbArcadeColors.Lime,
            )
        }
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 30.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}

/** Section label with the fading hairline (`.sec`). */
@Composable
fun ArcadeSectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NgbArcadeColors.Muted,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(NgbArcadeColors.Line, Color.Transparent),
                        ),
                    )
                },
        )
    }
}

/** Dashed key/value row (`.kv`) — values render in the HUD font, LTR. */
@Composable
fun ArcadeKvRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    valueFont: FontFamily = NgbHudFont,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = NgbArcadeColors.Muted, modifier = Modifier.weight(1f))
            Text(
                text = value,
                fontFamily = valueFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = valueColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = NgbArcadeColors.Line,
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                },
        )
    }
}

// -----------------------------------------------------------------------------
// BOOST REACTOR PIECES (Home)
// -----------------------------------------------------------------------------

/**
 * The blinking/done step badge (`.step .b`) — 18 dp chamfered box; `active`
 * blinks the lime border, `done` fills neon and draws the ink checkmark.
 */
@Composable
fun ArcadeStepBadge(active: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalNgbMotionEnabled.current
    val blink = rememberInfiniteTransition(label = "stepBlink")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    val borderAlpha = if (active && motion) alpha else 1f
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(NgbChamferShape(5.dp))
            .background(if (done) NgbArcadeColors.Neon else Color.Transparent)
            .border(
                1.5.dp,
                when {
                    done -> NgbArcadeColors.Neon
                    active -> NgbArcadeColors.Lime.copy(alpha = borderAlpha)
                    else -> NgbArcadeColors.Line
                },
                NgbChamferShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Canvas(Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.80f)
                    lineTo(w * 0.88f, h * 0.22f)
                }
                drawPath(
                    path,
                    color = ArcadeInkText,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** Orbit ring rotation speed presets (the reference's `.reactor` states). */
enum class OrbitSpeed(val durationMillis: Int) {
    IDLE(18_000),
    CHECKING(900),
    RUNNING(2_200),
}

/**
 * The spinning dashed orbit ring (`.reactor .orbit`) — apply to a Canvas that
 * draws the ring; spins slowly when idle, fast when checking/running.
 * Motion-gated: returns the receiver unchanged when motion is off.
 */
@Composable
fun Modifier.arcadeOrbitSpin(speed: OrbitSpeed): Modifier {
    val motion = LocalNgbMotionEnabled.current
    if (!motion) return this
    val spin = rememberInfiniteTransition(label = "orbitSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(speed.durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitAngle",
    )
    return graphicsLayer { rotationZ = angle }
}

// -----------------------------------------------------------------------------
// GAUGES + SPARKLINES (Monitor)
// -----------------------------------------------------------------------------

/**
 * The arcade ring gauge (`.gauge`): dashed track, tinted progress arc with a
 * soft glow, HUD value + unit in the middle, title below. The arc animates to
 * [value]/[max] with a 1.2 s ease — driven by the REAL value passed in.
 */
@Composable
fun ArcadeGauge(
    value: Float,
    max: Float,
    color: Color,
    title: String,
    valueText: String,
    unitText: String,
    modifier: Modifier = Modifier,
) {
    val motion = LocalNgbMotionEnabled.current
    val animated by animateFloatAsState(
        targetValue = (value / max).coerceIn(0f, 1f),
        animationSpec = if (motion) tween(1200, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)) else tween(0),
        label = "gaugeArc",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val r = (size.minDimension - stroke) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = NgbArcadeColors.Line,
                    radius = r,
                    center = c,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                    ),
                )
                val sweep = 360f * animated
                for (i in 3 downTo 1) {
                    drawArc(
                        color = color.copy(alpha = 0.08f * i),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = stroke + i * 2.dp.toPx()),
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                    )
                }
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke),
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    fontFamily = NgbHudFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = unitText,
                    fontFamily = NgbHudFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * The glowing trend sparkline: gradient area fill, dashed grid lines, neon
 * stroke and a pulsing end dot. Renders whatever REAL samples the caller
 * passes — the kit never fabricates data.
 */
@Composable
fun ArcadeSparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = LocalNgbMotionEnabled.current
    val pulse = rememberInfiniteTransition(label = "sparkPulse")
    val dotR by pulse.animateFloat(
        initialValue = 4f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotR",
    )
    val endRadiusDp = if (motion) dotR else 4.5f
    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val endRadius = endRadiusDp.dp.toPx()
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minV = (points.minOrNull() ?: 0f) - 2f
        val maxV = (points.maxOrNull() ?: 1f) + 2f
        val span = (maxV - minV).coerceAtLeast(0.001f)
        fun px(i: Int) = Offset(i * w / (points.size - 1), h - (points[i] - minV) / span * h)
        val grid = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
        for (g in 0..3) {
            val y = g * h / 3f
            drawLine(NgbArcadeColors.Line, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = grid)
        }
        val area = Path().apply {
            moveTo(0f, h)
            points.indices.forEach { i -> val p = px(i); lineTo(p.x, p.y) }
            lineTo(w, h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        val line = Path().apply {
            points.indices.forEach { i -> val p = px(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
        val last = px(points.size - 1)
        drawCircle(color, radius = endRadius, center = last)
    }
}

// -----------------------------------------------------------------------------
// AMBIENCE: floor grid, drift blobs, scan sweep, particle burst
// -----------------------------------------------------------------------------

/**
 * The retro perspective floor (`.floor`): a neon grid plane rotated 58° with
 * an endless forward scroll — the arcade room the app floats in. Static rows
 * when motion is off.
 */
@Composable
fun ArcadeFloorGrid(modifier: Modifier = Modifier) {
    val motion = LocalNgbMotionEnabled.current
    val grid = rememberInfiniteTransition(label = "floor")
    val scroll by grid.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "floorScroll",
    )
    val step = if (motion) scroll else 0f
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationX = 58f
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = 12f * density
            }
            .drawBehind { drawFloorGrid(step) },
    )
}

private fun DrawScope.drawFloorGrid(scrollStep: Float) {
    val cell = 44.dp.toPx()
    val step = scrollStep * cell
    val lineColor = NgbArcadeColors.Neon.copy(alpha = 0.35f)
    var x = -size.width
    while (x < size.width * 2) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        x += cell
    }
    var y = -cell + step
    while (y < size.height + cell) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += cell
    }
}

/**
 * One drifting radial glow blob (`.bg::before/::after`), 14–18 s alternate.
 * Pure ambient paint — sits behind all content, never intercepts touches.
 */
@Composable
fun ArcadeDriftBlob(
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    periodMillis: Int = 14_000,
    reverse: Boolean = false,
) {
    val motion = LocalNgbMotionEnabled.current
    val drift = rememberInfiniteTransition(label = "drift")
    val t by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMillis, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)),
            RepeatMode.Reverse,
        ),
        label = "driftT",
    )
    val tt = if (motion) t else 0.5f
    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                val dir = if (reverse) -1f else 1f
                translationX = dir * (tt - 0.5f) * 80f
                translationY = dir * (tt - 0.5f) * 60f
                val s = 1f + (if (reverse) (1 - tt) else tt) * 0.15f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(color, Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension / 2f,
                    ),
                )
            },
    )
}

/**
 * The navigation scan sweep (`.scan.go`): a thin neon band that sweeps down
 * the whole screen on every route change. Re-triggers by bumping [sweepKey].
 */
@Composable
fun ArcadeScanSweep(sweepKey: Int, modifier: Modifier = Modifier) {
    val motion = LocalNgbMotionEnabled.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(sweepKey) {
        if (!motion || sweepKey == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(750, easing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)))
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    Box(
        modifier = modifier
            .graphicsLayer { translationY = p * size.height }
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            NgbArcadeColors.Neon.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        startY = size.height * 0.4f,
                        endY = size.height * 0.6f,
                    ),
                )
            },
    )
}

/**
 * The celebration particle burst (`.spark` ×12): small glowing squares fly
 * out from the center with rotation and fade. Triggered by bumping [burstKey];
 * a no-op when motion is off.
 */
@Composable
fun ArcadeBurst(burstKey: Int, modifier: Modifier = Modifier, tint: Color = NgbArcadeColors.Neon) {
    val motion = LocalNgbMotionEnabled.current
    if (!motion) return
    val progress = remember { Animatable(1f) }
    val seeds = remember(burstKey) {
        List(12) { i ->
            val rnd = Random(burstKey * 100 + i)
            Triple(
                (Math.PI * 2 * i / 12 + rnd.nextFloat() * 0.5).toFloat(),
                40f + rnd.nextFloat() * 50f,
                rnd.nextFloat() * 90f,
            )
        }
    }
    LaunchedEffect(burstKey) {
        if (burstKey == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = LinearEasing))
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        seeds.forEach { (angle, dist, rot) ->
            val d = dist.dp.toPx() * p
            val x = cx + cos(angle) * d
            val y = cy + sin(angle) * d
            rotate(degrees = rot * p, pivot = Offset(x, y)) {
                drawRect(
                    color = tint.copy(alpha = 1f - p),
                    topLeft = Offset(x - 1.75.dp.toPx(), y - 1.75.dp.toPx()),
                    size = Size(3.5.dp.toPx(), 3.5.dp.toPx()),
                )
            }
        }
    }
}

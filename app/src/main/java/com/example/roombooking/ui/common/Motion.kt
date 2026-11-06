package com.example.roombooking.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Motion tokens used across the app, so every animation shares the same feel. */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 700
}

/**
 * Shrinks the element slightly while a finger is on it and springs back on release.
 * Pass the same [interactionSource] to the clickable component (Card, Button).
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades the element in and slides it up a little the first time it is shown.
 * [index] staggers the first items of a list by 45 ms each. Items further down have no delay,
 * so nothing stays blank while scrolling.
 */
@Composable
fun Modifier.enterAnimation(index: Int = 0, offsetPx: Float = 48f): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index in 1..8) delay(index * 45L)
        progress.animateTo(1f, tween(Motion.MEDIUM + 80, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/** [enterAnimation] for lazy lists: scrolling an item back into view does not replay it. */
@Composable
fun Modifier.enterOnce(seen: MutableSet<Any>, key: Any, index: Int): Modifier {
    val isFirstTime = remember(key) { seen.add(key) }
    return if (isFirstTime) enterAnimation(index) else this
}

/** A value that grows from 0 to [target] when first shown (and re-animates when target changes). */
@Composable
fun animatedProgress(target: Float, delayMillis: Int = 0): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target, tween(Motion.LONG, delayMillis = delayMillis, easing = FastOutSlowInEasing))
    }
    return value.value
}

/** A number that counts up from 0 to [target]. */
@Composable
fun animatedCount(target: Int, delayMillis: Int = 0): Int =
    animatedProgress(target.toFloat(), delayMillis).roundToInt()

/**
 * Background gradient that drifts slowly. It only redraws (no recomposition), but it runs for as
 * long as it is on screen, so keep it to hero areas rather than list items.
 */
@Composable
fun Modifier.flowingGradient(colors: List<Color> = Brand.Hero, durationMillis: Int = 8_000): Modifier {
    val shift by rememberInfiniteTransition(label = "flowingGradient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "flowingGradientShift",
    )
    return this.drawBehind {
        val travel = size.width * 0.6f * shift
        drawRect(
            Brush.linearGradient(
                colors,
                start = Offset(-travel, 0f),
                end = Offset(size.width * 1.6f - travel, size.height),
            ),
        )
    }
}

/** Soft translucent circles drifting behind the content of a hero area. */
@Composable
fun Modifier.floatingOrbs(): Modifier {
    val phase by rememberInfiniteTransition(label = "orbs").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "orbsPhase",
    )
    return this.clipToBounds().drawBehind {
        val unit = size.minDimension
        drawCircle(
            Color.White.copy(alpha = 0.09f),
            radius = unit * 0.55f,
            center = Offset(size.width * (0.88f + 0.05f * sin(phase)), size.height * (0.12f + 0.08f * cos(phase))),
        )
        drawCircle(
            Color.White.copy(alpha = 0.07f),
            radius = unit * 0.32f,
            center = Offset(size.width * (0.12f + 0.06f * cos(phase)), size.height * (0.92f + 0.06f * sin(phase))),
        )
    }
}

/** Skeleton placeholder: a band of light sweeping across while content loads. */
@Composable
fun Modifier.shimmer(shape: Shape = MaterialTheme.shapes.small): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shine = MaterialTheme.colorScheme.surface
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "shimmerSweep",
    )
    return this.clip(shape).drawBehind {
        val x = size.width * sweep
        drawRect(
            Brush.linearGradient(
                listOf(base, shine, base),
                start = Offset(x - size.width, 0f),
                end = Offset(x + size.width, 0f),
            ),
        )
    }
}

/** Gentle up-and-down float, for icons that sit alone on a screen. */
@Composable
fun Modifier.bob(amountPx: Float = 14f): Modifier {
    val y by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -amountPx,
        targetValue = amountPx,
        animationSpec = infiniteRepeatable(tween(1_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY",
    )
    return this.graphicsLayer { translationY = y }
}

/** Springs from nothing to full size when first shown. */
@Composable
fun Modifier.popIn(delayMillis: Int = 0): Modifier {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Shakes sideways whenever [trigger] changes to a non-null value (a failed form submit). */
@Composable
fun Modifier.shakeOn(trigger: Any?, amountPx: Float = 24f): Modifier {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            listOf(-1f, 0.8f, -0.6f, 0.3f, 0f).forEach { x.animateTo(it * amountPx, tween(55)) }
        }
    }
    return this.graphicsLayer { translationX = x.value }
}

/** Small dot with a ring that keeps expanding, used for "waiting" and "unread". */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val ring by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing)),
        label = "pulseRing",
    )
    Box(
        modifier.size(size).drawBehind {
            drawCircle(color.copy(alpha = (1f - ring) * 0.45f), radius = this.size.minDimension * (0.5f + ring * 0.6f))
            drawCircle(color)
        },
    )
}

/** Loading indicator: three dots in the brand colours bouncing in turn. */
@Composable
fun BouncingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Brand.Indigo, Brand.Violet, Brand.Pink).forEachIndexed { i, color ->
            val y by transition.animateFloat(
                initialValue = 0f,
                targetValue = -28f,
                animationSpec = infiniteRepeatable(
                    tween(420, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
                label = "dotY",
            )
            Box(Modifier.size(12.dp).graphicsLayer { translationY = y }.background(color, CircleShape))
        }
    }
}

/** Main action button: gradient fill with the press-scale feedback. */
@Composable
fun BounceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val fill = if (enabled) Brand.Grape else Brand.Grape.map { it.copy(alpha = 0.55f) }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(source, 0.95f)
            .clip(ButtonDefaults.shape)
            .background(Brush.horizontalGradient(fill)),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
        interactionSource = source,
        content = content,
    )
}

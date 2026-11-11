package com.titipong.dinofocus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titipong.dinofocus.Rarity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Shared motion pieces: press feedback, sliding selection, count-up numbers, the hatching egg, sparkles.

/** Shrinks a little while pressed and springs back. Put it first in the chain so the background scales too. */
fun Modifier.pressScale(src: InteractionSource, to: Float = 0.95f): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) to else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Quick side-to-side tremble while [active] (the armed "give up" button). */
fun Modifier.shake(active: Boolean, amount: Dp = 3.dp): Modifier = composed {
    if (!active || LocalReduceMotion.current) return@composed this
    val t by rememberInfiniteTransition(label = "shake").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(60, easing = LinearEasing), RepeatMode.Reverse), label = "x",
    )
    graphicsLayer { translationX = t * amount.toPx() }
}

/** A number that runs up from 0 (and on to any later value). */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 700): Int {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value.roundToInt()
}

/** A 0..1 fraction that grows from 0 to [target], used for bars. */
@Composable
fun rememberGrow(target: Float, durationMs: Int = 600): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value.coerceIn(0f, 1f)
}

/**
 * Equal-width choices with one highlight that slides to the selected choice.
 * [modifier] carries the container look (background, border, padding).
 */
@Composable
fun SegmentedRow(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    height: Dp = 36.dp,
    pill: Shape = RoundedCornerShape(999.dp),
    locked: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val p = LocalPal.current
    BoxWithConstraints(modifier) {
        val n = labels.size
        val tabW = (maxWidth - gap * (n - 1)) / n
        val x by animateDpAsState(
            (tabW + gap) * selected.coerceIn(0, n - 1), spring(dampingRatio = 0.78f, stiffness = 420f), label = "seg",
        )
        Box(Modifier.offset(x = x).width(tabW).height(height).clip(pill).background(p.accent))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val fg by animateColorAsState(
                    when {
                        on -> Color.White
                        locked -> p.muted.copy(alpha = 0.4f)
                        else -> p.muted
                    },
                    tween(200), label = "segfg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .clip(pill)
                        .clickable(enabled = !locked) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

/** Twinkling stars over the area, for legendary dinosaurs. */
@Composable
fun Sparkles(modifier: Modifier = Modifier, color: Color = Color(0xFFFFD54F), count: Int = 5, seed: Int = 3) {
    if (LocalReduceMotion.current) return
    val phase = rememberInfiniteTransition(label = "sparkle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p",
    )
    val pts = remember(seed, count) {
        val r = java.util.Random(seed.toLong())
        List(count) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.6f) }
    }
    Canvas(modifier) {
        pts.forEach { (fx, fy, off, sz) ->
            val k = (phase.value + off) % 1f
            val tw = sin(k * PI).toFloat()
            val a = tw * tw * tw
            if (a > 0.02f) {
                val c = Offset(size.width * (0.06f + 0.88f * fx), size.height * (0.04f + 0.8f * fy))
                val r = size.minDimension * 0.08f * sz * a
                val star = Path()
                for (i in 0 until 8) {
                    val rad = if (i % 2 == 0) r else r * 0.3f
                    val ang = i * PI / 4 - PI / 2
                    val x = c.x + (cos(ang) * rad).toFloat()
                    val y = c.y + (sin(ang) * rad).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, color.copy(alpha = a.coerceIn(0f, 1f)))
                drawCircle(Color.White.copy(alpha = (a * 0.9f).coerceIn(0f, 1f)), radius = r * 0.22f, center = c)
            }
        }
    }
}

/** Spot color of the egg, by how rare the dinosaur inside is. */
fun eggSpot(r: Rarity): Color = when (r) {
    Rarity.COMMON -> Color(0xFF8BC34A)
    Rarity.RARE -> Color(0xFF60A5FA)
    Rarity.EPIC -> Color(0xFF9B5DE5)
    Rarity.LEGENDARY -> Color(0xFFF59E0B)
}

private val EGG_SPOTS = listOf(
    floatArrayOf(-0.45f, 0.26f, 0.060f), floatArrayOf(0.30f, 0.18f, 0.045f), floatArrayOf(0.62f, 0.62f, 0.070f),
    floatArrayOf(-0.62f, 0.72f, 0.055f), floatArrayOf(0.05f, 0.84f, 0.050f), floatArrayOf(-0.08f, 0.10f, 0.030f),
)
private val CRACK_MAIN = listOf(
    -0.97f to 0.50f, -0.62f to 0.42f, -0.38f to 0.54f, -0.10f to 0.43f,
    0.16f to 0.55f, 0.44f to 0.44f, 0.68f to 0.54f, 0.96f to 0.47f,
)
private val CRACK_UP = listOf(-0.10f to 0.43f, -0.02f to 0.31f, -0.16f to 0.22f)
private val CRACK_DOWN = listOf(0.44f to 0.44f, 0.36f to 0.60f, 0.50f to 0.70f)

/** The first [k] (0..1) of a polyline, by length. */
private fun partialPath(pts: List<Offset>, k: Float): Path? {
    if (k <= 0f || pts.size < 2) return null
    var total = 0f
    for (i in 1 until pts.size) total += (pts[i] - pts[i - 1]).getDistance()
    var left = total * k.coerceAtMost(1f)
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val d = (b - a).getDistance()
        if (d <= 0f) continue
        if (left >= d) {
            path.lineTo(b.x, b.y)
            left -= d
        } else {
            val f = left / d
            path.lineTo(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
            break
        }
    }
    return path
}

/**
 * The egg shown while a focus round runs. Cracks spread with [progress] (0..1, taken from the real
 * elapsed time, so it is right again after the screen was off) and it rocks harder near the end.
 * [scared] = trembling while the give-up button is armed.
 */
@Composable
fun DinoEgg(progress: Float, size: Dp, spot: Color, modifier: Modifier = Modifier, scared: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val phase = rememberInfiniteTransition(label = "egg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ph",
    )
    val pr = progress.coerceIn(0f, 1f)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2
        val y0 = s * 0.10f
        val y1 = s * 0.92f
        val h = y1 - y0
        val w = s * 0.33f
        val ym = y0 + h * 0.60f
        val egg = Path().apply {
            moveTo(cx, y0)
            cubicTo(cx + w * 0.62f, y0, cx + w, y0 + h * 0.30f, cx + w, ym)
            cubicTo(cx + w, ym + h * 0.24f, cx + w * 0.58f, y1, cx, y1)
            cubicTo(cx - w * 0.58f, y1, cx - w, ym + h * 0.24f, cx - w, ym)
            cubicTo(cx - w, y0 + h * 0.30f, cx - w * 0.62f, y0, cx, y0)
            close()
        }
        val ph = phase.value
        val rot = when {
            reduce -> 0f
            scared -> sin(ph * 2 * PI * 16).toFloat() * 5f
            ph < 0.3f -> {
                val k = ph / 0.3f
                sin(k * PI * 4).toFloat() * (1.5f + 7f * pr) * (1f - k)
            }
            else -> 0f
        }
        fun pt(f: Pair<Float, Float>) = Offset(cx + w * f.first, y0 + h * f.second)

        drawOval(Color.Black.copy(alpha = 0.18f), topLeft = Offset(cx - w * 0.8f, y1 - s * 0.035f), size = Size(w * 1.6f, s * 0.07f))
        rotate(rot, pivot = Offset(cx, y1)) {
            drawPath(
                egg,
                Brush.radialGradient(
                    listOf(Color(0xFFFFFBEF), Color(0xFFEAD9B0)),
                    center = Offset(cx - w * 0.35f, y0 + h * 0.30f), radius = h * 0.9f,
                ),
            )
            clipPath(egg) {
                EGG_SPOTS.forEach { (fx, fy, r) ->
                    drawCircle(spot.copy(alpha = 0.55f), radius = s * r, center = Offset(cx + w * fx, y0 + h * fy))
                }
                val crack = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val line = Color(0xFF5D4426)
                val main = partialPath(CRACK_MAIN.map(::pt), (pr - 0.05f) / 0.75f)
                if (main != null) {
                    // light from inside just before it hatches
                    if (pr > 0.85f) drawPath(
                        main, Color(0xFFFFE082).copy(alpha = ((pr - 0.85f) / 0.15f * 0.85f).coerceIn(0f, 1f)),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawPath(main, line, style = crack)
                }
                partialPath(CRACK_UP.map(::pt), (pr - 0.45f) / 0.30f)?.let { drawPath(it, line, style = crack) }
                partialPath(CRACK_DOWN.map(::pt), (pr - 0.65f) / 0.30f)?.let { drawPath(it, line, style = crack) }
            }
            drawPath(egg, Color(0xFF8A6B3D).copy(alpha = 0.55f), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

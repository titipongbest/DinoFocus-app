package com.titipong.dinofocus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titipong.dinofocus.AppData
import com.titipong.dinofocus.Dinos
import com.titipong.dinofocus.dateOf
import com.titipong.dinofocus.fmtClock
import com.titipong.dinofocus.hhmm
import com.titipong.dinofocus.thDayMonth
import com.titipong.dinofocus.thWeekday
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private class Hit(val k: Int, val x: Float, val y: Float, val w: Float, val h: Float)

/** New dinos that already had their drop-in on a ground, so it plays once (until the app restarts). */
private val droppedIn = HashSet<String>()
private const val DROP_FRESH_MS = 10 * 60_000L

/** 0..1 with a small overshoot at the end. */
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
}

/** 0..1 that lands, bounces twice and settles. */
private fun bounceOut(x: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
        x < 2.5f / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
    }
}

private fun seededOrder(n: Int, seed: Int): IntArray {
    val a = IntArray(n) { it }
    var s = seed.toLong()
    for (i in n - 1 downTo 1) {
        s = (s * 1103515245L + 12345L) and 0x7fffffffL
        val j = (s % (i + 1)).toInt()
        val t = a[i]; a[i] = a[j]; a[j] = t
    }
    return a
}

private fun DrawScope.poly(pts: List<Offset>, color: Color) {
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    drawPath(path, color)
}

/**
 * Isometric grass block with the given dinos (collection indices) standing on it.
 * Tap a dino to select it; the info card below shows its history.
 */
@Composable
fun GroundView(data: AppData, keys: List<Int>, sel: Int, onSel: (Int) -> Unit, emptyText: String) {
    val p = LocalPal.current
    val density = LocalDensity.current
    val hits = remember { ArrayList<Hit>() }
    val reduce = LocalReduceMotion.current
    // frame clock: only ticks while this ground is on screen and the app is in front
    val t by produceState(0L, reduce) { if (!reduce) while (true) withFrameMillis { value = it } }
    // [0] = frame time when these dinos first appeared (entrance), [1] = selected dino, [2] = when it was tapped
    val marks = remember(keys) { longArrayOf(-1L, -1L, 0L) }
    // a dino earned in the last few minutes falls from the sky the first time its ground is opened
    val dropKey = remember(keys) {
        val nowMs = System.currentTimeMillis()
        keys.lastOrNull { q ->
            val e = data.collection.getOrNull(q) ?: return@lastOrNull false
            nowMs - e.ts in 0..DROP_FRESH_MS && droppedIn.add(e.id.ifBlank { "${e.ts}" })
        } ?: -1
    }
    val art = (Dinos.SPECIES.map { it.id } + "grave").associateWith { androidx.compose.ui.graphics.ImageBitmap.imageResource(dinoArt(it)!!) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wPx = constraints.maxWidth.toFloat()
            val n = keys.size
            val side = min(9, max(4, ceil(sqrt(n * 1.6)).toInt()))
            val maxTw = with(density) { 76.dp.toPx() }
            val tw = floor(min(wPx * 0.94f / side, maxTw))
            val th = tw / 2
            val depth = (tw * 0.4f).roundToInt().toFloat()
            val head = (tw * 1.3f).roundToInt().toFloat()
            val hPx = side * th + depth + head + with(density) { 6.dp.toPx() }
            val hDp = with(density) { hPx.toDp() }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(hDp)
                    .pointerInput(keys) {
                        detectTapGestures { o ->
                            val hit = hits.lastOrNull { o.x >= it.x && o.x <= it.x + it.w && o.y >= it.y && o.y <= it.y + it.h }
                            onSel(hit?.k ?: -1)
                        }
                    }
            ) {
                val col = data.collection
                val cap = side * side
                val first = max(0, n - cap)
                val ox = size.width / 2
                val oy = head
                fun P(i: Float, j: Float) = Offset(ox + (i - j) * tw / 2, oy + (i + j) * th / 2)
                fun down(o: Offset, d: Float) = Offset(o.x, o.y + d)

                // entrance clock for this set of dinos
                // (t stays 0 until the first frame arrives)
                if (marks[0] < 0L && t > 0L) marks[0] = t
                val since = if (reduce) 100_000f else if (marks[0] < 0L) 0f else (t - marks[0]).toFloat()

                // a day with nothing on it is the extinction scene: ash ground, an erupting volcano, falling meteors
                val barren = keys.isEmpty()

                // clouds drifting behind the block
                if (!reduce && !barren) {
                    val cu = max(2f, (tw / 12).roundToInt().toFloat())
                    val cloud = if (p.dark) Color.White.copy(alpha = 0.10f) else Color(0xFFB9A8F5).copy(alpha = 0.30f)
                    for (ci in 0 until 2) {
                        val cw = cu * 11
                        val span = size.width + cw
                        val x = ((t / (70.0 + ci * 35) + ci * span * 0.55) % span).toFloat() - cw
                        val y = head * (0.10f + ci * 0.34f)
                        drawRect(cloud, Offset(x + cu * 2, y), Size(cu * 5, cu))
                        drawRect(cloud, Offset(x, y + cu), Size(cu * 11, cu * 2))
                        drawRect(cloud, Offset(x + cu, y + cu * 3), Size(cu * 8, cu))
                    }
                }

                // soil block
                val L = P(0f, side.toFloat()); val B = P(side.toFloat(), side.toFloat()); val R = P(side.toFloat(), 0f)
                poly(listOf(L, B, down(B, depth), down(L, depth)), Color(0xFF8D6E63))
                poly(listOf(B, R, down(R, depth), down(B, depth)), Color(0xFF6D4C41))
                poly(listOf(L, B, down(B, depth * 0.22f), down(L, depth * 0.22f)), if (barren) Color(0xFF5A4E47) else Color(0xFF689F38))
                poly(listOf(B, R, down(R, depth * 0.22f), down(B, depth * 0.22f)), if (barren) Color(0xFF453B36) else Color(0xFF558B2F))
                for (k in 0 until side * 2) {
                    val f = ((k * 0.618) % 1).toFloat()
                    val onLeft = k % 2 == 0
                    val a = if (onLeft) L else B
                    val b = if (onLeft) B else R
                    val x = a.x + (b.x - a.x) * f
                    val y = a.y + (b.y - a.y) * f + depth * (0.45f + ((k * 0.37f) % 0.4f))
                    drawRect(
                        if (onLeft) Color(0xFFA1887F) else Color(0xFF5D4037),
                        Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat()),
                        Size(max(2f, tw / 14), max(2f, tw / 18)),
                    )
                }
                // grass tiles with tufts and flowers
                val u = max(1f, (tw / 24).roundToInt().toFloat())
                for (i in 0 until side) for (j in 0 until side) {
                    val fi = i.toFloat(); val fj = j.toFloat()
                    poly(
                        listOf(P(fi, fj), P(fi + 1, fj), P(fi + 1, fj + 1), P(fi, fj + 1)),
                        when {
                            barren -> if ((i + j) % 2 == 1) Color(0xFF75675E) else Color(0xFF695C54)
                            (i + j) % 2 == 1 -> Color(0xFF8BC34A)
                            else -> Color(0xFF7CB342)
                        },
                    )
                    if (barren) {
                        // glowing embers scattered over the ash
                        if ((i * 5 + j * 3) % 4 == 1) {
                            val c = P(fi + 0.25f + ((i * 3 + j) % 3) * 0.2f, fj + 0.3f + ((i + j * 2) % 3) * 0.2f)
                            val glow = if (reduce) 0.7f else (0.45f + 0.45f * sin(t / 330.0 + i * 1.9 + j * 2.7)).toFloat()
                            drawRect(Color(0xFFFF7043).copy(alpha = glow.coerceIn(0f, 1f)), c, Size(2 * u, u))
                        }
                        continue
                    }
                    if ((i * 7 + j * 13) % 5 == 0) {
                        val c = P(fi + 0.3f, fj + 0.7f)
                        val g = Color(0xFF558B2F)
                        // the tips lean one pixel with the wind
                        val sway = (sin(t / 700.0 + i * 1.3 + j * 0.7) * 1.2).roundToInt().coerceIn(-1, 1) * u
                        drawRect(g, Offset(c.x, c.y - u), Size(u, u))
                        drawRect(g, Offset(c.x + sway, c.y - 2 * u), Size(u, u))
                        drawRect(g, Offset(c.x + 2 * u, c.y - u), Size(u, u))
                        drawRect(g, Offset(c.x + 2 * u + sway, c.y - 3 * u), Size(u, 2 * u))
                        drawRect(g, Offset(c.x + 4 * u, c.y - u), Size(u, u))
                        drawRect(g, Offset(c.x + 4 * u + sway, c.y - 2 * u), Size(u, u))
                    }
                    if ((i * 11 + j * 5) % 9 == 4) {
                        val c = P(fi + 0.7f, fj + 0.35f)
                        drawRect(if ((i + j) % 3 != 0) Color(0xFFFFF176) else Color(0xFFF8BBD0), c, Size(2 * u, 2 * u))
                    }
                }

                // dinos, back to front
                // random spot per dino, picked from its id: looks scattered, but a dino keeps its spot
                // when more are added. Taken tiles move it to the next free one; a small nudge inside
                // the tile keeps rows from looking lined up.
                data class It(val k: Int, val i: Int, val j: Int, val dx: Float, val dy: Float)
                val taken = BooleanArray(cap)
                val items = (first until n).map { q ->
                    val e = col[keys[q]]
                    val h = (e.id.ifBlank { "${e.ts}" }).hashCode()
                    var slot = Math.floorMod(h * 31 + 17, cap)
                    while (taken[slot]) slot = (slot + 1) % cap
                    taken[slot] = true
                    val r = java.util.Random(h.toLong())
                    It(keys[q], slot % side, slot / side, (r.nextFloat() - 0.5f) * 0.3f, (r.nextFloat() - 0.5f) * 0.3f)
                }.sortedWith(compareBy<It>({ it.i + it.j + it.dx + it.dy }, { it.i }))

                items.firstOrNull { it.k == sel }?.let { s ->
                    val fi = s.i.toFloat(); val fj = s.j.toFloat()
                    val pts = listOf(P(fi, fj), P(fi + 1, fj), P(fi + 1, fj + 1), P(fi, fj + 1))
                    poly(pts, Color.White.copy(alpha = 0.28f))
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, Color.White, style = Stroke(width = with(density) { 2.dp.toPx() }))
                }

                if (barren) {
                    fun rnd(n: Int): Float = (((n * 1103515245 + 12345) ushr 8) and 0xFFFF) / 65535f
                    val bu = max(2f, (tw / 16).roundToInt().toFloat())
                    val mid = side / 2f
                    val c = P(mid, mid)
                    val bw = tw * 1.05f            // half width of the volcano's base
                    val cw = tw * 0.26f            // half width of the crater
                    val vh = tw * 1.05f            // height
                    val top = c.y - vh
                    val pulse = if (reduce) 0.7f else (0.6f + 0.4f * sin(t / 240.0)).toFloat()

                    // meteors: each falls along a diagonal to a tile, then bursts
                    if (!reduce) for (m in 0 until 3) {
                        val period = 2300L + m * 700L
                        val shifted = t + m * 900L
                        val cycle = (shifted / period).toInt()
                        val k = (shifted % period) / period.toFloat()
                        // land near the edges so the volcano stays clear
                        val edge = rnd(cycle * 7 + m * 131) * (side - 1f)
                        val target = when ((cycle + m) % 4) {
                            0 -> P(0.5f, 0.5f + edge)
                            1 -> P(0.5f + edge, 0.5f)
                            2 -> P(side - 0.5f, 0.5f + edge)
                            else -> P(0.5f + edge, side - 0.5f)
                        }
                        val fly = 0.62f
                        if (k < fly) {
                            val f = k / fly
                            val dy = target.y - bu * 2
                            val start = Offset(target.x + dy * 0.55f, bu * 2)
                            val a = (f * 6f).coerceIn(0f, 1f)
                            for (q in 5 downTo 0) {
                                val g = (f - q * 0.035f).coerceAtLeast(0f)
                                val x = start.x + (target.x - start.x) * g * g
                                val y = start.y + (target.y - start.y) * g * g
                                val sz = bu * (2.2f - q * 0.3f)
                                val tail = when (q) { 0 -> Color(0xFFFFF59D); 1 -> Color(0xFFFFB74D); 2 -> Color(0xFFFF7043); else -> Color(0xFFE53935) }
                                drawRect(tail.copy(alpha = a * (1f - q * 0.15f)), Offset(x - sz / 2, y - sz / 2), Size(sz, sz))
                            }
                        } else {
                            val f = (k - fly) / (1f - fly)
                            if (f < 0.6f) {
                                val e = f / 0.6f
                                drawOval(
                                    Color(0xFFFFE082).copy(alpha = 0.55f * (1f - e)),
                                    Offset(target.x - tw * 0.5f * e, target.y - th * 0.5f * e), Size(tw * e, th * e),
                                )
                                for (q in 0 until 6) {
                                    val ang = q * PI / 3 + m
                                    val x = target.x + (cos(ang) * tw * 0.45 * e).toFloat()
                                    val y = target.y + (sin(ang) * th * 0.45 * e).toFloat() - sin(e * PI).toFloat() * tw * 0.3f
                                    drawRect(Color(0xFFFF8A65).copy(alpha = 1f - e), Offset(x - bu / 2, y - bu / 2), Size(bu, bu))
                                }
                            }
                            // a small scorch mark stays until the next one falls
                            drawOval(Color.Black.copy(alpha = 0.25f * (1f - f)), Offset(target.x - tw * 0.16f, target.y - th * 0.16f), Size(tw * 0.32f, th * 0.32f))
                        }
                    }

                    // smoke rising from the crater
                    if (!reduce) for (q in 0 until 5) {
                        val k = ((t / 2600.0 + q / 5.0) % 1.0).toFloat()
                        val sz = bu * (2f + 5f * k)
                        val x = c.x + (sin(k * 5.0 + q * 1.7) * tw * 0.12).toFloat() + k * tw * 0.35f
                        val y = top - k * tw * 0.95f
                        drawRect(Color(0xFF4E4A48).copy(alpha = 0.5f * (1f - k)), Offset(x - sz / 2, y - sz / 2), Size(sz, sz))
                    }

                    // the cone: lit left face, shaded right face
                    val lb = Offset(c.x - bw, c.y); val fb = Offset(c.x, c.y + bw / 2); val rb = Offset(c.x + bw, c.y)
                    val lt = Offset(c.x - cw, top); val ft = Offset(c.x, top + cw / 2); val rt = Offset(c.x + cw, top)
                    poly(listOf(lb, fb, ft, lt), Color(0xFF6D4C41))
                    poly(listOf(fb, rb, rt, ft), Color(0xFF4E342E))
                    // crater and the lava running down both faces
                    val lava = Color(0xFFFF7043)
                    val hot = Color(0xFFFFCA28)
                    poly(listOf(lt, Offset(c.x, top - cw / 2), rt, ft), lerpColor(lava, hot, pulse))
                    for (q in 0 until 2) {
                        val dir = if (q == 0) -1f else 1f
                        val flow = if (reduce) 0.7f else ((t / 1800.0 + q * 0.4) % 1.0).toFloat()
                        val steps = 7
                        for (st in 0 until steps) {
                            val f = st / steps.toFloat()
                            if (f > 0.25f + 0.6f * flow) break
                            val x = c.x + dir * (cw * 0.5f + (bw * 0.55f - cw * 0.5f) * f)
                            val y = top + cw * 0.3f + (vh - cw * 0.2f) * f * 0.92f
                            drawRect(if (st % 2 == 0) lava else hot, Offset(x - bu, y), Size(bu * 2, vh / steps * 0.95f))
                        }
                    }
                    // glow over the crater
                    drawCircle(lava.copy(alpha = 0.22f * pulse), radius = tw * 0.42f, center = Offset(c.x, top))

                    // lava bombs thrown out in arcs
                    if (!reduce) for (q in 0 until 14) {
                        val period = 1500L + (q % 4) * 260L
                        val k = ((t + q * 137L) % period) / period.toFloat()
                        val vx = (rnd(q * 31 + 5) - 0.5f) * 1.9f
                        val vy = 1.9f + rnd(q * 17 + 9) * 1.3f
                        val x = c.x + vx * k * tw
                        val y = top - (vy * k - 2.6f * k * k) * tw
                        if (y > c.y + bw / 2) continue
                        val sz = bu * (if (q % 3 == 0) 2f else 1.4f)
                        drawRect(
                            lerpColor(hot, Color(0xFFD84315), k).copy(alpha = (1.2f - k).coerceIn(0f, 1f)),
                            Offset(x - sz / 2, y - sz / 2), Size(sz, sz),
                        )
                    }
                }

                hits.clear()
                val s = max(1f, floor(tw * 0.9f / 18))
                // a tapped dino jumps once
                if (marks[1] != sel.toLong()) { marks[1] = sel.toLong(); marks[2] = t }
                for ((order, it) in items.withIndex()) {
                    val d = col.getOrNull(it.k) ?: continue
                    // entrance: one after another they pop up from their tile; the newest one drops from the sky
                    val dropping = it.k == dropKey && !reduce
                    val appear = ((since - min(order * 35f, 500f)) / 320f).coerceIn(0f, 1f)
                    val dropK = if (dropping) ((since - 250f) / 800f).coerceIn(0f, 1f) else 1f
                    val grow = if (dropping) 1f else easeOutBack(appear)
                    val fade = if (dropping) (dropK * 5f).coerceIn(0f, 1f) else (appear * 2f).coerceIn(0f, 1f)
                    if (fade <= 0f) continue
                    val fall = (1f - bounceOut(dropK)) * tw * 2.6f
                    val jumpK = if (it.k == sel && !reduce) ((t - marks[2]) / 420f).coerceIn(0f, 1f) else 1f
                    val jump = if (jumpK < 1f) sin(jumpK * PI).toFloat() * tw * 0.32f else 0f
                    val sprite = Dinos.sprite(d.spriteId)
                    val flying = Dinos.get(d.spriteId).shape == "ptero"
                    val w = 18 * s
                    val h = sprite.h * s
                    val c = P(it.i + 0.5f + it.dx, it.j + 0.5f + it.dy)
                    val hop = if (d.dead) 0.0 else max(0.0, sin(t / 520.0 + it.k * 2.1)).pow(6)
                    val lift = (if (flying) tw * 0.35 + sin(t / 400.0 + it.k) * s * 2 else hop * tw * 0.14).roundToInt() +
                        (fall + jump).roundToInt()
                    // the shadow shrinks while the dino is in the air
                    val air = (1f - (fall + jump) / (tw * 2.6f)).coerceIn(0.35f, 1f)
                    val rx = w * (if (flying) 0.22f else 0.32f) * air * grow.coerceIn(0f, 1f)
                    val ry = th * 0.2f * air * grow.coerceIn(0f, 1f)
                    drawOval(Color.Black.copy(alpha = 0.22f * fade), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
                    // dust puffs where the falling dino lands (first touch is at 1/2.75 of the bounce)
                    if (dropping) {
                        val landed = since - 250f - 800f / 2.75f
                        if (landed > 0f && landed < 500f) {
                            val dk = landed / 500f
                            val dust = (if (p.dark) Color(0xFFD7CCC8) else Color(0xFFFFFFFF)).copy(alpha = 0.7f * (1f - dk))
                            val du = max(2f, tw / 16)
                            for (sd in -1..1 step 2) for (q in 0 until 3) {
                                val dx = sd * (tw * 0.18f + dk * tw * (0.22f + q * 0.12f))
                                val dy = -dk * tw * (0.06f + q * 0.05f)
                                val ds = du * (1f + dk * (1.2f - q * 0.25f))
                                drawRect(dust, Offset(c.x + dx - ds / 2, c.y + dy - ds / 2), Size(ds, ds))
                            }
                        }
                    }
                    val top = (c.y - h + s - lift).roundToInt().toFloat()
                    val left = (c.x - w / 2).roundToInt().toFloat()
                    val img = art[d.spriteId]
                    if (img == null) {
                        drawSprite(sprite, left, top, s, flip = it.k % 2 == 1 && !d.dead)
                        hits.add(Hit(it.k, left, top, w, h))
                    } else {
                        // illustrated dino: fit inside about one tile wide / 1.2 tiles tall, feet on the tile
                        val ratio = img.height.toFloat() / img.width
                        var iw = tw * 0.95f
                        var ih = iw * ratio
                        if (ih > tw * 1.2f) { ih = tw * 1.2f; iw = ih / ratio }
                        val il = (c.x - iw / 2).roundToInt()
                        val itop = (c.y - ih * 0.94f - lift).roundToInt()
                        val flip = it.k % 2 == 1 && !d.dead
                        withTransform({
                            if (flip) scale(-1f, 1f, pivot = Offset(c.x, c.y))
                            if (grow != 1f) scale(grow, grow, pivot = Offset(c.x, c.y))
                        }) {
                            drawImage(
                                img,
                                dstOffset = androidx.compose.ui.unit.IntOffset(il, itop),
                                dstSize = androidx.compose.ui.unit.IntSize(iw.roundToInt(), ih.roundToInt()),
                                alpha = fade,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                            )
                        }
                        hits.add(Hit(it.k, il.toFloat(), itop.toFloat(), iw, ih))
                    }
                }

                // one butterfly wanders over the grass
                if (!reduce && !barren) {
                    val bu = max(2f, (tw / 14).roundToInt().toFloat())
                    val wings = listOf(Color(0xFFF48FB1), Color(0xFFFFF176))
                    for (b in 0 until 1) {
                        val a = t / (2300.0 + b * 700) + b * 2.4
                        val bx = ox + (sin(a) * side * tw * 0.30).toFloat()
                        val by = oy + side * th * 0.5f + (cos(a * 1.7 + b) * side * th * 0.22).toFloat() - tw * 0.55f +
                            (sin(t / 260.0 + b) * bu).toFloat()
                        val flap = abs(sin(t / 95.0 + b * 1.3)).toFloat()
                        val ww = bu * (0.6f + 1.6f * flap)
                        drawRect(wings[b], Offset(bx - ww, by - bu), Size(ww, bu * 2))
                        drawRect(wings[b], Offset(bx + bu * 0.5f, by - bu), Size(ww, bu * 2))
                        drawRect(Color(0xFF4E342E), Offset(bx, by - bu * 0.5f), Size(bu * 0.5f, bu * 1.5f))
                    }
                }
            }
        }
        GroundInfo(data, keys, sel, emptyText)
    }
}

@Composable
private fun GroundInfo(data: AppData, keys: List<Int>, sel: Int, emptyText: String) {
    val p = LocalPal.current
    val d = if (sel in keys) data.collection.getOrNull(sel) else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            keys.isEmpty() -> Text(emptyText, color = p.muted, fontSize = 13.sp)
            d == null -> Text(
                buildAnnotatedString {
                    // graves are not living dinos: count them separately
                    val dead = keys.count { data.collection.getOrNull(it)?.dead == true }
                    append("ไดโนเสาร์ ")
                    withStyle(SpanStyle(color = p.accentGlow, fontWeight = FontWeight.Bold)) { append("${keys.size - dead}") }
                    append(" ตัว")
                    if (dead > 0) {
                        append(" · หลุมศพ ")
                        withStyle(SpanStyle(color = p.fg, fontWeight = FontWeight.Bold)) { append("$dead") }
                    }
                    append(" · แตะที่ตัวเพื่อดูประวัติ")
                },
                color = p.muted, fontSize = 13.sp,
            )
            else -> {
                SpriteImage(d.spriteId, 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            d.species.name + if (d.dead) " (ตายแล้ว)" else "",
                            color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (!d.dead) RarityChip(d.species.rarity, small = true)
                    }
                    val dt = dateOf(d.ts)
                    val whenTxt = "${thWeekday(dt)} ${thDayMonth(dt)} ${hhmm(d.ts)}"
                    val studied = if (d.workSec > 0) "เรียน ${d.workSec / 60} นาที (${fmtClock(d.workSec.toLong())})" else "ไม่มีบันทึกเวลา"
                    Text(
                        "${if (d.dead) "ยอมแพ้เมื่อ" else "ได้รับ"} $whenTxt · $studied" + (d.tag?.let { " · $it" } ?: ""),
                        color = p.muted, fontSize = 12.sp,
                    )
                    val same = data.collection.count { it.sp == d.sp && !it.dead }
                    Text(
                        if (d.dead) "เป้าหมาย ${if (d.targetMin > 0) d.targetMin else "-"} นาที" else "ชนิดนี้มีทั้งหมด $same ตัว",
                        color = p.muted, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private const val TWO_PI = 2 * PI

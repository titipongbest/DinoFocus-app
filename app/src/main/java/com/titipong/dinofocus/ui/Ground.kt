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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private class Hit(val k: Int, val x: Float, val y: Float, val w: Float, val h: Float)

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
    val t by produceState(0L) { while (true) withFrameMillis { value = it } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wPx = constraints.maxWidth.toFloat()
            val n = keys.size
            val side = min(9, max(4, ceil(sqrt(n * 1.6)).toInt()))
            val maxTw = with(density) { 76.dp.toPx() }
            val tw = floor(min(wPx * 0.94f / side, maxTw))
            val th = tw / 2
            val depth = (tw * 0.4f).roundToInt().toFloat()
            val head = (tw * 1.05f).roundToInt().toFloat()
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

                // soil block
                val L = P(0f, side.toFloat()); val B = P(side.toFloat(), side.toFloat()); val R = P(side.toFloat(), 0f)
                poly(listOf(L, B, down(B, depth), down(L, depth)), Color(0xFF8D6E63))
                poly(listOf(B, R, down(R, depth), down(B, depth)), Color(0xFF6D4C41))
                poly(listOf(L, B, down(B, depth * 0.22f), down(L, depth * 0.22f)), Color(0xFF689F38))
                poly(listOf(B, R, down(R, depth * 0.22f), down(B, depth * 0.22f)), Color(0xFF558B2F))
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
                        if ((i + j) % 2 == 1) Color(0xFF8BC34A) else Color(0xFF7CB342),
                    )
                    if ((i * 7 + j * 13) % 5 == 0) {
                        val c = P(fi + 0.3f, fj + 0.7f)
                        val g = Color(0xFF558B2F)
                        drawRect(g, Offset(c.x, c.y - 2 * u), Size(u, 2 * u))
                        drawRect(g, Offset(c.x + 2 * u, c.y - 3 * u), Size(u, 3 * u))
                        drawRect(g, Offset(c.x + 4 * u, c.y - 2 * u), Size(u, 2 * u))
                    }
                    if ((i * 11 + j * 5) % 9 == 4) {
                        val c = P(fi + 0.7f, fj + 0.35f)
                        drawRect(if ((i + j) % 3 != 0) Color(0xFFFFF176) else Color(0xFFF8BBD0), c, Size(2 * u, 2 * u))
                    }
                }

                // dinos, back to front
                val order = seededOrder(cap, 7)
                data class It(val k: Int, val i: Int, val j: Int)
                val items = (first until n).map { q ->
                    val slot = order[q - first]
                    It(keys[q], slot % side, slot / side)
                }.sortedWith(compareBy<It>({ it.i + it.j }, { it.i }))

                items.firstOrNull { it.k == sel }?.let { s ->
                    val fi = s.i.toFloat(); val fj = s.j.toFloat()
                    val pts = listOf(P(fi, fj), P(fi + 1, fj), P(fi + 1, fj + 1), P(fi, fj + 1))
                    poly(pts, Color.White.copy(alpha = 0.28f))
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, Color.White, style = Stroke(width = with(density) { 2.dp.toPx() }))
                }

                hits.clear()
                val s = max(1f, floor(tw * 0.9f / 18))
                for (it in items) {
                    val d = col.getOrNull(it.k) ?: continue
                    val sprite = Dinos.sprite(d.spriteId)
                    val flying = Dinos.get(d.spriteId).shape == "ptero"
                    val w = 18 * s
                    val h = sprite.h * s
                    val c = P(it.i + 0.5f, it.j + 0.5f)
                    val hop = if (d.dead) 0.0 else max(0.0, sin(t / 520.0 + it.k * 2.1)).pow(6)
                    val lift = (if (flying) tw * 0.35 + sin(t / 400.0 + it.k) * s * 2 else hop * tw * 0.14).roundToInt()
                    val rx = w * (if (flying) 0.22f else 0.32f)
                    val ry = th * 0.2f
                    drawOval(Color.Black.copy(alpha = 0.22f), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
                    val top = (c.y - h + s - lift).roundToInt().toFloat()
                    val left = (c.x - w / 2).roundToInt().toFloat()
                    drawSprite(sprite, left, top, s, flip = it.k % 2 == 1 && !d.dead)
                    hits.add(Hit(it.k, left, top, w, h))
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

package com.titipong.dinofocus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titipong.dinofocus.AppData
import com.titipong.dinofocus.Dinos
import com.titipong.dinofocus.Entry
import com.titipong.dinofocus.NO_TAG
import com.titipong.dinofocus.Rarity
import com.titipong.dinofocus.Sprite
import com.titipong.dinofocus.hhmm
import kotlin.math.floor

/** Draws a sprite with its top-left at (left, top); px = size of one pixel cell. */
fun DrawScope.drawSprite(sp: Sprite, left: Float, top: Float, px: Float, flip: Boolean = false) {
    for ((x, y, c) in sp.px) {
        val xx = if (flip) sp.w - 1 - x else x
        drawRect(c, Offset(left + xx * px, top + y * px), Size(px + 0.35f, px + 0.35f))
    }
}

/** Square sprite image, vertically centered like the web version's data-URL sprites. */
@Composable
fun SpriteImage(id: String, size: Dp, modifier: Modifier = Modifier) {
    dinoArt(id)?.let { res ->
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), contentDescription = null,
            modifier = modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        return
    }
    val sp = Dinos.sprite(id)
    Canvas(modifier.size(size)) {
        val px = floor(this.size.width / 18f).coerceAtLeast(1f)
        val w = 18 * px
        val h = sp.h * px
        drawSprite(sp, (this.size.width - w) / 2, (this.size.height - h) / 2, px)
    }
}

@Composable
fun RarityChip(r: Rarity, small: Boolean = false) {
    val p = LocalPal.current
    val (bg, fg) = rarityColors(r, p)
    Text(
        r.th,
        color = fg,
        fontSize = if (small) 9.sp else 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = if (small) 6.dp else 12.dp, vertical = if (small) 2.dp else 4.dp),
    )
}

@Composable
fun SubTitle(text: String) {
    Text(text, color = LocalPal.current.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
}

@Composable
fun MutedBox(text: String) {
    val p = LocalPal.current
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center) }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Minutes per tag for a list of rounds, biggest first (same as the web tagBreakdown). */
@Composable
fun TagBreakdown(data: AppData, list: List<Entry>, inner: Boolean = false, longFormat: Boolean = false) {
    val p = LocalPal.current
    val sums = LinkedHashMap<String, Int>()
    list.forEach { if (it.workSec > 0) { val k = it.tag ?: NO_TAG; sums[k] = (sums[k] ?: 0) + it.workSec } }
    val rows = sums.entries.sortedByDescending { it.value }
    if (rows.isEmpty()) return
    val max = rows.first().value.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (inner) p.surface2 else p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { (name, sec) ->
            val c = Color(data.tagColor(name))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(c)
                    Spacer(Modifier.width(6.dp))
                    Text(name, color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (longFormat) fmtDuration(sec) else "${sec / 60} นาที",
                        color = p.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (inner) p.surface else p.surface2)) {
                    Box(Modifier.fillMaxWidth(sec / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
        }
    }
}

@Composable
fun SessionRow(data: AppData, d: Entry, inner: Boolean = false) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (inner) p.surface2 else p.surface)
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpriteImage(d.spriteId, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                d.species.name + if (d.dead) " · ยอมแพ้" else "",
                color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hhmm(d.ts), color = p.muted, fontSize = 11.sp)
                if (d.tag != null) {
                    Text(" · ", color = p.muted, fontSize = 11.sp)
                    Dot(Color(data.tagColor(d.tag)), 6.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(d.tag, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            if (d.workSec > 0) "${d.workSec / 60} นาที" else "0 นาที",
            color = p.accentGlow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pill chip used for tags. */
@Composable
fun TagChip(
    label: String,
    color: Color?,
    selected: Boolean = false,
    ghost: Boolean = false,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
    removable: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(999.dp)
    val bg = when {
        ghost -> Color.Transparent
        selected && color != null -> lerpColor(p.surface2, color, 0.22f)
        else -> p.surface2
    }
    val bc = when {
        selected && color != null -> color
        else -> p.border
    }
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, bc, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (color != null && !ghost) Dot(color)
        Text(
            label,
            color = if (ghost) p.muted else p.fg,
            fontSize = if (small) 12.sp else 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (removable) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(p.red), contentAlignment = Alignment.Center) {
                Text("✕", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** How many of each dinosaur (and graves) in a list of rounds, most first. */
@Composable
fun DinoBreakdown(data: AppData, list: List<Entry>) {
    val p = LocalPal.current
    if (list.isEmpty()) return
    val groups = list.groupBy { it.spriteId }.entries.sortedWith(compareByDescending<Map.Entry<String, List<Entry>>> { it.value.size }.thenBy { it.key == "grave" })
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { (id, items) ->
            val sp = Dinos.get(id)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpriteImage(id, 28.dp)
                Text(sp.name, color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${items.sumOf { it.workSec } / 60} นาที", color = p.muted, fontSize = 12.sp)
                Text("×${items.size}", color = p.accentGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 754 min → "12 ชม. 34 นาที"; under an hour → "34 นาที". */
fun fmtDuration(sec: Int): String {
    val m = sec / 60
    return if (m >= 60) "${m / 60} ชม. ${m % 60} นาที" else "$m นาที"
}

/** Illustrated art for each species (the grave stays a pixel sprite). */
fun dinoArt(id: String): Int? = when (id) {
    "rex" -> com.titipong.dinofocus.R.drawable.dino_rex
    "neck" -> com.titipong.dinofocus.R.drawable.dino_neck
    "trike" -> com.titipong.dinofocus.R.drawable.dino_trike
    "stego" -> com.titipong.dinofocus.R.drawable.dino_stego
    "raptor" -> com.titipong.dinofocus.R.drawable.dino_raptor
    "ankylo" -> com.titipong.dinofocus.R.drawable.dino_ankylo
    "parasaur" -> com.titipong.dinofocus.R.drawable.dino_parasaur
    "allo" -> com.titipong.dinofocus.R.drawable.dino_allo
    "spino" -> com.titipong.dinofocus.R.drawable.dino_spino
    "pachy" -> com.titipong.dinofocus.R.drawable.dino_pachy
    "diplo" -> com.titipong.dinofocus.R.drawable.dino_diplo
    "ptero" -> com.titipong.dinofocus.R.drawable.dino_ptero
    else -> null
}

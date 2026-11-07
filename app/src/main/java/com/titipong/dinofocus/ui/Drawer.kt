package com.titipong.dinofocus.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import com.titipong.dinofocus.thMonth
import com.titipong.dinofocus.thDateYear
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.titipong.dinofocus.AppData
import com.titipong.dinofocus.AppViewModel
import com.titipong.dinofocus.SYNC_FILE
import com.titipong.dinofocus.dateOf
import com.titipong.dinofocus.hhmm
import com.titipong.dinofocus.Entry
import com.titipong.dinofocus.NO_TAG
import com.titipong.dinofocus.NO_TAG_COLOR
import com.titipong.dinofocus.keyToDate
import com.titipong.dinofocus.thDayMonth
import com.titipong.dinofocus.thLongDate
import com.titipong.dinofocus.thWeekday
import com.titipong.dinofocus.todayKey
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun Drawer(vm: AppViewModel, open: Boolean, onClose: () -> Unit) {
    val p = LocalPal.current
    BackHandler(open) { onClose() }
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        val w = min(maxWidth * 0.9f, 380.dp)
        AnimatedVisibility(open, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            var tab by remember { mutableStateOf("today") }
            var view by remember { mutableStateOf(VIEW_DINO) }
            Column(
                Modifier
                    .width(w)
                    .fillMaxHeight()
                    .background(p.bg)
                    .border(1.dp, p.border)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("สรุปข้อมูล", fontFamily = PixelFont, fontSize = 11.sp, color = p.accentGlow, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.surface)
                            .border(1.dp, p.border, RoundedCornerShape(10.dp))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = p.muted, fontSize = 16.sp) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.surface)
                        .border(1.dp, p.border, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("today" to "วันนี้", "history" to "ย้อนหลัง", "sync" to "ซิงค์").forEach { (id, label) ->
                        val active = tab == id
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (active) p.accent else Color.Transparent)
                                .clickable { tab = id }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (active) Color.White else p.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        "today" -> TodayTab(vm, view) { view = it }
                        "history" -> HistoryTab(vm, view) { view = it }
                        else -> SyncTab(vm)
                    }
                }
                CreditLine(Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier) {
    val p = LocalPal.current
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, color = p.accentGlow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = p.muted, fontSize = 10.sp)
    }
}

private const val VIEW_DINO = "dino"
private const val VIEW_TAG = "tag"

/** Switch between looking at the data by dinosaur or by tag. */
@Composable
private fun ViewSwitch(view: String, onView: (String) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        listOf(VIEW_DINO to "🦖 ตามไดโนเสาร์", VIEW_TAG to "🏷️ ตาม tag").forEach { (id, label) ->
            val on = view == id
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) p.accent else Color.Transparent)
                    .clickable { onView(id) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.White else p.muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium) }
        }
    }
}

@Composable
private fun TodayTab(vm: AppViewModel, view: String, onView: (String) -> Unit) {
    val d = vm.data
    val today = todayKey()
    val keys = d.entriesOn(today)
    val list = keys.map { d.collection[it] }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("${d.minutesOn(today)}", "นาทีวันนี้", Modifier.weight(1f))
                StatChip("${d.dailyPomos[today] ?: 0}", "รอบวันนี้", Modifier.weight(1f))
            }
        }
        item { ViewSwitch(view, onView) }
        if (view == VIEW_DINO) {
            item {
                GroundView(d, keys, vm.todaySel, { vm.todaySel = it }, "ยังไม่มีไดโนเสาร์วันนี้ เรียนให้จบรอบเพื่อรับตัวแรก")
            }
            // by dinosaur: ground + the list of today's rounds
            item { SubTitle("รอบที่เรียนวันนี้") }
            if (list.isEmpty()) item { MutedBox("ยังไม่มีรอบวันนี้ กด Start Focus เพื่อเริ่มเรียน") }
            else list.reversed().forEach { e -> item { SessionRow(d, e) } }
        } else {
            // by tag: only the minutes per tag
            item {
                if (list.none { it.workSec > 0 }) MutedBox("ยังไม่มีเวลาที่บันทึกวันนี้") else TagBreakdown(d, list)
            }
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

// ── history ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTab(vm: AppViewModel, view: String, onView: (String) -> Unit) {
    val p = LocalPal.current
    val d = vm.data
    val today = todayKey()
    var tagFilter by remember { mutableStateOf<String?>(null) }
    val byTag = view == VIEW_TAG
    // the tag filter only applies while looking "by tag"
    val histTag = if (byTag) tagFilter else null
    var weekOffset by remember { mutableIntStateOf(0) }
    var openDay by remember { mutableStateOf<String?>(null) }
    var histSel by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun match(e: Entry) = histTag == null || (e.tag ?: NO_TAG) == histTag
    fun indicesOn(k: String) = d.collection.indices.filter { d.collection[it].day == k && match(d.collection[it]) }
    fun minutes(k: String) = if (histTag == null) d.minutesOn(k)
    else indicesOn(k).sumOf { d.collection[it].workSec } / 60

    // tag filter names: saved tags + tags only found in history + "ไม่ระบุ"
    val names = buildList {
        addAll(d.tags.map { it.name })
        d.collection.forEach { e -> e.tag?.let { if (it !in this) add(it) } }
        if (d.collection.any { it.tag == null && it.workSec > 0 }) add(NO_TAG)
    }
    if (tagFilter != null && tagFilter !in names) tagFilter = null

    val dayKeys = (d.dailyPomos.keys + d.dailyWorkSec.keys + d.collection.map { it.day }).toSortedSet().reversed()
        .filter { histTag == null || indicesOn(it).isNotEmpty() }
    if (openDay != null && openDay !in dayKeys) openDay = null
    LaunchedEffect(openDay) { histSel = -1 }

    val days = (6 downTo 0).map { LocalDate.now().minusDays((it + weekOffset * 7).toLong()) }
    val vals = days.map { minutes(it.toString()) }
    val barColor = when (histTag) { null -> null; NO_TAG -> Color(NO_TAG_COLOR); else -> Color(d.tagColor(histTag)) }

    // items before the day list: switch, [tag chips], week nav, chart, breakdown, [all-time title + totals], "ทุกวัน"
    // items before the day list: switch, [tag chips, all-time line], week nav, chart, [dino count], "รายวัน"
    val dayListOffset = if (byTag) 6 else 5
    var chartSel by remember(weekOffset) { mutableStateOf<Int?>(null) }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item { ViewSwitch(view, onView) }
        if (byTag) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("ทั้งหมด", p.accentGlow, selected = tagFilter == null, small = true) { tagFilter = null }
                names.forEach { n ->
                    TagChip(n, Color(d.tagColor(n)), selected = tagFilter == n, small = true) { tagFilter = n }
                }
            }
        }
        // all-time totals as one plain line under the filter
        if (byTag) item {
            val sums = d.collection.filter { match(it) && it.workSec > 0 }.groupBy { it.tag ?: NO_TAG }
                .mapValues { e -> e.value.sumOf { it.workSec } }.entries.sortedByDescending { it.value }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("รวมตลอดกาล", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (sums.isEmpty()) Text("—", color = p.muted, fontSize = 12.sp)
                sums.forEach { (name, sec) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(Color(d.tagColor(name)), 6.dp)
                        Text(" $name ", color = p.muted, fontSize = 12.sp)
                        Text(fmtDuration(sec), color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavBtn("‹", true) { weekOffset++ }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val range = "${thDayMonth(days[0])} – ${thDayMonth(days[6])}"
                    Text(if (weekOffset == 0) "7 วันล่าสุด · $range" else range, color = p.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("รวม ${vals.sum()} นาที" + (histTag?.let { " · $it" } ?: ""), color = p.muted, fontSize = 11.sp)
                }
                NavBtn("›", weekOffset > 0) { if (weekOffset > 0) weekOffset-- }
            }
        }
        item {
            WeekChart(
                days, vals, today, barColor,
                rounds = { k -> if (histTag == null) d.roundsOn(k) else indicesOn(k).count { !d.collection[it].dead } },
                sel = chartSel,
                onSel = { chartSel = it },
                canOpen = { k -> k in dayKeys },
            ) { k ->
                val idx = dayKeys.indexOf(k)
                if (idx >= 0) {
                    openDay = k
                    scope.launch { listState.animateScrollToItem(dayListOffset + idx) }
                }
            }
        }
        if (!byTag) item {
            val inRange = days.flatMap { day -> indicesOn(day.toString()).map { d.collection[it] } }
            DinoBreakdown(d, inRange)
        }
        item { SubTitle("รายวัน") }
        if (dayKeys.isEmpty()) {
            item { MutedBox(if (histTag == null) "ยังไม่มีข้อมูลย้อนหลัง" else "ยังไม่มีรอบที่บันทึกกับ \"$histTag\"") }
        } else {
            dayItems(this, d, dayKeys, today, openDay, byTag, histTag, histSel, ::indicesOn, ::minutes,
                onToggle = { k -> openDay = if (openDay == k) null else k },
                onSel = { histSel = it })
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

private fun dayItems(
    scope: LazyListScope,
    d: AppData,
    dayKeys: List<String>,
    today: String,
    openDay: String?,
    byTag: Boolean,
    histTag: String?,
    histSel: Int,
    indicesOn: (String) -> List<Int>,
    minutes: (String) -> Int,
    onToggle: (String) -> Unit,
    onSel: (Int) -> Unit,
) {
    scope.itemsIndexed(dayKeys, key = { _, k -> k }) { _, k ->
        val p = LocalPal.current
        val idx = indicesOn(k)
        val list = idx.map { d.collection[it] }.reversed()
        val open = openDay == k
        val rounds = if (histTag == null) d.roundsOn(k) else list.size
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface)
                .border(1.dp, if (open) p.accentGlow else p.border, RoundedCornerShape(12.dp))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(k) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (k == today) "วันนี้ · ${thDayMonth(keyToDate(k))}" else thDateYear(keyToDate(k)),
                            color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text("$rounds รอบ", color = p.muted, fontSize = 11.sp)
                    }
                    // the day's total, big
                    Text("${minutes(k)}", color = p.accentGlow, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(" นาที", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (byTag) {
                    // tag colors with minutes, biggest first
                    val sums = list.filter { it.workSec > 0 }.groupBy { it.tag ?: NO_TAG }
                        .mapValues { e -> e.value.sumOf { it.workSec } / 60 }.entries.sortedByDescending { it.value }
                    Row(Modifier.fillMaxWidth().clipToBounds(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sums.isEmpty()) Text("—", color = p.muted)
                        sums.take(4).forEach { (name, min) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(Color(d.tagColor(name)), 7.dp)
                                Text(" $name $min", color = p.muted, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().clipToBounds()) {
                        if (list.isEmpty()) Text("—", color = p.muted)
                        list.take(14).forEach { SpriteImage(it.spriteId, 24.dp) }
                    }
                }
            }
            if (open) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!byTag) GroundView(d, idx, histSel, onSel, "วันนี้ไม่ได้รับไดโนเสาร์")
                    else if (histTag == null) TagBreakdown(d, list, inner = true)
                    if (!byTag) list.forEach { SessionRow(d, it, inner = true) }
                }
            }
        }
    }
}

@Composable
private fun NavBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = p.fg, fontSize = 20.sp) }
}

/** Bar chart for 7 days. Tap or drag a finger across it to read any day (empty days read 0). */
@Composable
private fun WeekChart(
    days: List<LocalDate>,
    vals: List<Int>,
    today: String,
    barColor: Color?,
    rounds: (String) -> Int,
    sel: Int?,
    onSel: (Int?) -> Unit,
    canOpen: (String) -> Boolean,
    onOpen: (String) -> Unit,
) {
    val p = LocalPal.current
    val max = maxOf(30, vals.maxOrNull() ?: 0).toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // readout of the touched day
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel == null) {
                Text("แตะหรือลากนิ้วบนกราฟเพื่อดูแต่ละวัน", color = p.muted, fontSize = 12.sp)
            } else {
                val day = days[sel]
                val k = day.toString()
                Column(Modifier.weight(1f)) {
                    Text(if (k == today) "วันนี้ · ${thDayMonth(day)}" else thDateYear(day), color = p.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${rounds(k)} รอบ", color = p.muted, fontSize = 11.sp)
                }
                Text("${vals[sel]}", color = barColor ?: p.accentGlow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(" นาที", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (canOpen(k)) {
                    Text(
                        "ดู ›", color = p.accentGlow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(k) }.padding(4.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(days) {
                    fun idx(x: Float) = (x / size.width * 7).toInt().coerceIn(0, 6)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onSel(idx(down.position.x))
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (drag != null) {
                            onSel(idx(drag.position.x))
                            horizontalDrag(drag.id) { c -> onSel(idx(c.position.x)); c.consume() }
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, day ->
                val k = day.toString()
                val v = vals[i]
                val isToday = k == today
                val picked = sel == i
                val base = when {
                    v == 0 -> p.border
                    barColor != null -> barColor
                    isToday -> p.accent
                    else -> p.accentGlow
                }
                val fill = if (sel != null && !picked && v > 0) base.copy(alpha = 0.45f) else base
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (picked) p.accent.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(if (v > 0) "$v" else "", color = if (picked) p.fg else p.muted, fontSize = 10.sp, modifier = Modifier.height(14.dp))
                    Box(Modifier.width(26.dp).height(90.dp), contentAlignment = Alignment.BottomCenter) {
                        val frac = (v / max).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(if (frac <= 0f) Modifier.height(2.dp) else Modifier.fillMaxHeight(frac))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(fill)
                        )
                    }
                    // date + month, no weekday
                    Text(
                        "${day.dayOfMonth}", color = if (isToday || picked) p.fg else p.muted, fontSize = 12.sp,
                        fontWeight = if (isToday || picked) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                    )
                    Text(thMonth(day), color = p.muted, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SyncTab(vm: AppViewModel) {
    val p = LocalPal.current
    val d = vm.data
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseBackupFolder(uri)
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item { SubTitle("ข้อมูลในเครื่องนี้") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("${d.minutesOn(todayKey())}", "นาทีวันนี้", Modifier.weight(1f))
                StatChip("${d.collection.count { !it.dead }}", "ไดโนเสาร์", Modifier.weight(1f))
                StatChip("${d.dailyWorkSec.values.sum() / 60}", "นาทีทั้งหมด", Modifier.weight(1f))
            }
        }
        item { SubTitle("ซิงค์หลายเครื่อง") }
        item {
            Text(
                "เลือกโฟลเดอร์เดียวกันในทุกเครื่อง (เช่นโฟลเดอร์ใน Google Drive) แล้วกดซิงค์ " +
                    "รอบที่บันทึกในแต่ละเครื่องจะรวมกัน ไม่นับซ้ำ เช่น เครื่องนี้ 15 นาที อีกเครื่อง 20 นาที ซิงค์แล้วได้ 35 นาทีทั้งสองเครื่อง",
                color = p.muted, fontSize = 12.sp,
            )
        }
        item {
            val name = vm.backupFolderName
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.surface2)
                    .border(1.dp, p.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📁 ", fontSize = 14.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.backupFolder == null) "ยังไม่ได้เลือกโฟลเดอร์" else (name ?: "โฟลเดอร์ที่เลือกไว้"),
                        color = if (vm.backupFolder == null) p.muted else p.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                    if (vm.lastSync > 0) {
                        val t = vm.lastSync
                        Text("ซิงค์ล่าสุด ${thDayMonth(dateOf(t))} ${hhmm(t)}", color = p.muted, fontSize = 11.sp)
                    }
                }
                if (vm.backupFolder != null) {
                    Text(
                        "เปลี่ยน", color = p.accentGlow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { folderLauncher.launch(null) }.padding(4.dp),
                    )
                }
            }
        }
        item {
            BigButton(
                when {
                    vm.syncing -> "กำลังซิงค์…"
                    vm.backupFolder == null -> "เลือกโฟลเดอร์และซิงค์…"
                    else -> "🔄 ซิงค์ตอนนี้"
                },
                BtnStyle.Primary, Modifier.fillMaxWidth().alpha(if (vm.syncing) 0.6f else 1f), small = true,
            ) {
                if (!vm.syncing && !vm.syncNow()) folderLauncher.launch(null)
            }
        }
        item {
            Text(
                "ไฟล์ที่ใช้ซิงค์ชื่อ $SYNC_FILE ในโฟลเดอร์นั้น ทุกครั้งที่ซิงค์จะบันทึกทับไฟล์เดิม " +
                    "ไดโนเสาร์ตัวที่เลือก tag ที่เลือก และระยะเวลาที่ตั้งไว้ของแต่ละเครื่องจะไม่ถูกเปลี่ยน",
                color = p.muted, fontSize = 11.sp,
            )
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

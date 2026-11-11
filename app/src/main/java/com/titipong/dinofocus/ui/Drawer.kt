package com.titipong.dinofocus.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import com.titipong.dinofocus.thMonth
import com.titipong.dinofocus.thDateYear
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

/** Full-screen page for the bottom navigation: today, history or sync. */
@Composable
fun SummaryPage(vm: AppViewModel, page: String, view: String, onView: (String) -> Unit) {
    val p = LocalPal.current
    val title = when (page) { "today" -> "วันนี้"; "history" -> "ประวัติ"; else -> "ซิงค์" }
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = p.fg, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(com.titipong.dinofocus.thHeaderDate(LocalDate.now()), color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                "today" -> TodayTab(vm, view, onView)
                "history" -> HistoryTab(vm, view, onView)
                else -> SyncTab(vm)
            }
        }
        if (page == "sync") CreditLine(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun StatChip(value: Int, label: String, modifier: Modifier) {
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
        // the number runs up from 0 when the page opens
        Text("${rememberCountUp(value)}", color = p.accentGlow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = p.muted, fontSize = 10.sp)
    }
}

private const val VIEW_DINO = "dino"
private const val VIEW_TAG = "tag"

/** Switch between looking at the data by dinosaur or by tag. */
@Composable
private fun ViewSwitch(view: String, onView: (String) -> Unit) {
    val p = LocalPal.current
    SegmentedRow(
        labels = listOf("🦖 ตามไดโนเสาร์", "🏷️ ตาม tag"),
        selected = if (view == VIEW_TAG) 1 else 0,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(999.dp))
            .padding(3.dp),
        height = 34.dp,
    ) { onView(if (it == 1) VIEW_TAG else VIEW_DINO) }
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
                StatChip(d.minutesOn(today), "นาทีวันนี้", Modifier.weight(1f))
                StatChip(d.dailyPomos[today] ?: 0, "รอบวันนี้", Modifier.weight(1f))
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
    // history is always by tag (filter chips); opening a day shows its ground
    val byTag = true
    // the tag filter only applies while looking "by tag"
    val histTag = if (byTag) tagFilter else null
    var weekOffset by remember { mutableIntStateOf(0) }
    // month calendar: the shown month and the tapped day ("yyyy-mm-dd")
    var calMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
    var calDay by remember { mutableStateOf<String?>(null) }
    // history has two modes: the 7-day chart, or the month calendar
    var histMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(HIST_WEEK) }
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
    LaunchedEffect(calDay) { histSel = -1 }
    // the calendar can go back to the month of the first round, never past this month
    val firstMonth = dayKeys.lastOrNull()?.let { java.time.YearMonth.from(keyToDate(it)) } ?: java.time.YearMonth.now()

    val days = (6 downTo 0).map { LocalDate.now().minusDays((it + weekOffset * 7).toLong()) }
    val vals = days.map { minutes(it.toString()) }
    val barColor = when (histTag) { null -> null; NO_TAG -> Color(NO_TAG_COLOR); else -> Color(d.tagColor(histTag)) }

    // items before the day list: switch, [tag chips], week nav, chart, breakdown, [all-time title + totals], "ทุกวัน"
    // items before the day list: tag chips, all-time line, week nav, chart, [chart ground], "รายวัน"
    val dayListOffset = 5
    var chartSel by remember(weekOffset) { mutableStateOf<Int?>(null) }
    var chartGroundSel by remember { mutableIntStateOf(-1) }
    LaunchedEffect(chartSel) { chartGroundSel = -1 }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
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
            SegmentedRow(
                labels = listOf("📊 รายสัปดาห์", "🗓️ รายเดือน"),
                selected = if (histMode == HIST_MONTH) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.surface2)
                    .border(1.dp, p.border, RoundedCornerShape(999.dp))
                    .padding(3.dp),
                height = 34.dp,
            ) { histMode = if (it == 1) HIST_MONTH else HIST_WEEK }
        }
        if (histMode == HIST_WEEK) item(key = "week-nav") {
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
        if (histMode == HIST_WEEK) item(key = "week-chart") {
            WeekChart(
                days, vals, today, barColor,
                rounds = { k -> if (histTag == null) d.roundsOn(k) else indicesOn(k).size },
                sel = chartSel,
                onSel = { chartSel = it },
                canOpen = { k -> k in dayKeys },
            ) { k ->
                calMonth = java.time.YearMonth.from(keyToDate(k))
                calDay = k
            }
        }
        // tapping / dragging on the chart shows that day's dino ground right here
        if (histMode == HIST_WEEK) chartSel?.let { i ->
            item(key = "chart-ground") {
                GroundView(d, indicesOn(days[i].toString()), chartGroundSel, { chartGroundSel = it }, "วันที่เลือกไม่ได้รับไดโนเสาร์")
            }
        }
        if (!byTag) item {
            val inRange = days.flatMap { day -> indicesOn(day.toString()).map { d.collection[it] } }
            DinoBreakdown(d, inRange)
        }
        if (histMode == HIST_MONTH) item(key = "month") {
            MonthCalendar(
                month = calMonth,
                firstMonth = firstMonth,
                today = today,
                selected = calDay,
                color = barColor ?: p.accentGlow,
                minutes = ::minutes,
                onMonth = { calMonth = it; calDay = null },
                onDay = { calDay = it },
            )
        }
        item { Box(Modifier.height(8.dp)) }
    }
    // a day tapped in the month calendar pops up with its totals and dino ground
    calDay?.let { k ->
        DayDialog(d, k, today, histTag, ::indicesOn, ::minutes, histSel, { histSel = it }) { calDay = null }
    }
}

private const val HIST_WEEK = "week"
private const val HIST_MONTH = "month"

private val TH_MONTH_LONG = arrayOf(
    "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
    "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม",
)
private val TH_WEEK_SHORT = listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")

/**
 * One month as a grid (Sunday first). Each day is tinted by how many minutes were studied,
 * today is outlined, and tapping a day opens it below. ‹ › or a sideways swipe change the month.
 */
@Composable
private fun MonthCalendar(
    month: java.time.YearMonth,
    firstMonth: java.time.YearMonth,
    today: String,
    selected: String?,
    color: Color,
    minutes: (String) -> Int,
    onMonth: (java.time.YearMonth) -> Unit,
    onDay: (String) -> Unit,
) {
    val p = LocalPal.current
    val thisMonth = java.time.YearMonth.now()
    val canBack = month > firstMonth
    val canNext = month < thisMonth
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val total = (1..month.lengthOfMonth()).sumOf { minutes(month.atDay(it).toString()) }
        val active = (1..month.lengthOfMonth()).count { minutes(month.atDay(it).toString()) > 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavBtn("‹", canBack) { if (canBack) onMonth(month.minusMonths(1)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${TH_MONTH_LONG[month.monthValue - 1]} ${month.year + 543}", color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("รวม ${rememberCountUp(total)} นาที · เรียน $active วัน", color = p.muted, fontSize = 11.sp)
            }
            NavBtn("›", canNext) { if (canNext) onMonth(month.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth()) {
            TH_WEEK_SHORT.forEach {
                Text(it, color = p.muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        // swipe left / right to change month; the grid slides the same way
        var drag by remember { mutableStateOf(0f) }
        androidx.compose.animation.AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { dir * it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -dir * it / 3 } + fadeOut(tween(160)))
            },
            modifier = Modifier.pointerInput(month, canBack, canNext) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { c, dx -> drag += dx; c.consume() },
                    onDragEnd = {
                        val limit = 60.dp.toPx()
                        if (drag > limit && canBack) onMonth(month.minusMonths(1))
                        else if (drag < -limit && canNext) onMonth(month.plusMonths(1))
                    },
                )
            },
            label = "month",
        ) { m ->
            val first = m.atDay(1)
            val lead = first.dayOfWeek.value % 7            // Sunday = 0
            val len = m.lengthOfMonth()
            val weeks = (lead + len + 6) / 7
            val vals = (1..len).map { minutes(m.atDay(it).toString()) }
            val max = maxOf(30, vals.maxOrNull() ?: 0).toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in 0 until weeks) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until 7) {
                            val dayN = w * 7 + c - lead + 1
                            if (dayN < 1 || dayN > len) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                                continue
                            }
                            val date = m.atDay(dayN)
                            val k = date.toString()
                            val v = vals[dayN - 1]
                            val future = k > today
                            val picked = k == selected
                            val level = if (v > 0) 0.25f + 0.75f * (v / max).coerceIn(0f, 1f) else 0f
                            // cells pop in one after another when the month appears
                            val pop = remember(m) { Animatable(0f) }
                            LaunchedEffect(m) {
                                kotlinx.coroutines.delay((w * 7 + c) * 12L)
                                pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
                            }
                            val sel by androidx.compose.animation.core.animateFloatAsState(
                                if (picked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "calsel",
                            )
                            val bg = if (v > 0) color.copy(alpha = level) else p.surface2
                            val fg = when {
                                future -> p.muted.copy(alpha = 0.4f)
                                level > 0.55f -> Color.White
                                else -> p.fg
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        val sc = (0.6f + 0.4f * pop.value) * (1f + 0.08f * sel)
                                        scaleX = sc; scaleY = sc
                                        alpha = pop.value.coerceIn(0f, 1f)
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .then(
                                        when {
                                            picked -> Modifier.border(2.dp, p.fg, RoundedCornerShape(8.dp))
                                            k == today -> Modifier.border(1.5.dp, p.accentGlow, RoundedCornerShape(8.dp))
                                            else -> Modifier
                                        }
                                    )
                                    .clickable(enabled = !future) { onDay(k) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$dayN", color = fg, fontSize = 12.sp, fontWeight = if (k == today || picked) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                    if (v > 0) Text("$v", color = fg.copy(alpha = 0.85f), fontSize = 8.sp, maxLines = 1, lineHeight = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        // legend: less → more
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("น้อย ", color = p.muted, fontSize = 10.sp)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { a ->
                Box(
                    Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (a == 0f) p.surface2 else color.copy(alpha = a))
                )
            }
            Text(" มาก", color = p.muted, fontSize = 10.sp)
        }
    }
}

/** Pop-up for the day tapped in the calendar: totals, minutes per tag and its dino ground. */
@Composable
private fun DayDialog(
    d: AppData,
    k: String,
    today: String,
    histTag: String?,
    indicesOn: (String) -> List<Int>,
    minutes: (String) -> Int,
    sel: Int,
    onSel: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPal.current
    val idx = indicesOn(k)
    val list = idx.map { d.collection[it] }
    val rounds = if (histTag == null) d.roundsOn(k) ?: 0 else list.size
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.padding(20.dp)) {
            DialogBox(p.accent) {
                DialogTitle(if (k == today) "วันนี้" else thWeekday(keyToDate(k)), p.accentGlow)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(thDateYear(keyToDate(k)), color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("$rounds รอบ" + (histTag?.let { " · $it" } ?: ""), color = p.muted, fontSize = 12.sp)
                        }
                        Text("${rememberCountUp(minutes(k))}", color = p.accentGlow, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(" นาที", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    GroundView(d, idx, sel, onSel, "วันนี้ไม่ได้รับไดโนเสาร์")
                    if (histTag == null && list.any { it.workSec > 0 }) TagBreakdown(d, list, inner = true)
                }
                OutlineButton("ปิด", Modifier.fillMaxWidth(), onClose)
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
                val fill by animateColorAsState(
                    if (sel != null && !picked && v > 0) base.copy(alpha = 0.45f) else base, tween(180), label = "bar",
                )
                // bars rise from the ground one after another, then follow the data when the week or tag changes
                val target = (v / max).coerceIn(0f, 1f)
                val grow = remember { Animatable(0f) }
                var first by remember { mutableStateOf(true) }
                LaunchedEffect(target) {
                    if (first) {
                        first = false
                        kotlinx.coroutines.delay(i * 45L)
                    }
                    grow.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 240f))
                }
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
                        val frac = grow.value.coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(if (target <= 0f && frac <= 0.02f) Modifier.height(2.dp) else Modifier.fillMaxHeight(frac))
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
                StatChip(d.minutesOn(todayKey()), "นาทีวันนี้", Modifier.weight(1f))
                StatChip(d.collection.count { !it.dead }, "ไดโนเสาร์", Modifier.weight(1f))
                StatChip(d.dailyWorkSec.values.sum() / 60, "นาทีทั้งหมด", Modifier.weight(1f))
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
                    vm.syncInfo?.let { Text(it, color = p.muted, fontSize = 11.sp) }
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
                "แอพสร้างโฟลเดอร์ \"DinoFocus-backup\" ไว้ในโฟลเดอร์ที่เลือก แต่ละเครื่องมีไฟล์ของตัวเองในนั้น " +
                    "(DinoFocus-sync-ชื่อเครื่อง.json) และอ่านไฟล์ของทุกเครื่อง " +
                    "จึงไม่มีเครื่องไหนเขียนทับข้อมูลของอีกเครื่อง แอพซิงค์เองตอนเปิดแอพและหลังจบทุกรอบ " +
                    "Google Drive อาจใช้เวลา 1–2 นาทีกว่าไฟล์จะไปถึงอีกเครื่อง " +
                    "ไดโนเสาร์ตัวที่เลือก tag ที่เลือก และระยะเวลาที่ตั้งไว้ของแต่ละเครื่องจะไม่ถูกเปลี่ยน",
                color = p.muted, fontSize = 11.sp,
            )
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

package com.titipong.dinofocus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titipong.dinofocus.AppViewModel
import com.titipong.dinofocus.CANCEL_MS
import com.titipong.dinofocus.Dinos
import com.titipong.dinofocus.MODE_SHORT
import com.titipong.dinofocus.MODE_WORK
import com.titipong.dinofocus.SliderCfg
import com.titipong.dinofocus.fmtClock
import com.titipong.dinofocus.thHeaderDate
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.ceil

@Composable
fun MainScreen(vm: AppViewModel) {
    val p = LocalPal.current
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var petPicker by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(onMenu = { drawerOpen = true })
            ModeTabs(vm)
            TimerCard(vm, onPet = { petPicker = true }, onTag = { tagDialog = true })
        }

        Drawer(vm, open = drawerOpen, onClose = { drawerOpen = false })

        vm.result?.let { ResultDialog(vm, it) }
        if (vm.successOpen) SuccessDialog(vm)
        if (petPicker) PetPickerDialog(vm) { petPicker = false }
        if (tagDialog) TagDialog(vm) { tagDialog = false }

        ToastHost(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(onMenu: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "DINO\nPOMODORO", fontFamily = PixelFont, fontSize = 12.sp, lineHeight = 18.sp,
            color = p.accentGlow, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f),
        )
        Text(thHeaderDate(LocalDate.now()), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.surface)
                .border(1.dp, p.border, RoundedCornerShape(10.dp))
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { Box(Modifier.width(16.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(p.fg)) }
            }
        }
    }
}

@Composable
private fun ModeTabs(vm: AppViewModel) {
    val p = LocalPal.current
    val locked = vm.inSession
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(MODE_WORK to "🦖 ทำงาน", MODE_SHORT to "☕ พักสั้น").forEach { (mode, label) ->
            val active = vm.data.mode == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) p.accent else Color.Transparent)
                    .clickable(enabled = !locked) { vm.selectModeTab(mode) }
                    .alpha(if (locked && !active) 0.4f else 1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Color.White else p.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerCard(vm: AppViewModel, onPet: () -> Unit, onTag: () -> Unit) {
    val p = LocalPal.current
    val d = vm.data
    val work = d.mode == MODE_WORK
    val el = vm.elapsedMs
    val tgt = vm.targetMs
    val over = vm.inOvertime
    val started = el > 0
    val running = vm.isRunning
    val inSession = vm.inSession
    val shape = RoundedCornerShape(20.dp)
    val glow = if (over) p.yellow.copy(alpha = 0.18f) else p.accent.copy(alpha = 0.12f)

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(shape)
            .background(p.surface)
            .background(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = 700f))
            .border(1.dp, if (over) lerpColor(p.border, p.yellow, 0.5f) else p.border, shape)
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (work) {
            // pet: tap to choose the species (locked during a round)
            val bob = if (running && (vm.now / 450) % 2 == 0L) (-5).dp else 0.dp
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !inSession, onClick = onPet)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage(d.pet, 104.dp, Modifier.offset(y = bob))
                Row {
                    Text(Dinos.get(d.pet).name, color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (!inSession) Text(" · แตะเพื่อเลือก", color = p.muted, fontSize = 12.sp)
                }
            }
            // single tag chip; tap opens the tag dialog
            val t = d.tags.firstOrNull { it.name == d.tag }
            if (t != null) {
                TagChip(t.name + if (inSession) "" else "  ▼", Color(t.color), selected = true, enabled = !inSession, onClick = onTag)
            } else {
                TagChip("＋ เลือก tag" + if (inSession) "" else "  ▼", null, ghost = true, enabled = !inSession, onClick = onTag)
            }
        }

        Ring(vm, el, tgt, over, started)

        if (over) Text(if (vm.atCap) "ครบ 90 นาทีแล้ว · กดเสร็จสิ้นเพื่อบันทึก" else "ไดโนเสาร์ใหม่สำเร็จ · นับเวลาต่อได้ถึง 90 นาที", color = p.yellow, fontSize = 12.sp, textAlign = TextAlign.Center)

        // duration slider (locked once started)
        val range = SliderCfg.range(d.mode)
        val minutes = d.minutesFor(d.mode)
        Column(Modifier.fillMaxWidth().alpha(if (started) 0.45f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ระยะเวลา: ", color = p.muted, fontSize = 12.sp)
                Text("$minutes", color = p.accentGlow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(" นาที", color = p.muted, fontSize = 12.sp)
            }
            Slider(
                value = minutes.toFloat(),
                onValueChange = { vm.setDuration((Math.round(it / 5f) * 5)) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / 5 - 1,
                enabled = !started,
                colors = SliderDefaults.colors(
                    thumbColor = p.accentGlow, activeTrackColor = p.accentGlow, inactiveTrackColor = p.border,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    disabledThumbColor = p.accentGlow, disabledActiveTrackColor = p.accentGlow,
                    disabledInactiveTrackColor = p.border, disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("${range.first} นาที", color = p.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("${range.last} นาที", color = p.muted, fontSize = 10.sp)
            }
        }

        if (work) {
            val name = Dinos.get(d.pet).name
            val (label, style, hint) = when {
                !started -> Triple("Start Focus", BtnStyle.Primary, "")
                over -> Triple("เสร็จสิ้น", BtnStyle.Stop, "")
                el < CANCEL_MS -> Triple(
                    "ยกเลิก (${ceil((CANCEL_MS - el) / 1000.0).toInt()})", BtnStyle.Cancel,
                    "ยกเลิกได้ภายใน 10 วินาที หลังจากนั้นต้องยอมแพ้เท่านั้น",
                )
                vm.giveUpArmed -> Triple("แตะอีกครั้งเพื่อยอมแพ้", BtnStyle.Confirm, "$name จะตายและกลายเป็นหลุมศพ")
                else -> Triple("ยอมแพ้", BtnStyle.GiveUp, "อดทนไว้ $name กำลังรออยู่")
            }
            val ctx = LocalContext.current
            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            BigButton(label, style, Modifier.widthIn(min = 220.dp)) {
                if (over) { vm.finishSuccess(goBreak = false); return@BigButton }
                if (!started && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.onFocusButton()
            }
            Text(hint, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.offset(y = (-10).dp))
        } else {
            if (!started) {
                BigButton("☕ เริ่มพัก", BtnStyle.Break, Modifier.widthIn(min = 220.dp)) { vm.breakStart() }
            } else {
                Row(Modifier.fillMaxWidth().widthIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("ยกเลิก", BtnStyle.Cancel, Modifier.weight(1f), small = true) { vm.breakCancel() }
                    BigButton("Start Focus", BtnStyle.Primary, Modifier.weight(1f), small = true) { vm.breakToFocus() }
                }
            }
        }
    }
}

@Composable
private fun Ring(vm: AppViewModel, el: Long, tgt: Long, over: Boolean, started: Boolean) {
    val p = LocalPal.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    val work = vm.data.mode == MODE_WORK
    val frac = if (over) 1f else (el.toFloat() / tgt).coerceIn(0f, 1f)
    val ringColor = when {
        over -> p.yellow
        started -> if (work) WORK_COLOR else SHORT_COLOR
        else -> p.border
    }
    val text = if (over) "+" + fmtClock((el - tgt) / 1000) else fmtClock(ceil((tgt - el) / 1000.0).toLong())
    val sub = if (over) "รวม " + fmtClock(el / 1000) else ""
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val sw = 8.dp.toPx()
            val r = size.width * 88f / 200f
            val tl = Offset(center.x - r, center.y - r)
            drawCircle(p.border, radius = r, style = Stroke(sw))
            drawArc(
                ringColor.copy(alpha = if (over) pulse else 1f), startAngle = -90f, sweepAngle = 360f * frac,
                useCenter = false, topLeft = tl, size = Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (over) "เกินเวลา" else if (work) "โฟกัส" else "พักสั้น",
                color = p.muted, fontSize = 11.sp, letterSpacing = 0.8.sp,
            )
            Text(
                text,
                color = if (over) p.yellow else p.fg,
                fontSize = when { text.length >= 7 -> 34.sp; text.length == 6 -> 42.sp; else -> 52.sp },
                fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
            )
            Text(sub, color = p.muted, fontSize = 10.sp)
        }
    }
}

enum class BtnStyle { Primary, Cancel, GiveUp, Confirm, Stop, Break }

@Composable
fun BigButton(label: String, style: BtnStyle, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(999.dp)
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> p.accent to Color.White
        BtnStyle.Cancel -> p.surface2 to p.fg
        BtnStyle.GiveUp -> Color.Transparent to p.red
        BtnStyle.Confirm -> p.red to Color.White
        BtnStyle.Stop -> p.yellow to Color.White
        BtnStyle.Break -> p.green to Color.White
    }
    val borderMod = when (style) {
        BtnStyle.Cancel -> Modifier.border(1.dp, p.border, shape)
        BtnStyle.GiveUp -> Modifier.border(2.dp, p.red.copy(alpha = 0.6f), shape)
        else -> Modifier
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 32.dp, vertical = if (small) 16.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = fg, fontWeight = FontWeight.Bold,
            fontSize = when { small -> 16.sp; style == BtnStyle.GiveUp -> 16.sp; else -> 18.sp },
        )
    }
}

@Composable
private fun ToastHost(vm: AppViewModel, modifier: Modifier) {
    val p = LocalPal.current
    val msg = vm.toast
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(msg) {
        if (msg != null) {
            text = msg.text
            visible = true
            delay(3200)
            visible = false
        }
    }
    AnimatedVisibility(
        visible, modifier.navigationBarsPadding().padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        enter = slideInVertically(tween(300, easing = LinearEasing)) { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface)
                .border(1.dp, p.accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) { Text(text, color = p.fg, fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
}

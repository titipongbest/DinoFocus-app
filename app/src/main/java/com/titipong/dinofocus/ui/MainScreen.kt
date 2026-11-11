package com.titipong.dinofocus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import android.view.HapticFeedbackConstants
import com.titipong.dinofocus.Rarity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
    var page by rememberSaveable { mutableStateOf(PAGE_TIMER) }
    var view by rememberSaveable { mutableStateOf("dino") }
    var petPicker by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    BackHandler(page != PAGE_TIMER) { page = PAGE_TIMER }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                // pages fade and slide a little towards the side their tab is on
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val dir = if (PAGE_ORDER.indexOf(targetState) >= PAGE_ORDER.indexOf(initialState)) 1 else -1
                        (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260)) { dir * it / 10 }) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -dir * it / 10 })
                    },
                    label = "page",
                ) { pg ->
                    if (pg == PAGE_TIMER) {
                        // one screen, no scrolling: the timer card takes whatever height is left and scales to it
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Header()
                            ModeTabs(vm)
                            TimerCard(vm, onPet = { petPicker = true }, onTag = { tagDialog = true }, modifier = Modifier.weight(1f))
                            CreditLine()
                        }
                    } else {
                        SummaryPage(vm, pg, view) { view = it }
                    }
                }
            }
            BottomBar(vm, page) { page = it }
        }

        vm.result?.let { ResultDialog(vm, it) }
        if (vm.successOpen) SuccessDialog(vm)
        if (petPicker) PetPickerDialog(vm) { petPicker = false }
        if (tagDialog) TagDialog(vm) { tagDialog = false }

        ToastHost(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp))
        FxOverlay(vm)
    }
}

@Composable
private fun Header() {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "DINO\nPOMODORO", fontFamily = PixelFont, fontSize = 12.sp, lineHeight = 18.sp,
            color = p.accentGlow, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f),
        )
        Text(thHeaderDate(LocalDate.now()), color = p.muted, fontSize = 12.sp)
    }
}

private const val PAGE_TIMER = "timer"
private val PAGE_ORDER = listOf(PAGE_TIMER, "today", "history", "sync")

/** Bottom navigation: timer · today · history · sync. The timer tab shows the live time while a round runs. */
@Composable
private fun BottomBar(vm: AppViewModel, page: String, onPage: (String) -> Unit) {
    val p = LocalPal.current
    val running = vm.isRunning
    val timerLabel = if (running && page != PAGE_TIMER) {
        val el = vm.elapsedMs
        val tgt = vm.targetMs
        if (vm.inOvertime) "+" + fmtClock((el - tgt) / 1000) else fmtClock(ceil((tgt - el) / 1000.0).toLong())
    } else "จับเวลา"
    val items = listOf(
        Triple(PAGE_TIMER, if (vm.data.mode == MODE_WORK) "⏱️" else "☕", timerLabel),
        Triple("today", "🏞️", "วันนี้"),
        Triple("history", "📊", "ประวัติ"),
        Triple("sync", "🔄", "ซิงค์"),
    )
    Column(Modifier.fillMaxWidth().background(p.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (id, icon, label) ->
                val active = page == id
                // the highlight widens into place and the icon pops when a tab becomes active
                val k by animateFloatAsState(if (active) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "nav")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPage(id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier
                            .drawBehind {
                                val w = size.width * (0.4f + 0.6f * k)
                                drawRoundRect(
                                    p.accent.copy(alpha = 0.18f * k),
                                    topLeft = Offset((size.width - w) / 2, 0f),
                                    size = Size(w, size.height),
                                    cornerRadius = CornerRadius(size.height / 2),
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, fontSize = 18.sp, modifier = Modifier.graphicsLayer {
                            val sc = 1f + 0.14f * k
                            scaleX = sc; scaleY = sc
                        })
                        if (id == PAGE_TIMER && running && !active) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 12.dp).size(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (vm.inOvertime) p.yellow else p.green)
                            )
                        }
                    }
                    Text(
                        label,
                        color = if (active) p.accentGlow else if (id == PAGE_TIMER && running) p.fg else p.muted,
                        fontSize = 11.sp,
                        fontWeight = if (active || (id == PAGE_TIMER && running)) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTabs(vm: AppViewModel) {
    val p = LocalPal.current
    // the highlight position comes straight from the current mode, so it can never be left stale
    SegmentedRow(
        labels = listOf("🦖 ทำงาน", "☕ พักสั้น"),
        selected = if (vm.data.mode == MODE_WORK) 0 else 1,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(6.dp),
        gap = 8.dp,
        height = 36.dp,
        pill = RoundedCornerShape(8.dp),
        locked = vm.inSession,
    ) { vm.selectModeTab(if (it == 0) MODE_WORK else MODE_SHORT) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerCard(vm: AppViewModel, onPet: () -> Unit, onTag: () -> Unit, modifier: Modifier = Modifier) {
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

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(shape)
            .background(p.surface)
            .background(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = 700f))
            .border(1.dp, if (over) lerpColor(p.border, p.yellow, 0.5f) else p.border, shape)
    ) {
    // scale pet, ring and text to the card height (1 = roomy phone, smaller on short screens)
    val s = ((maxHeight / 640.dp) * 1.08f).coerceIn(0.55f, 1f)
    val cardH = maxHeight
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = cardH)
            .padding(horizontal = 20.dp, vertical = (14 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (work) {
            // pet: tap to choose the species (locked during a round).
            // While the round runs it is an egg that cracks with the progress; it hatches at the target.
            val hatching = inSession && !over
            val species = Dinos.get(d.pet)
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !inSession, onClick = onPet)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Crossfade(targetState = hatching, animationSpec = tween(350), label = "pet") { egg ->
                    if (egg) {
                        DinoEgg(
                            progress = el.toFloat() / tgt.coerceAtLeast(1L), size = (104 * s).dp,
                            spot = eggSpot(species.rarity), scared = vm.giveUpArmed,
                        )
                    } else {
                        LivingDino(d.pet, (104 * s).dp, hopping = running)
                    }
                }
                Row {
                    Text(species.name, color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (!inSession) Text(" · แตะเพื่อเลือก", color = p.muted, fontSize = 12.sp)
                    else if (hatching) Text(" · กำลังฟัก ${(el * 100 / tgt.coerceAtLeast(1L)).coerceIn(0L, 99L)}%", color = p.muted, fontSize = 12.sp)
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

        Ring(vm, el, tgt, over, started, s)

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
            val view = LocalView.current
            LaunchedEffect(vm.giveUpArmed) {
                if (vm.giveUpArmed) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askBatteryOnce(ctx) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BigButton(label, style, Modifier.widthIn(min = 220.dp).shake(vm.giveUpArmed)) {
                if (over) { vm.finishSuccess(goBreak = false); return@BigButton }
                if (!started) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else askBatteryOnce(ctx)
                }
                vm.onFocusButton()
            }
            // hint keeps its line so the layout does not jump when it appears
            Text(hint, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, minLines = 1, maxLines = 2)
            }
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
}

/** The pet on the timer card: breathes while idle, hops softly while the clock runs. */
@Composable
private fun LivingDino(id: String, size: Dp, hopping: Boolean) {
    val reduce = LocalReduceMotion.current
    val tr = rememberInfiniteTransition(label = "dino")
    val breath = tr.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath",
    )
    val hop = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "hop")
    Box(contentAlignment = Alignment.Center) {
        SpriteImage(
            id, size,
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                if (!reduce) {
                    if (hopping) {
                        // up and down in the first 60 %, a little squash on landing in the rest
                        val k = hop.value
                        val air = if (k < 0.6f) sin(k / 0.6f * PI).toFloat() else 0f
                        val squash = if (k >= 0.6f) sin((k - 0.6f) / 0.4f * PI).toFloat() else 0f
                        translationY = -air * 7.dp.toPx()
                        scaleY = 1f + air * 0.04f - squash * 0.05f
                        scaleX = 1f - air * 0.03f + squash * 0.04f
                    } else {
                        val b = breath.value
                        scaleY = 1f + b * 0.03f
                        scaleX = 1f - b * 0.012f
                    }
                }
            },
        )
        if (Dinos.get(id).rarity == Rarity.LEGENDARY) Sparkles(Modifier.matchParentSize())
    }
}

@Composable
private fun Ring(vm: AppViewModel, el: Long, tgt: Long, over: Boolean, started: Boolean, s: Float = 1f) {
    val p = LocalPal.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    val work = vm.data.mode == MODE_WORK
    val frac = if (over) 1f else (el.toFloat() / tgt).coerceIn(0f, 1f)
    // the clock ticks every 250 ms: glide between ticks so the ring moves every frame;
    // a reset or a mode change sweeps it back smoothly
    val fracSpec: AnimationSpec<Float> =
        if (vm.isRunning) tween(250, easing = LinearEasing) else tween(600, easing = FastOutSlowInEasing)
    val animFrac = animateFloatAsState(frac, fracSpec, label = "frac")
    val ringColor by animateColorAsState(
        when {
            over -> p.yellow
            started -> if (work) WORK_COLOR else SHORT_COLOR
            else -> p.border
        },
        tween(400), label = "ring",
    )
    val text = if (over) "+" + fmtClock((el - tgt) / 1000) else fmtClock(ceil((tgt - el) / 1000.0).toLong())
    val sub = if (over) "รวม " + fmtClock(el / 1000) else ""
    Box(Modifier.size((200 * s).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((200 * s).dp)) {
            val sw = 8.dp.toPx()
            val r = size.width * 88f / 200f
            val tl = Offset(center.x - r, center.y - r)
            val f = animFrac.value.coerceIn(0f, 1f)
            drawCircle(p.border, radius = r, style = Stroke(sw))
            drawArc(
                ringColor.copy(alpha = if (over) pulse else 1f), startAngle = -90f, sweepAngle = 360f * f,
                useCenter = false, topLeft = tl, size = Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round),
            )
            // glowing head at the end of the arc while a round runs
            if (started && !over && f > 0f && f < 1f) {
                val ang = (-90.0 + 360.0 * f) * PI / 180.0
                val head = Offset(center.x + (cos(ang) * r).toFloat(), center.y + (sin(ang) * r).toFloat())
                val glow = (1f - pulse) / 0.45f
                drawCircle(ringColor.copy(alpha = 0.16f + 0.14f * glow), radius = sw * (1.2f + 0.5f * glow), center = head)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = sw * 0.26f, center = head)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (over) "เกินเวลา" else if (work) "โฟกัส" else "พักสั้น",
                color = p.muted, fontSize = 11.sp, letterSpacing = 0.8.sp,
            )
            Text(
                text,
                color = if (over) p.yellow else p.fg,
                fontSize = (s * when { text.length >= 7 -> 34; text.length == 6 -> 42; else -> 52 }).sp,
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
    // pressed-in feel, a light tick, and colours that blend when the button changes role
    val src = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val view = LocalView.current
    val bgA by animateColorAsState(bg, tween(220), label = "btnbg")
    val fgA by animateColorAsState(fg, tween(220), label = "btnfg")
    Box(
        modifier
            .pressScale(src)
            .clip(shape)
            .background(bgA)
            .then(borderMod)
            .clickable(interactionSource = src, indication = indication) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = if (small) 12.dp else 32.dp, vertical = if (small) 16.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = fgA, fontWeight = FontWeight.Bold,
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

/** Ask once to be excluded from battery optimisation so the timer is not stopped in the background. */
@android.annotation.SuppressLint("BatteryLife")
private fun askBatteryOnce(ctx: android.content.Context) {
    val prefs = ctx.getSharedPreferences("dinofocus_ui", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("askedBattery", false)) return
    val pm = ctx.getSystemService(android.os.PowerManager::class.java) ?: return
    if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return
    prefs.edit().putBoolean("askedBattery", true).apply()
    try {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:" + ctx.packageName))
        )
    } catch (_: Exception) {
    }
}

/** "DinoFocus v1.N · by NorthXbar" — the version comes from the build (commit count). */
@Composable
fun CreditLine(modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Text(
        "DinoFocus v${com.titipong.dinofocus.BuildConfig.VERSION_NAME} · by NorthXbar",
        color = p.muted, fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().alpha(0.8f),
    )
}

/**
 * Big moment animation over everything: rings ripple out from the centre while the title pops in,
 * then it all fades (about 1.6 s). Touches pass through.
 */
@Composable
private fun FxOverlay(vm: AppViewModel) {
    val ev = vm.fx ?: return
    val p = LocalPal.current
    val anim = remember(ev.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(ev.id) {
        anim.animateTo(1f, tween(1700, easing = LinearEasing))
        if (vm.fx?.id == ev.id) vm.fx = null
    }
    val t = anim.value
    val color = when (ev.kind) {
        com.titipong.dinofocus.Fx.FOCUS -> p.accent
        com.titipong.dinofocus.Fx.BREAK -> p.green
        com.titipong.dinofocus.Fx.BREAK_DONE -> p.yellow
    }
    val d = vm.data
    val title = when (ev.kind) {
        com.titipong.dinofocus.Fx.FOCUS -> "เริ่มโฟกัส!"
        com.titipong.dinofocus.Fx.BREAK -> "เริ่มพัก"
        com.titipong.dinofocus.Fx.BREAK_DONE -> "พักเสร็จแล้ว!"
    }
    val sub = when (ev.kind) {
        com.titipong.dinofocus.Fx.FOCUS -> "${d.workMin} นาที · ${Dinos.get(d.pet).name} กำลังฟัก"
        com.titipong.dinofocus.Fx.BREAK -> "พักสบายๆ ${d.shortMin} นาที"
        com.titipong.dinofocus.Fx.BREAK_DONE -> "กด Start Focus เพื่อเริ่มรอบใหม่"
    }
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val easeOut = { x: Float -> 1 - (1 - x) * (1 - x) * (1 - x) }
    val fadeOut = 1f - seg(0.72f, 1f)
    val scrim = (seg(0f, 0.12f) * 0.55f) * fadeOut
    val pop = when {
        t < 0.18f -> 0.3f + 0.85f * (t / 0.18f)
        t < 0.3f -> 1.15f - 0.15f * ((t - 0.18f) / 0.12f)
        else -> 1f
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(p.bg.copy(alpha = scrim))
            val maxR = size.maxDimension * 0.75f
            listOf(0f, 0.12f, 0.24f).forEach { delayT ->
                val k = easeOut(seg(delayT, delayT + 0.6f))
                if (k > 0f) drawCircle(
                    color.copy(alpha = 0.45f * (1f - k) * fadeOut),
                    radius = maxR * k,
                    style = Stroke(width = (18f * (1f - k) + 3f).dp.toPx()),
                )
            }
            drawCircle(color.copy(alpha = 0.18f * fadeOut * seg(0f, 0.15f)), radius = 120.dp.toPx() * pop)
        }
        Column(
            Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = seg(0f, 0.08f) * fadeOut },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (ev.kind) {
                com.titipong.dinofocus.Fx.FOCUS -> SpriteImage(d.pet, 120.dp)
                com.titipong.dinofocus.Fx.BREAK -> Text("☕", fontSize = 72.sp)
                com.titipong.dinofocus.Fx.BREAK_DONE -> Text("💪", fontSize = 72.sp)
            }
            Text(title, color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = p.fg, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

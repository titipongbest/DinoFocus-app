package com.titipong.dinofocus.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.titipong.dinofocus.Rarity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.titipong.dinofocus.AppViewModel
import com.titipong.dinofocus.Dinos
import com.titipong.dinofocus.NO_TAG_COLOR
import com.titipong.dinofocus.RoundResult
import com.titipong.dinofocus.fmtClock

@Composable
private fun DialogBox(borderColor: Color, content: @Composable () -> Unit) {
    val p = LocalPal.current
    // every dialog springs in from slightly smaller
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = 380f))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .graphicsLayer {
                val sc = 0.86f + 0.14f * appear.value
                scaleX = sc; scaleY = sc
                alpha = (appear.value * 1.6f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface)
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun DialogTitle(text: String, color: Color) {
    Text(text, fontFamily = PixelFont, fontSize = 12.sp, lineHeight = 20.sp, color = color, textAlign = TextAlign.Center)
}

@Composable
private fun OutlineButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/** Shown after Stop (summary + new dino) or Give up (grave). */
@Composable
fun ResultDialog(vm: AppViewModel, r: RoundResult) {
    val p = LocalPal.current
    val sp = Dinos.get(r.sp)
    val bounce by rememberInfiniteTransition(label = "b").animateFloat(
        0f, -8f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "y",
    )
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp)) {
            DialogBox(if (r.dead) p.muted else p.accent) {
                DialogTitle(if (r.dead) "ยอมแพ้แล้ว" else "สรุปการเรียน", if (r.dead) p.muted else p.accentGlow)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.surface2)
                        .border(1.dp, p.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("เรียนไปจริง", color = p.muted, fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${rememberCountUp(r.workSec / 60, 900)}", color = p.fg, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(" นาที", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.tag != null) {
                            Dot(Color(vm.data.tagColor(r.tag)), 6.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("${r.tag} · ", color = p.muted, fontSize = 12.sp)
                        }
                        Text(
                            buildAnnotatedString {
                                append("${fmtClock(r.workSec.toLong())} · เป้าหมาย ${r.targetMin} นาที")
                                if (r.overSec >= 1) {
                                    append(" · เกิน ")
                                    withStyle(SpanStyle(color = p.yellow, fontWeight = FontWeight.SemiBold)) { append("+${fmtClock(r.overSec.toLong())}") }
                                }
                            },
                            color = p.muted, fontSize = 12.sp,
                        )
                    }
                }
                Text(if (r.dead) "หลุมศพจะอยู่บนพื้นหญ้าของวันนี้" else "ได้รับไดโนเสาร์", color = p.muted, fontSize = 12.sp)
                if (r.dead) FallingGrave() else Box(contentAlignment = Alignment.Center) {
                    SpriteImage(r.sp, 120.dp, Modifier.offset(y = bounce.dp))
                    if (sp.rarity == Rarity.LEGENDARY) Sparkles(Modifier.matchParentSize(), count = 7)
                }
                Text(if (r.dead) "${sp.name} ตายแล้ว" else sp.name, color = p.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (!r.dead) RarityChip(sp.rarity)
                FinishButtons(onFinish = { vm.recordResult(goBreak = false) }, onBreak = { vm.recordResult(goBreak = true) })
            }
        }
    }
}

@Composable
fun PetPickerDialog(vm: AppViewModel, onClose: () -> Unit) {
    val p = LocalPal.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp)) {
            DialogBox(p.accent) {
                DialogTitle("เลือกไดโนเสาร์", p.accentGlow)
                Text("ตัวที่เลือกจะเป็นตัวที่ได้รับเมื่อเรียนจบรอบ", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Dinos.SPECIES.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                val sel = s.id == vm.data.pet
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) lerpColor(p.surface2, p.accent, 0.14f) else p.surface2)
                                        .border(2.dp, if (sel) p.accentGlow else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable { vm.selectPet(s.id); onClose() }
                                        .padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SpriteImage(s.id, 56.dp)
                                        if (s.rarity == Rarity.LEGENDARY) Sparkles(Modifier.matchParentSize(), count = 4, seed = s.id.hashCode())
                                    }
                                    Text(
                                        s.name, color = p.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    RarityChip(s.rarity, small = true)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                OutlineButton("ปิด", Modifier.fillMaxWidth(), onClose)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDialog(vm: AppViewModel, onClose: () -> Unit) {
    val p = LocalPal.current
    var manage by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val wiggle by rememberInfiniteTransition(label = "w").animateFloat(
        -1.5f, 1.5f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "r",
    )
    val d = vm.data

    fun submit() {
        if (vm.addTag(input)) {
            input = ""; adding = false
            if (vm.data.tag != null) onClose()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp)) {
            DialogBox(p.accent) {
                DialogTitle("เลือก tag", p.accentGlow)
                Text("เวลาของรอบนี้จะบันทึกไว้กับ tag ที่เลือก", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!manage && !adding) {
                        TagChip("ไม่ระบุ", Color(NO_TAG_COLOR), selected = d.tag == null) { vm.selectTag(null); onClose() }
                    }
                    d.tags.forEach { t ->
                        TagChip(
                            t.name, Color(t.color),
                            selected = !manage && d.tag == t.name,
                            modifier = if (manage) Modifier.rotate(wiggle) else Modifier,
                            removable = manage,
                        ) {
                            if (manage) {
                                vm.deleteTag(t.name)
                                if (vm.data.tags.isEmpty()) manage = false
                            } else {
                                vm.selectTag(t.name); onClose()
                            }
                        }
                    }
                    if (adding) {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.take(20) },
                            singleLine = true,
                            textStyle = TextStyle(color = p.fg, fontSize = 13.sp),
                            cursorBrush = SolidColor(p.accentGlow),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier
                                .width(150.dp)
                                .focusRequester(focus)
                                .clip(RoundedCornerShape(999.dp))
                                .background(p.surface)
                                .border(1.dp, p.accentGlow, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (input.isEmpty()) Text("ชื่อ tag ใหม่", color = p.muted, fontSize = 13.sp)
                                    inner()
                                }
                            },
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TagChip("เพิ่ม", null) { submit() }
                        TagChip("ยกเลิก", null, ghost = true) { adding = false; input = "" }
                    } else if (!manage) {
                        TagChip("＋ เพิ่ม", null, ghost = true) { adding = true }
                    }
                    if (d.tags.isNotEmpty() && !adding) {
                        TagChip(if (manage) "เสร็จ" else "✎ จัดการ", null, ghost = true) { manage = !manage }
                    }
                }
                if (manage) {
                    Text(
                        buildAnnotatedString { append("แตะ tag เพื่อลบ · เวลาที่บันทึกไว้แล้วจะไม่หาย") },
                        color = p.muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                }
                OutlineButton("ปิด", Modifier.fillMaxWidth(), onClose)
            }
        }
    }
}

@Composable
private fun FinishButtons(onFinish: () -> Unit, onBreak: () -> Unit) {
    val p = LocalPal.current
    val indication = LocalIndication.current
    val srcA = remember { MutableInteractionSource() }
    val srcB = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .pressScale(srcA)
                .clip(RoundedCornerShape(12.dp))
                .background(p.accent)
                .clickable(interactionSource = srcA, indication = indication, onClick = onFinish)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("เสร็จสิ้น", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Box(
            Modifier
                .weight(1f)
                .pressScale(srcB)
                .clip(RoundedCornerShape(12.dp))
                .background(p.green)
                .clickable(interactionSource = srcB, indication = indication, onClick = onBreak)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("☕ Take a break", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** Opens when the target is reached; time keeps counting (max 90 min) until Finish / Take a break. */
@Composable
fun SuccessDialog(vm: AppViewModel) {
    val p = LocalPal.current
    val d = vm.data
    val sp = Dinos.get(d.pet)
    val el = vm.elapsedMs
    val over = el - d.workMin * 60_000L
    val bounce by rememberInfiniteTransition(label = "b").animateFloat(
        0f, -8f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "y",
    )
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp)) {
            DialogBox(p.yellow) {
                DialogTitle("ไดโนเสาร์ใหม่สำเร็จ!", p.yellow)
                HatchingDino(sp.id, bounce)
                Text(sp.name, color = p.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                RarityChip(sp.rarity)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.surface2)
                        .border(1.dp, p.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (vm.atCap) "ครบ 90 นาทีแล้ว หยุดนับเวลา" else "กำลังนับเวลาต่อ…", color = if (vm.atCap) p.yellow else p.muted, fontSize = 11.sp)
                    Text(fmtClock(el / 1000), color = p.fg, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (d.tag != null) {
                            Dot(Color(d.tagColor(d.tag)), 6.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("${d.tag} · ", color = p.muted, fontSize = 12.sp)
                        }
                        Text(
                            buildAnnotatedString {
                                append("เป้าหมาย ${d.workMin} นาที")
                                if (over >= 1000) {
                                    append(" · เกิน ")
                                    withStyle(SpanStyle(color = p.yellow, fontWeight = FontWeight.SemiBold)) { append("+${fmtClock(over / 1000)}") }
                                }
                            },
                            color = p.muted, fontSize = 12.sp,
                        )
                    }
                    if (!vm.atCap) Text("นับได้สูงสุด 90 นาที", color = p.muted, fontSize = 11.sp)
                }
                Text("กดเสร็จสิ้นเพื่อบันทึกไดโนเสาร์พร้อมเวลา", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                FinishButtons(onFinish = { vm.finishSuccess(goBreak = false) }, onBreak = { vm.finishSuccess(goBreak = true) })
            }
            Confetti(Modifier.matchParentSize())
        }
    }
}

/** The new dino pops out with an overshoot, a little shake and a pulsing glow behind it. */
@Composable
private fun HatchingDino(id: String, bounce: Float) {
    val p = LocalPal.current
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.38f, stiffness = 180f))
    }
    val glow by rememberInfiniteTransition(label = "g").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "gl",
    )
    val t = pop.value
    // the two halves of the egg shell fly apart as the dino pops out
    val shell = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { shell.animateTo(1f, tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(p.yellow.copy(alpha = 0.45f * glow * t.coerceIn(0f, 1f)), Color.Transparent),
                ),
                radius = size.minDimension / 2 * (0.75f + 0.25f * glow),
            )
            val k = shell.value
            if (k < 1f) {
                val a = (1f - k * k).coerceIn(0f, 1f)
                val w = size.width * 0.36f
                val cx = size.width / 2
                val cy = size.height * 0.55f
                val cream = Color(0xFFF3E6C4).copy(alpha = a)
                val edge = Color(0xFF8A6B3D).copy(alpha = a * 0.6f)
                // top half: up and to the left, tumbling
                val topC = androidx.compose.ui.geometry.Offset(cx - k * size.width * 0.34f, cy - k * size.height * 0.36f)
                rotate(-70f * k, topC) {
                    drawArc(cream, 180f, 180f, true, androidx.compose.ui.geometry.Offset(topC.x - w / 2, topC.y - w * 0.62f), androidx.compose.ui.geometry.Size(w, w * 1.24f))
                    drawArc(edge, 180f, 180f, false, androidx.compose.ui.geometry.Offset(topC.x - w / 2, topC.y - w * 0.62f), androidx.compose.ui.geometry.Size(w, w * 1.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
                // bottom half: drops down to the right
                val botC = androidx.compose.ui.geometry.Offset(cx + k * size.width * 0.30f, cy + k * k * size.height * 0.30f)
                rotate(55f * k, botC) {
                    drawArc(cream, 0f, 180f, true, androidx.compose.ui.geometry.Offset(botC.x - w / 2, botC.y - w * 0.5f), androidx.compose.ui.geometry.Size(w, w))
                    drawArc(edge, 0f, 180f, false, androidx.compose.ui.geometry.Offset(botC.x - w / 2, botC.y - w * 0.5f), androidx.compose.ui.geometry.Size(w, w), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
        }
        SpriteImage(
            id, 120.dp,
            Modifier
                .offset(y = (bounce * t.coerceIn(0f, 1f)).dp)
                .graphicsLayer {
                    scaleX = t; scaleY = t
                    rotationZ = (1f - t.coerceIn(0f, 1f)) * 25f * kotlin.math.sin(t * 18f)
                },
        )
        if (Dinos.get(id).rarity == Rarity.LEGENDARY) Sparkles(Modifier.matchParentSize(), count = 7)
    }
}

/** A grave drops in and settles. */
@Composable
private fun FallingGrave() {
    val drop = remember { androidx.compose.animation.core.Animatable(-160f) }
    LaunchedEffect(Unit) {
        drop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    SpriteImage("grave", 120.dp, Modifier.graphicsLayer { translationY = drop.value * density; alpha = (1f + drop.value / 160f).coerceIn(0.2f, 1f) })
}

/** One burst of pixel confetti over the dialog. */
@Composable
private fun Confetti(modifier: Modifier) {
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2200, easing = androidx.compose.animation.core.LinearEasing)) }
    val colors = listOf(Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF9B5DE5), Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFFFFF176))
    val parts = remember {
        val r = java.util.Random(7)
        List(46) { floatArrayOf(r.nextFloat() * 6.283f, 0.35f + r.nextFloat() * 0.65f, r.nextFloat() * 360f, r.nextInt(colors.size).toFloat()) }
    }
    if (t.value >= 1f) return
    androidx.compose.foundation.Canvas(modifier) {
        val k = t.value
        val cx = size.width / 2
        val cy = size.height * 0.3f
        val u = 5.dp.toPx()
        parts.forEach { (ang, spd, rot, ci) ->
            val dist = spd * size.width * 0.75f * (1 - (1 - k) * (1 - k))
            val x = cx + kotlin.math.cos(ang) * dist
            val y = cy + kotlin.math.sin(ang) * dist * 0.8f + k * k * size.height * 0.55f
            rotate(rot + k * 540f, androidx.compose.ui.geometry.Offset(x, y)) {
                drawRect(
                    colors[ci.toInt()].copy(alpha = (1f - k).coerceIn(0f, 1f)),
                    androidx.compose.ui.geometry.Offset(x - u / 2, y - u / 2),
                    androidx.compose.ui.geometry.Size(u, u * 0.6f),
                )
            }
        }
    }
}

package com.titipong.dinofocus.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
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
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface2)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
                        Text("${r.workSec / 60}", color = p.fg, fontSize = 36.sp, fontWeight = FontWeight.Bold)
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
                SpriteImage(if (r.dead) "grave" else r.sp, 120.dp, Modifier.offset(y = if (r.dead) 0.dp else bounce.dp))
                Text(if (r.dead) "${sp.name} ตายแล้ว" else sp.name, color = p.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (!r.dead) RarityChip(sp.rarity)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("กลับหน้าหลัก", Modifier.weight(1f)) { vm.recordResult(goBreak = false) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.green)
                            .clickable { vm.recordResult(goBreak = true) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("☕ พักสั้น", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
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
                                    SpriteImage(s.id, 56.dp)
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

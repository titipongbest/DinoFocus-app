package com.titipong.dinofocus

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

data class ToastMsg(val text: String, val id: Long = System.nanoTime())

/** What the result popup shows after Stop (dead = false) or Give up (dead = true). */
data class RoundResult(
    val dead: Boolean,
    val sp: String,
    val workSec: Int,
    val targetMin: Int,
    val overSec: Int,
    val tag: String?,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("dinofocus", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var data by mutableStateOf(load())
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var toast by mutableStateOf<ToastMsg?>(null)
    var giveUpArmed by mutableStateOf(false)
        private set
    var result by mutableStateOf<RoundResult?>(null)
        private set
    /** Selected dino on today's ground (collection index), -1 = none. */
    var todaySel by mutableIntStateOf(-1)

    private var armJob: Job? = null

    init {
        TimerService.sync(app, data)
        val s = data.session
        if (s.runStart == null && s.accumulated > 0) {
            update { it.copy(session = s.copy(runStart = System.currentTimeMillis())) }
        }
        viewModelScope.launch {
            while (true) {
                now = System.currentTimeMillis()
                tick()
                delay(250)
            }
        }
    }

    private fun load(): AppData = try {
        prefs.getString("data", null)?.let { json.decodeFromString(AppData.serializer(), it) } ?: AppData()
    } catch (e: Exception) {
        AppData()
    }.let { d ->
        var x = d
        if (x.mode != MODE_WORK && x.mode != MODE_SHORT) x = x.copy(mode = MODE_WORK, session = Session())
        if (!Dinos.exists(x.pet)) x = x.copy(pet = "rex")
        if (x.tag != null && x.tags.none { it.name == x.tag }) x = x.copy(tag = null)
        val wr = SliderCfg.range(MODE_WORK); val sr = SliderCfg.range(MODE_SHORT)
        x.copy(workMin = x.workMin.coerceIn(wr), shortMin = x.shortMin.coerceIn(sr))
    }

    private fun update(f: (AppData) -> AppData) {
        val before = data
        data = f(data)
        // commit() so the foreground service reads the new state right away
        prefs.edit().putString("data", json.encodeToString(AppData.serializer(), data)).commit()
        if (before.session != data.session || before.mode != data.mode) TimerService.sync(getApplication(), data)
    }

    // ── time ────────────────────────────────────────────────────────────
    private val rawElapsedMs: Long get() = data.session.accumulated + (data.session.runStart?.let { now - it } ?: 0L)
    /** Focus rounds stop counting at 90 minutes. */
    val elapsedMs: Long get() = if (data.mode == MODE_WORK) minOf(rawElapsedMs, MAX_FOCUS_MS) else rawElapsedMs
    val atCap: Boolean get() = data.mode == MODE_WORK && rawElapsedMs >= MAX_FOCUS_MS
    /** Target reached: the "new dino" window stays open until Finish or Take a break. */
    val successOpen: Boolean get() = inOvertime && result == null
    val targetMs: Long get() = data.minutesFor(data.mode) * 60_000L
    val isRunning: Boolean get() = data.session.runStart != null
    val inOvertime: Boolean get() = data.mode == MODE_WORK && elapsedMs >= targetMs
    val started: Boolean get() = elapsedMs > 0
    val inSession: Boolean get() = data.mode == MODE_WORK && started

    private fun tick() {
        if (!isRunning) return
        val el = elapsedMs
        if (data.mode == MODE_WORK) {
            if (el >= targetMs && !data.session.notified) {
                update { it.copy(session = it.session.copy(notified = true)) }
                chime()
                Notifier.cancelAlarm(getApplication())
                Notifier.showSuccess(getApplication(), Dinos.get(data.pet).name, data.workMin)
            }
        } else if (el >= targetMs) {
            chime()
            showToast("พักเสร็จแล้ว! กลับมาโฟกัสกัน 💪")
            val min = data.shortMin
            switchMode(MODE_WORK)
            Notifier.showBreakDone(getApplication(), min)
        }
    }

    private fun start() {
        now = System.currentTimeMillis()
        update { it.copy(session = it.session.copy(runStart = now)) }
        val at = now + (targetMs - data.session.accumulated)
        if (data.mode == MODE_WORK && !data.session.notified) {
            Notifier.scheduleSuccess(getApplication(), at, Dinos.get(data.pet).name, data.workMin)
        } else if (data.mode == MODE_SHORT) {
            Notifier.scheduleBreakDone(getApplication(), at, data.shortMin)
        }
    }

    private fun pause() {
        now = System.currentTimeMillis()
        val el = elapsedMs
        update { it.copy(session = it.session.copy(accumulated = el, runStart = null)) }
    }

    private fun clearSession() {
        Notifier.cancelSuccess(getApplication())
        update { it.copy(session = Session()) }
    }

    /** Mode tabs: locked while a focus round is in progress. */
    fun selectModeTab(mode: String) {
        if (inSession) return
        switchMode(mode)
    }

    private fun switchMode(mode: String) {
        giveUpArmed = false
        Notifier.cancelSuccess(getApplication())
        update { it.copy(mode = mode, session = Session()) }
    }

    fun setDuration(min: Int) {
        if (started) return
        val m = min.coerceIn(SliderCfg.range(data.mode))
        update { if (it.mode == MODE_WORK) it.copy(workMin = m) else it.copy(shortMin = m) }
    }

    fun showToast(text: String) { toast = ToastMsg(text) }

    // ── Start Focus button: start → cancel (10 s) → give up (tap twice) → stop & save ──
    fun onFocusButton() {
        val el = elapsedMs
        when {
            el == 0L && !isRunning -> start()
            inOvertime -> {}
            el < CANCEL_MS -> { clearSession(); showToast("ยกเลิกแล้ว") }
            !giveUpArmed -> {
                giveUpArmed = true
                armJob?.cancel()
                armJob = viewModelScope.launch { delay(3000); giveUpArmed = false }
            }
            else -> {
                giveUpArmed = false
                armJob?.cancel()
                finishWork(true)
            }
        }
    }

    // ── break page ──────────────────────────────────────────────────────
    fun breakStart() = start()
    fun breakCancel() { clearSession(); showToast("ยกเลิกการพักแล้ว") }
    fun breakToFocus() { switchMode(MODE_WORK); start() }

    private fun finishWork(dead: Boolean) {
        Notifier.cancelSuccess(getApplication())
        pause()
        val workSec = Math.round(elapsedMs / 1000.0).toInt()
        val targetMin = data.workMin
        result = RoundResult(
            dead = dead,
            sp = data.pet,
            workSec = workSec,
            targetMin = targetMin,
            overSec = maxOf(0, workSec - targetMin * 60),
            tag = data.tag,
        )
    }

    /** Finish (goBreak = false) or Take a break from the "new dino" window: save dino + real time. */
    fun finishSuccess(goBreak: Boolean) {
        if (!inOvertime) return
        now = System.currentTimeMillis()
        val workSec = Math.round(elapsedMs / 1000.0).toInt()
        val targetMin = data.workMin
        result = RoundResult(false, data.pet, workSec, targetMin, maxOf(0, workSec - targetMin * 60), data.tag)
        recordResult(goBreak)
    }

    /** Save the round shown in the popup, then go home or straight into a short break. */
    fun recordResult(goBreak: Boolean) {
        val r = result ?: return
        result = null
        val nowMs = System.currentTimeMillis()
        val today = dayKey(nowMs)
        update { d ->
            val entry = Entry(sp = r.sp, ts = nowMs, workSec = r.workSec, targetMin = r.targetMin, tag = r.tag, dead = r.dead)
            var n = d.copy(
                collection = d.collection + entry,
                dailyWorkSec = d.dailyWorkSec + (today to (d.dailyWorkSec[today] ?: 0) + r.workSec),
            )
            if (!r.dead) {
                n = n.copy(
                    totalPomos = d.totalPomos + 1,
                    dailyPomos = d.dailyPomos + (today to (d.dailyPomos[today] ?: 0) + 1),
                )
                if (d.lastDay != today) {
                    val y = LocalDate.parse(today).minusDays(1).toString()
                    n = n.copy(streak = if (d.lastDay == y) d.streak + 1 else 1, lastDay = today)
                }
            }
            n
        }
        todaySel = data.collection.size - 1
        if (goBreak) {
            switchMode(MODE_SHORT)
            start()
            showToast("เริ่มพักสั้นแล้ว ☕")
        } else {
            switchMode(MODE_WORK)
            showToast("บันทึกแล้ว · เรียนไป ${r.workSec / 60} นาที")
        }
    }

    // ── pet & tags ──────────────────────────────────────────────────────
    fun selectPet(id: String) {
        if (inSession) return
        update { it.copy(pet = id) }
        showToast("เลือก ${Dinos.get(id).name} แล้ว")
    }

    fun selectTag(name: String?) {
        if (inSession) return
        update { it.copy(tag = name) }
    }

    /** Returns false when the name is empty or already used. */
    fun addTag(raw: String): Boolean {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(20)
        if (name.isEmpty()) return true
        if (data.tags.any { it.name.equals(name, ignoreCase = true) }) {
            showToast("มี tag \"$name\" อยู่แล้ว")
            return false
        }
        val used = data.tags.map { it.color }
        val color = TAG_COLORS.firstOrNull { it !in used } ?: TAG_COLORS[data.tags.size % TAG_COLORS.size]
        update { it.copy(tags = it.tags + Tag(name, color), tag = name) }
        showToast("เพิ่ม tag \"$name\" แล้ว")
        return true
    }

    fun deleteTag(name: String) {
        update { d -> d.copy(tags = d.tags.filter { it.name != name }, tag = if (d.tag == name) null else d.tag) }
        showToast("ลบ tag \"$name\" แล้ว")
    }

    // ── chime: three rising triangle-wave notes ─────────────────────────
    private fun chime() {
        thread(isDaemon = true) {
            try {
                val rate = 44_100
                val total = (rate * 0.7).toInt()
                val buf = FloatArray(total)
                listOf(660.0, 880.0, 1320.0).forEachIndexed { i, f ->
                    val startI = (i * 0.18 * rate).toInt()
                    val len = (0.3 * rate).toInt()
                    for (k in 0 until len) {
                        val idx = startI + k
                        if (idx >= total) break
                        val t = k.toDouble() / rate
                        val phase = t * f
                        val tri = 4 * abs(phase - floor(phase + 0.5)) - 1
                        val env = if (t < 0.02) t / 0.02 else exp(-(t - 0.02) * 14)
                        buf[idx] += (tri * env * 0.25).toFloat()
                    }
                }
                val pcm = ShortArray(total) { (buf[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(900)
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}

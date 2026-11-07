package com.titipong.dinofocus

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val MODE_WORK = "work"
const val MODE_SHORT = "short"
const val CANCEL_MS = 10_000L
const val MAX_FOCUS_MS = 90 * 60_000L
const val NO_TAG = "ไม่ระบุ"
const val BACKUP_FILE = "DinoFocus-backup.json"
const val SYNC_FILE = "DinoFocus-sync.json"
const val NO_TAG_COLOR = 0xFF94A3B8

val TAG_COLORS = listOf(
    0xFF3B82F6, 0xFFF97316, 0xFF10B981, 0xFFEC4899,
    0xFFEAB308, 0xFF8B5CF6, 0xFF06B6D4, 0xFFEF4444,
)

@Serializable
data class Tag(val name: String, val color: Long)

/** One finished focus round. dead = the user gave up and the dino became a grave. */
@Serializable
data class Entry(
    val id: String = "",
    val sp: String,
    val ts: Long,
    val workSec: Int = 0,
    val targetMin: Int = 0,
    val tag: String? = null,
    val dead: Boolean = false,
) {
    val species get() = Dinos.get(sp)
    val spriteId get() = if (dead) "grave" else sp
    val day get() = dayKey(ts)
}

@Serializable
data class Session(val accumulated: Long = 0, val runStart: Long? = null, val notified: Boolean = false)

@Serializable
data class AppData(
    val mode: String = MODE_WORK,
    val collection: List<Entry> = emptyList(),
    val totalPomos: Int = 0,
    val dailyPomos: Map<String, Int> = emptyMap(),
    val dailyWorkSec: Map<String, Int> = emptyMap(),
    val streak: Int = 0,
    val lastDay: String? = null,
    val session: Session = Session(),
    val pet: String = "rex",
    val tags: List<Tag> = listOf(
        Tag("อ่านหนังสือ", TAG_COLORS[0]),
        Tag("Blender", TAG_COLORS[1]),
        Tag("Solidwork", TAG_COLORS[2]),
    ),
    val tag: String? = null,
    val workMin: Int = 25,
    val shortMin: Int = 5,
) {
    fun minutesFor(mode: String) = if (mode == MODE_WORK) workMin else shortMin
    fun tagColor(name: String?): Long =
        if (name == null || name == NO_TAG) NO_TAG_COLOR else tags.firstOrNull { it.name == name }?.color ?: NO_TAG_COLOR
    fun entriesOn(day: String) = collection.indices.filter { collection[it].day == day }
    fun minutesOn(day: String) = (dailyWorkSec[day] ?: 0) / 60
    /** Daily minutes, rounds, total and streak rebuilt from the rounds themselves (used after a sync merge). */
    fun recomputed(): AppData {
        val ws = HashMap<String, Int>()
        val rounds = HashMap<String, Int>()
        val aliveDays = HashSet<String>()
        collection.forEach { e ->
            ws[e.day] = (ws[e.day] ?: 0) + e.workSec
            rounds[e.day] = (rounds[e.day] ?: 0) + 1
            if (!e.dead) aliveDays.add(e.day)
        }
        val last = aliveDays.maxOrNull()
        var streak = 0
        if (last != null) {
            var d = LocalDate.parse(last)
            while (aliveDays.contains(d.toString())) { streak++; d = d.minusDays(1) }
        }
        return copy(dailyWorkSec = ws, dailyPomos = rounds, totalPomos = collection.size, lastDay = last, streak = streak)
    }
    fun roundsOn(day: String) = (dailyPomos[day] ?: 0).takeIf { it > 0 }
        ?: collection.count { it.day == day }
}

object SliderCfg {
    fun range(mode: String) = if (mode == MODE_WORK) 15..60 else 5..25
}

// ── dates (viewer's own timezone, Thai labels like the web app) ─────────
private val zone: ZoneId get() = ZoneId.systemDefault()
private val TH_DAYS = arrayOf("จันทร์", "อังคาร", "พุธ", "พฤหัส", "ศุกร์", "เสาร์", "อาทิตย์")
private val TH_MONTHS = arrayOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

fun dayKey(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
fun todayKey(): String = LocalDate.now(zone).toString()
fun keyToDate(k: String): LocalDate = LocalDate.parse(k)
fun thWeekday(d: LocalDate) = TH_DAYS[d.dayOfWeek.value - 1]
fun thDayMonth(d: LocalDate) = "${d.dayOfMonth} ${TH_MONTHS[d.monthValue - 1]}"
fun thHeaderDate(d: LocalDate) = "${thWeekday(d)} ${thDayMonth(d)}"
fun thMonth(d: LocalDate) = TH_MONTHS[d.monthValue - 1]
fun thDateYear(d: LocalDate) = "${thDayMonth(d)} ${String.format("%02d", (d.year + 543) % 100)}"
fun thLongDate(d: LocalDate) = "${thWeekday(d)} ${thDayMonth(d)} ${String.format("%02d", (d.year + 543) % 100)}"
fun hhmm(ms: Long): String = HHMM.format(Instant.ofEpochMilli(ms).atZone(zone))
fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

fun fmtClock(totalSecIn: Long): String {
    val totalSec = maxOf(0L, totalSecIn)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

/** Backup file: the whole AppData plus a small header. Plain AppData JSON is accepted on import too. */
@Serializable
data class Backup(
    val app: String = "DinoFocus",
    val version: Int = 1,
    val exportedAt: Long = 0,
    val data: AppData,
) {
    companion object {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        fun parse(text: String): AppData? {
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.app == "DinoFocus" }?.let { return it.data }
            return runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull()
                ?.takeIf { text.contains("\"collection\"") }
        }
    }
}

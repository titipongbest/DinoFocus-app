package com.titipong.dinofocus

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
        // pick up the other phones' data when the app opens
        viewModelScope.launch { delay(1500); syncNow(quiet = true) }
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
    }.let { normalize(it) }

    private fun normalize(d: AppData): AppData {
        var x = d
        if (x.mode != MODE_WORK && x.mode != MODE_SHORT) x = x.copy(mode = MODE_WORK, session = Session())
        if (!Dinos.exists(x.pet)) x = x.copy(pet = "rex")
        if (x.tag != null && x.tags.none { it.name == x.tag }) x = x.copy(tag = null)
        val wr = SliderCfg.range(MODE_WORK); val sr = SliderCfg.range(MODE_SHORT)
        // every round needs an id so syncing never counts it twice (old rounds get a stable one)
        x = x.copy(collection = x.collection.filter { Dinos.exists(it.sp) }).withEntryIds().withWholeMinutes()
        // rebuild daily minutes/rounds from the rounds themselves (also counts older given-up rounds)
        return x.copy(workMin = x.workMin.coerceIn(wr), shortMin = x.shortMin.coerceIn(sr)).recomputed()
    }

    // ── sync: every device merges its rounds into one shared file in a folder they all use ──
    /** Shared folder (tree URI with a persisted permission), e.g. a Google Drive folder. */
    var backupFolder by mutableStateOf(prefs.getString("backupTree", null)?.let { Uri.parse(it) })
        private set
    val backupFolderName: String?
        get() = backupFolder?.let { runCatching { DocumentFile.fromTreeUri(getApplication(), it)?.name }.getOrNull() }
    var lastSync by mutableLongStateOf(prefs.getLong("lastSync", 0L))
        private set
    var syncing by mutableStateOf(false)
        private set
    /** What the shared file held after the last sync (shown in the sync tab to check both phones match). */
    var syncInfo by mutableStateOf(prefs.getString("syncInfo", null))
        private set

    /** Remember the folder, then sync with it. */
    fun chooseBackupFolder(tree: Uri) {
        val cr = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        backupFolder?.takeIf { it != tree }?.let { old -> runCatching { cr.releasePersistableUriPermission(old, flags) } }
        runCatching { cr.takePersistableUriPermission(tree, flags) }
        prefs.edit().putString("backupTree", tree.toString()).apply()
        backupFolder = tree
        syncNow()
    }

    /** This phone's own sync file: phones never overwrite each other's file, so a slow cloud cannot erase data. */
    private val deviceId: String = prefs.getString("deviceId", null)
        ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    private val deviceName: String = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
        .filter { !it.isNullOrBlank() }.distinct().joinToString(" ").ifBlank { "Android" }
    private val ownFile: String =
        "DinoFocus-sync-" + deviceName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(24) + "-" + deviceId.take(6) + ".json"

    private fun isLegacyShared(n: String) = n == SYNC_FILE || Regex("""DinoFocus-sync \(\d+\)\.json""").matches(n)

    /**
     * Each phone writes its own DinoFocus-sync-<phone>.json and reads every phone's file:
     * rounds are merged by id (never counted twice), tags by their latest add/delete,
     * daily totals rebuilt. Returns false when a folder must be picked first.
     * quiet = automatic sync (app start, after a round): only speaks up when something new arrived.
     */
    fun syncNow(quiet: Boolean = false): Boolean {
        val tree = backupFolder ?: return false
        if (syncing) return true
        val app = getApplication<Application>()
        syncing = true
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(app, tree)
                    if (root == null || !root.canWrite()) return@withContext SyncOutcome.NoAccess
                    // all phones keep their files together in a "DinoFocus" folder inside the chosen folder
                    val rootList = root.listFiles()
                    val dir = rootList.firstOrNull { it.isDirectory && it.name == SYNC_DIR }
                        ?: root.createDirectory(SYNC_DIR) ?: return@withContext SyncOutcome.NoAccess
                    fun syncFiles(list: Array<DocumentFile>) = list.filter { f ->
                        val n = f.name ?: return@filter false
                        f.isFile && n.endsWith(".json") && (n.startsWith("DinoFocus-sync") || n == BACKUP_FILE)
                    }
                    // files in the chosen folder itself are from before the sub-folder: read them too
                    val files = syncFiles(dir.listFiles()) + syncFiles(rootList)
                    var remote: AppData? = null
                    val seen = ArrayList<Pair<String, Long>>()
                    files.forEach { f ->
                        val backup = runCatching {
                            app.contentResolver.openInputStream(f.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                                ?.let { Backup.parseBackup(it) }
                        }.getOrNull() ?: return@forEach
                        remote = remote?.let { mergeData(it, backup.data) } ?: backup.data
                        if (f.name != ownFile && backup.device.isNotBlank()) seen.add(backup.device to backup.exportedAt)
                    }
                    SyncOutcome.Read(dir, remote, files, seen)
                }
                when (outcome) {
                    SyncOutcome.NoAccess -> {
                        prefs.edit().remove("backupTree").apply()
                        backupFolder = null
                        if (!quiet) showToast("เข้าถึงโฟลเดอร์เดิมไม่ได้แล้ว เลือกโฟลเดอร์ใหม่")
                    }
                    is SyncOutcome.Read -> {
                        val before = data.collection.size
                        val tagsBefore = data.tags.map { it.name.lowercase() }.toSet()
                        val remote = outcome.remote?.let { normalize(it) }
                        update { merge(it, remote) }
                        val added = data.collection.size - before
                        val newTags = data.tags.count { it.name.lowercase() !in tagsBefore }
                        val stamp = System.currentTimeMillis()
                        val out = Backup(exportedAt = stamp, device = deviceName, data = data.copy(session = Session(), mode = MODE_WORK))
                        withContext(Dispatchers.IO) {
                            val mine = outcome.files.filter { it.name == ownFile && it.parentFile?.uri == outcome.dir.uri }
                            val file = mine.firstOrNull()
                                ?: outcome.dir.createFile("application/json", ownFile) ?: throw IllegalStateException()
                            app.contentResolver.openOutputStream(file.uri, "wt")?.use {
                                it.write(Backup.json.encodeToString(Backup.serializer(), out).toByteArray(Charsets.UTF_8))
                            } ?: throw IllegalStateException()
                            // duplicates of this phone's file (also its old copy outside the folder) and the
                            // old shared file are merged in already
                            outcome.files.filter { f -> f.uri != file.uri && (f.name == ownFile || isLegacyShared(f.name ?: "")) }
                                .forEach { runCatching { it.delete() } }
                        }
                        todaySel = -1
                        lastSync = stamp
                        prefs.edit().putLong("lastSync", lastSync).apply()
                        val others = outcome.seen.groupBy { it.first }.map { (n, v) -> n to v.maxOf { it.second } }
                        syncInfo = (if (others.isEmpty()) "ยังไม่เห็นข้อมูลจากเครื่องอื่น"
                            else "เห็นข้อมูลจาก: " + others.joinToString { (n, t) -> "$n (ซิงค์ ${thDayMonth(dateOf(t))} ${hhmm(t)})" }) +
                            "\nรวม ${data.collection.size} รอบ · tag ${data.tags.size} อัน (${data.tags.joinToString { it.name }})"
                        prefs.edit().putString("syncInfo", syncInfo).apply()
                        val today = todayKey()
                        if (!quiet || added > 0 || newTags > 0) showToast(
                            (if (added > 0) "ซิงค์แล้ว · ได้รอบจากเครื่องอื่น $added รอบ" else "ซิงค์แล้ว · ข้อมูลตรงกันแล้ว") +
                                (if (newTags > 0) " · tag ใหม่ $newTags อัน" else "") +
                                " · วันนี้รวม ${data.minutesOn(today)} นาที"
                        )
                    }
                }
            } catch (e: Exception) {
                if (!quiet) showToast("ซิงค์ไม่สำเร็จ ลองใหม่อีกครั้ง")
            } finally {
                syncing = false
            }
        }
        return true
    }

    private sealed class SyncOutcome {
        data object NoAccess : SyncOutcome()
        class Read(val dir: DocumentFile, val remote: AppData?, val files: List<DocumentFile>, val seen: List<Pair<String, Long>>) : SyncOutcome()
    }

    /** Union of rounds by id; tags merged by name; this device keeps its own settings and running round. */
    private fun merge(local: AppData, remote: AppData?): AppData = mergeData(local, remote)

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
                // the countdown notification itself turns into the "new dino" alert (TimerService)
                if (!TimerService.running) Notifier.showSuccess(getApplication(), Dinos.get(data.pet).name, data.workMin)
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
            // seconds are dropped: a round counts whole minutes only, so rounds always add up to the totals
            val sec = r.workSec / 60 * 60
            val entry = Entry(id = java.util.UUID.randomUUID().toString(), sp = r.sp, ts = nowMs, workSec = sec, targetMin = r.targetMin, tag = r.tag, dead = r.dead)
            var n = d.copy(
                collection = d.collection + entry,
                dailyWorkSec = d.dailyWorkSec + (today to (d.dailyWorkSec[today] ?: 0) + sec),
            )
            // every round counts, a given-up one (grave) too; only the streak needs a living dino
            n = n.copy(
                totalPomos = d.totalPomos + 1,
                dailyPomos = d.dailyPomos + (today to (d.dailyPomos[today] ?: 0) + 1),
            )
            if (!r.dead) {
                if (d.lastDay != today) {
                    val y = LocalDate.parse(today).minusDays(1).toString()
                    n = n.copy(streak = if (d.lastDay == y) d.streak + 1 else 1, lastDay = today)
                }
            }
            n
        }
        todaySel = data.collection.size - 1
        syncNow(quiet = true)   // share the new round with the other phones right away
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
        val now = System.currentTimeMillis()
        update { it.copy(tags = it.tags + Tag(name, color, now), tag = name, deletedTags = it.deletedTags - name.lowercase()) }
        showToast("เพิ่ม tag \"$name\" แล้ว")
        return true
    }

    fun deleteTag(name: String) {
        val now = System.currentTimeMillis()
        update { d ->
            d.copy(
                tags = d.tags.filter { it.name != name },
                tag = if (d.tag == name) null else d.tag,
                deletedTags = d.deletedTags + (name.lowercase() to now),
            )
        }
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

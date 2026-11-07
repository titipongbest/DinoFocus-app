package com.titipong.dinofocus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json

/** Reads the saved app state (same SharedPreferences the ViewModel writes). */
object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun load(ctx: Context): AppData = try {
        ctx.getSharedPreferences("dinofocus", Context.MODE_PRIVATE).getString("data", null)
            ?.let { json.decodeFromString(AppData.serializer(), it) } ?: AppData()
    } catch (e: Exception) {
        AppData()
    }
}

/**
 * Foreground service that keeps the process alive while a focus round or break is running and shows
 * an ongoing notification with a live countdown (count-up after the target, up to 90 minutes).
 * The timer itself is timestamp-based, so even if Android kills everything the time stays correct.
 */
class TimerService : Service() {
    companion object {
        private const val CHANNEL = "focus_timer_v2"

        /** True while the service is alive in this process (it then raises the target alert itself). */
        @Volatile var running = false
        private const val NOTIF_ID = 7

        fun sync(ctx: Context, data: AppData) {
            if (data.session.runStart == null) {
                ctx.stopService(Intent(ctx, TimerService::class.java))
                return
            }
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java))
            } catch (_: Exception) {
                // starting from the background can be refused; the state is saved and resumes on open
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacksAndMessages(null)
        val data = Store.load(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(data),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val s = data.session
        if (s.runStart == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // refresh the notification when the round changes phase (target reached / 90-minute cap)
        val startAt = s.runStart - s.accumulated
        val target = startAt + data.minutesFor(data.mode) * 60_000L
        val now = System.currentTimeMillis()
        listOf(target, startAt + MAX_FOCUS_MS).filter { it > now }.forEach { at ->
            handler.postDelayed({ refresh() }, at - now + 500)
        }
        // already past the target (e.g. app reopened): keep the "+mm:ss" ticking
        if (data.mode == MODE_WORK && now >= target && now < startAt + MAX_FOCUS_MS) handler.post { refresh() }
        return START_STICKY
    }

    private fun refresh() {
        val data = Store.load(this)
        val s = data.session
        val runStart = s.runStart ?: run { stopSelf(); return }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(data))
        // after the target the "+mm:ss" text is ours to update, once per second, until the 90-minute cap
        val startAt = runStart - s.accumulated
        val target = startAt + data.workMin * 60_000L
        val now = System.currentTimeMillis()
        if (data.mode == MODE_WORK && now >= target && now < startAt + MAX_FOCUS_MS) {
            handler.postDelayed({ refresh() }, 1000 - (now - target) % 1000 + 5)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * One notification for the whole round, also on the lock screen. When the target is reached this same
     * notification alerts once (sound + vibration) and then shows "15 +01:16", ticking every second.
     */
    private fun build(data: AppData): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("focus_timer")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "ตัวจับเวลา", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "นับถอยหลังระหว่างโฟกัสและพัก"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val s = data.session
        val runStart = s.runStart
        val startAt = (runStart ?: 0L) - s.accumulated
        val target = startAt + data.minutesFor(data.mode) * 60_000L
        val now = System.currentTimeMillis()
        val overtime = runStart != null && data.mode == MODE_WORK && now >= target

        // the first build past the target alerts; later updates stay quiet
        val marks = getSharedPreferences("dinofocus_ui", MODE_PRIVATE)
        val alertNow = overtime && marks.getLong("alertedStart", 0L) != startAt
        if (alertNow) {
            marks.edit().putLong("alertedStart", startAt).apply()
            Notifier.cancelAlarm(this)
            Notifier.vibrate(this)
        }

        val b = NotificationCompat.Builder(this, if (overtime) Notifier.alertChannel(this) else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dino)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(!alertNow)
            .setSilent(!alertNow)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (overtime) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(if (overtime) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (runStart == null) return b.setContentTitle("DinoFocus").build()
        val pet = Dinos.get(data.pet).name
        val tag = data.tag?.let { " · $it" } ?: ""

        if (data.mode == MODE_WORK) {
            val overSec = (minOf(now, startAt + MAX_FOCUS_MS) - target) / 1000
            when {
                now < target -> b.setContentTitle("🦖 โฟกัสอยู่ · $pet")
                    .setContentText("อดทนไว้! ไดโนเสาร์จะฟักเวลา ${hhmm(target)}$tag")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(target).setShowWhen(true)
                now < startAt + MAX_FOCUS_MS -> b.setContentTitle("ไดโนเสาร์ใหม่สำเร็จ! 🦖")
                    .setContentText("${data.workMin} +${fmtClock(overSec)} นาที · แตะเพื่อเสร็จสิ้น$tag")
                    .setShowWhen(false)
                else -> b.setContentTitle("ไดโนเสาร์ใหม่สำเร็จ! 🦖 ครบ 90 นาที")
                    .setContentText("${data.workMin} +${fmtClock(overSec)} นาที · แตะเพื่อบันทึก$tag")
                    .setShowWhen(false)
            }
        } else {
            if (now < target) {
                b.setContentTitle("☕ พักสั้นอยู่")
                    .setContentText("พักให้สบาย! กลับมาโฟกัสเวลา ${hhmm(target)}")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(target).setShowWhen(true)
            } else {
                b.setContentTitle("พักเสร็จแล้ว!").setContentText("กลับมาโฟกัสกัน 💪")
            }
        }
        return b.build()
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }
}

/** After a reboot: put the countdown notification and the "new dino" alarm back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val d = Store.load(ctx)
        val s = d.session
        val runStart = s.runStart ?: return
        val target = runStart - s.accumulated + d.minutesFor(d.mode) * 60_000L
        if (target > System.currentTimeMillis()) {
            if (d.mode == MODE_WORK && !s.notified) Notifier.scheduleSuccess(ctx, target, Dinos.get(d.pet).name, d.workMin)
            else if (d.mode == MODE_SHORT) Notifier.scheduleBreakDone(ctx, target, d.shortMin)
        }
        TimerService.sync(ctx, d)
    }
}

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
        private const val CHANNEL = "focus_timer"
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
        return START_STICKY
    }

    private fun refresh() {
        val data = Store.load(this)
        if (data.session.runStart == null) { stopSelf(); return }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(data))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun build(data: AppData): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "ตัวจับเวลา", NotificationManager.IMPORTANCE_LOW).apply {
                description = "นับถอยหลังระหว่างโฟกัสและพัก"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dino)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val s = data.session
        val runStart = s.runStart ?: return b.setContentTitle("DinoFocus").build()
        val startAt = runStart - s.accumulated
        val target = startAt + data.minutesFor(data.mode) * 60_000L
        val now = System.currentTimeMillis()
        val pet = Dinos.get(data.pet).name
        val tag = data.tag?.let { " · $it" } ?: ""

        if (data.mode == MODE_WORK) {
            when {
                now < target -> b.setContentTitle("🦖 โฟกัสอยู่ · $pet")
                    .setContentText("อดทนไว้! ไดโนเสาร์จะฟักเวลา ${hhmm(target)}$tag")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(target).setShowWhen(true)
                now < startAt + MAX_FOCUS_MS -> b.setContentTitle("ไดโนเสาร์ใหม่สำเร็จ! 🦖 นับเวลาต่อ")
                    .setContentText("แตะเพื่อเสร็จสิ้นและบันทึก · นับได้ถึง 90 นาที$tag")
                    .setUsesChronometer(true).setWhen(startAt).setShowWhen(true)
                else -> b.setContentTitle("ครบ 90 นาทีแล้ว")
                    .setContentText("แตะเพื่อบันทึก $pet พร้อมเวลา$tag")
            }
        } else {
            if (now < target) {
                b.setContentTitle("☕ พักสั้น")
                    .setContentText("กลับมาโฟกัสเวลา ${hhmm(target)}")
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(target).setShowWhen(true)
            } else {
                b.setContentTitle("พักเสร็จแล้ว!").setContentText("กลับมาโฟกัสกัน 💪")
            }
        }
        return b.build()
    }
}

/** After a reboot: put the countdown notification and the "new dino" alarm back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val d = Store.load(ctx)
        val s = d.session
        val runStart = s.runStart ?: return
        if (d.mode == MODE_WORK && !s.notified) {
            val target = runStart - s.accumulated + d.workMin * 60_000L
            if (target > System.currentTimeMillis()) Notifier.scheduleSuccess(ctx, target, Dinos.get(d.pet).name, d.workMin)
        }
        TimerService.sync(ctx, d)
    }
}

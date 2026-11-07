package com.titipong.dinofocus

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** "New dino" notification at the moment the focus target is reached, even if the app is closed. */
object Notifier {
    // new channel id: Android keeps a channel's settings forever, the old one had no vibration
    private const val CHANNEL = "focus_alert_v2"
    private const val OLD_CHANNEL = "focus_done"
    private val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)
    private const val REQ = 42
    private const val NOTIF_ID = 1

    private fun alarmIntent(ctx: Context, kind: String = "", name: String = "", minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ,
            Intent(ctx, SuccessReceiver::class.java).putExtra("kind", kind).putExtra("name", name).putExtra("min", minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(ctx: Context, atMs: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    /** Alert when the focus target is reached. */
    fun scheduleSuccess(ctx: Context, atMs: Long, dinoName: String, minutes: Int) =
        schedule(ctx, atMs, alarmIntent(ctx, "focus", dinoName, minutes))

    /** Alert when the short break is over. */
    fun scheduleBreakDone(ctx: Context, atMs: Long, minutes: Int) =
        schedule(ctx, atMs, alarmIntent(ctx, "break", "", minutes))

    fun cancelAlarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
    }

    /** Cancel the pending alarm and remove an alert that is showing. */
    fun cancelSuccess(ctx: Context) {
        cancelAlarm(ctx)
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    fun showBreakDone(ctx: Context, minutes: Int) = post(
        ctx,
        "พักเสร็จแล้ว! ☕",
        "ครบ $minutes นาที ได้เวลากลับมาโฟกัส กด Start Focus เพื่อเริ่มรอบใหม่",
    )

    fun showSuccess(ctx: Context, dinoName: String, minutes: Int) = post(
        ctx,
        "ไดโนเสาร์ใหม่สำเร็จ! 🦖",
        "$dinoName ฟักแล้ว · ครบ $minutes นาที\nนับเวลาต่อได้ถึง 90 นาที กดเสร็จสิ้นเพื่อบันทึก",
    )

    private fun post(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "ครบเวลาโฟกัส / พัก", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "แจ้งเตือนพร้อมสั่นเมื่อได้ไดโนเสาร์ใหม่หรือพักเสร็จ"
                enableVibration(true)
                vibrationPattern = PATTERN
                enableLights(true)
            }
        )
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dino)
            .setContentTitle(title)
            .setContentText(text.replace("\n", " "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
        vibrate(ctx)
    }

    /** Vibrate directly too, so it buzzes even when the phone mutes notification vibration. */
    private fun vibrate(ctx: Context) {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(android.os.Vibrator::class.java)
            } ?: return
            if (!v.hasVibrator()) return
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            v.vibrate(android.os.VibrationEffect.createWaveform(PATTERN, -1), attrs)
        } catch (_: Exception) {
        }
    }
}

class SuccessReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val min = intent.getIntExtra("min", 0)
        if (intent.getStringExtra("kind") == "break") Notifier.showBreakDone(ctx, min)
        else Notifier.showSuccess(ctx, intent.getStringExtra("name") ?: "ไดโนเสาร์", min)
    }
}

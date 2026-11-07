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
    private const val CHANNEL = "focus_done"
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
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "โฟกัสสำเร็จ", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "แจ้งเตือนเมื่อครบเวลาโฟกัสและได้ไดโนเสาร์ใหม่"
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
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class SuccessReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val min = intent.getIntExtra("min", 0)
        if (intent.getStringExtra("kind") == "break") Notifier.showBreakDone(ctx, min)
        else Notifier.showSuccess(ctx, intent.getStringExtra("name") ?: "ไดโนเสาร์", min)
    }
}

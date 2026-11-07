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

    private fun alarmIntent(ctx: Context, name: String = "", minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ,
            Intent(ctx, SuccessReceiver::class.java).putExtra("name", name).putExtra("min", minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleSuccess(ctx: Context, atMs: Long, dinoName: String, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, alarmIntent(ctx, dinoName, minutes))
    }

    fun cancelSuccess(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    fun show(ctx: Context, dinoName: String, minutes: Int) {
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
            .setContentTitle("ไดโนเสาร์ใหม่สำเร็จ! 🦖")
            .setContentText("$dinoName ฟักแล้ว · ครบ $minutes นาที นับเวลาต่อได้ถึง 90 นาที กดเสร็จสิ้นเพื่อบันทึก")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$dinoName ฟักแล้ว · ครบ $minutes นาที\nนับเวลาต่อได้ถึง 90 นาที กดเสร็จสิ้นเพื่อบันทึก"))
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
        Notifier.show(ctx, intent.getStringExtra("name") ?: "ไดโนเสาร์", intent.getIntExtra("min", 0))
    }
}

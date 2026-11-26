package com.quicknap.app.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quicknap.app.MainActivity
import com.quicknap.app.R

/**
 * 自動で眠らせられなかったときの通知と、Shizuku 停止中に設定画面で起こしたアプリを開く通知。
 * 後者は、裏にいる Quicknap からは OS の制限でアプリを直接開けないため、通知のタップで開いてもらう
 */
class AutoSleepNotifier(private val context: Context) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "自動で眠らせられなかったとき",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WOKEN_CHANNEL_ID,
                "設定画面で起こしたアプリを開く",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    /** 設定画面で有効にされたアプリを、通知のタップでそのまま開けるようにする */
    fun notifyWoken(packageName: String, label: String) {
        if (!canNotify()) return
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        ensureChannel()
        val openTarget = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, WOKEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle("${label}を起こしました")
            .setContentText("タップして開く")
            .setContentIntent(openTarget)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(WOKEN_TIMEOUT_MS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(WOKEN_NOTIFICATION_ID, notification) }
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyFailed() {
        if (!canNotify()) return
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle("アプリを自動で眠らせられませんでした")
            .setContentText("Shizuku が停止中です。開いて再開すると、まとめて眠らせます。")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_ID = "auto_sleep_failed"
        const val NOTIFICATION_ID = 1
        const val WOKEN_CHANNEL_ID = "manual_woken"
        const val WOKEN_NOTIFICATION_ID = 2
        /** 開かれないまま残らないよう、しばらくしたら消す */
        const val WOKEN_TIMEOUT_MS = 2 * 60 * 1000L
    }
}

package com.quicknap.app.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.quicknap.app.QuicknapApp
import com.quicknap.app.widget.QuicknapWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shizuku 停止中に「設定で有効化」したアプリを見張り、Android の設定画面で有効にされたら
 * Quicknap に戻らなくても開けるよう、タップで開く通知を出す（裏から直接開くのは OS が止めるため）。
 * 見張るのは開いてから一定時間だけ
 */
class ManualWakeWatcher(private val app: QuicknapApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingPackage: String? = null
    private var deadline = 0L
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg != pendingPackage) return
            if (SystemClock.elapsedRealtime() > deadline) {
                stop()
                return
            }
            if (app.installed.enabledState(pkg) != true) return
            stop()
            val label = runCatching {
                app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            app.notifier.notifyWoken(pkg, label)
            scope.launch {
                app.sleepController.markWoken(pkg)
                QuicknapWidgetProvider.updateAll(app)
            }
        }
    }

    /** アプリ情報画面を開く直前に呼ぶ */
    fun watch(packageName: String) {
        pendingPackage = packageName
        deadline = SystemClock.elapsedRealtime() + WATCH_MS
        if (registered) return
        val filter = IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    private fun stop() {
        pendingPackage = null
        if (!registered) return
        runCatching { app.unregisterReceiver(receiver) }
        registered = false
    }

    private companion object {
        /** 設定画面で「有効にする」を押すまでの猶予 */
        const val WATCH_MS = 3 * 60 * 1000L
    }
}

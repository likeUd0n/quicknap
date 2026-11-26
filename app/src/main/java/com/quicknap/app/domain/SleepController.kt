package com.quicknap.app.domain

import com.quicknap.app.data.DefaultCategoryClassifier
import com.quicknap.app.data.InstalledAppRepository
import com.quicknap.app.data.ManagedApp
import com.quicknap.app.data.ManagedAppStore
import com.quicknap.app.data.SleepReason
import com.quicknap.app.data.UsageRepository
import com.quicknap.app.shizuku.ShizukuPackageController
import com.quicknap.app.shizuku.ShizukuStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 操作前の状態。トーストの「元に戻す」でこの状態に戻す */
data class AppSnapshot(
    val packageName: String,
    val record: ManagedApp?,
    val enabled: Boolean,
)

enum class AutoSleepOutcome { NothingToDo, Slept, Failed }

/**
 * 眠らせる・起こす・自動で眠らせるの手順をまとめる。画面と WorkManager の両方から使う。
 */
class SleepController(
    private val store: ManagedAppStore,
    private val installed: InstalledAppRepository,
    private val usage: UsageRepository,
    private val shizuku: ShizukuPackageController,
    private val notifier: AutoSleepNotifier,
    private val classifier: DefaultCategoryClassifier,
) {
    private val autoSleepMutex = Mutex()

    suspend fun snapshot(packageName: String): AppSnapshot = AppSnapshot(
        packageName = packageName,
        record = store.current().managedApps[packageName],
        enabled = installed.enabledState(packageName) ?: true,
    )

    /** 手動で眠らせる（選ぶ画面でチェック・一覧のトグル OFF） */
    suspend fun sleepManually(packageName: String): Result<Unit> =
        shizuku.setPackageEnabled(packageName, enabled = false).onSuccess {
            val state = store.current()
            val prev = state.managedApps[packageName]
            val categoryId = prev?.categoryId ?: run {
                val predicted = classifier.classify(packageName)
                if (predicted != null && state.categories.any { it.id == predicted }) predicted else null
            }
            store.put(
                ManagedApp(
                    packageName = packageName,
                    sleepReason = SleepReason.SELF,
                    sleepThresholdDaysAtSleep = null,
                    sleptAt = now(),
                    lastWokenAt = prev?.lastWokenAt,
                    categoryId = categoryId,
                    wakeCount = prev?.wakeCount ?: 0,
                )
            )
        }

    /** 起こす（行タップ・一覧のトグル ON） */
    suspend fun wake(packageName: String): Result<Unit> =
        shizuku.setPackageEnabled(packageName, enabled = true).onSuccess {
            markWoken(packageName)
        }

    /** 起きているアプリを Quicknap から開いたとき。OS の履歴が取れなくても直後に眠らないようにする */
    suspend fun markWoken(packageName: String) {
        val prev = store.current().managedApps[packageName] ?: return
        store.put(
            prev.copy(
                sleepReason = null,
                sleepThresholdDaysAtSleep = null,
                sleptAt = null,
                lastWokenAt = now(),
                wakeCount = prev.wakeCount + 1,
            )
        )
    }

    /** 選ぶ画面でチェックを外す（対象から外す）。起きていれば Shizuku は不要 */
    suspend fun release(packageName: String): Result<Unit> {
        if (installed.enabledState(packageName) == false) {
            val result = shizuku.setPackageEnabled(packageName, enabled = true)
            if (result.isFailure) return result
        }
        store.remove(packageName)
        return Result.success(Unit)
    }

    /** 「元に戻す」：OS の有効状態と記録を操作前に戻す */
    suspend fun restore(snapshot: AppSnapshot): Result<Unit> {
        val current = installed.enabledState(snapshot.packageName)
        if (current != null && current != snapshot.enabled) {
            val result = shizuku.setPackageEnabled(snapshot.packageName, snapshot.enabled)
            if (result.isFailure) return result
        }
        val record = snapshot.record
        if (record == null) store.remove(snapshot.packageName) else store.put(record)
        return Result.success(Unit)
    }

    suspend fun setAutoSleepDays(days: Int) = store.setAutoSleepDays(days)

    /**
     * OS の状態と記録をそろえる。
     * - アンインストール済みは静かに削除
     * - Quicknap の外で有効にされたら lastWokenAt を補完して「最近起こした」へ（直後に眠り返さない）
     * - Quicknap の外で無効にされたら「手動」で眠ったものとして扱う
     */
    suspend fun reconcile() {
        val now = now()
        store.updateApps { apps ->
            apps.mapNotNull { (pkg, app) ->
                when (installed.enabledState(pkg)) {
                    null -> null
                    true -> if (app.sleptAt != null || app.lastWokenAt == null) {
                        pkg to app.copy(
                            sleepReason = null,
                            sleepThresholdDaysAtSleep = null,
                            sleptAt = null,
                            lastWokenAt = now,
                        )
                    } else {
                        pkg to app
                    }
                    false -> if (app.sleptAt == null) {
                        pkg to app.copy(sleepReason = SleepReason.SELF, sleptAt = now)
                    } else {
                        pkg to app
                    }
                }
            }.toMap()
        }
    }

    /** 最後に使った日時 = max(Quicknap で起こした日時, OS の使用履歴) */
    fun lastUsedAt(app: ManagedApp, usageMap: Map<String, Long>): Long =
        maxOf(app.lastWokenAt ?: 0L, usageMap[app.packageName] ?: 0L)

    /**
     * 期限を過ぎた「最近起こした」アプリをまとめて眠らせる。
     * 眠らせられなかったとき（Shizuku 停止など）だけ1回通知し、Shizuku 復帰＋成功でフラグを戻す。
     */
    suspend fun runAutoSleep(shizukuWaitMs: Long = 0L): AutoSleepOutcome {
        // Shizuku の起動待ちはロック外で行い、画面側（onResume）を最大5秒待たせない
        val waitedStatus = if (shizukuWaitMs > 0) shizuku.awaitStatus(shizukuWaitMs) else null
        return autoSleepMutex.withLock {
            reconcile()
            val state = store.current()
            val usageMap = usage.lastUsedMap()
            val now = now()
            val limitMs = state.autoSleepDays * DAY_MS
            val overdue = state.managedApps.values.filter { app ->
                installed.enabledState(app.packageName) == true &&
                    now - lastUsedAt(app, usageMap) >= limitMs
            }

            val status = waitedStatus ?: shizuku.refreshStatus()
            if (overdue.isEmpty()) {
                if (status == ShizukuStatus.Ready && state.failedAutoSleepNotified) {
                    store.setFailedAutoSleepNotified(false)
                }
                return@withLock AutoSleepOutcome.NothingToDo
            }

            var failed = status != ShizukuStatus.Ready
            if (!failed) {
                for (app in overdue) {
                    val result = shizuku.setPackageEnabled(app.packageName, enabled = false)
                    if (result.isSuccess) {
                        store.put(
                            app.copy(
                                sleepReason = SleepReason.AUTO,
                                sleepThresholdDaysAtSleep = state.autoSleepDays,
                                sleptAt = now,
                            )
                        )
                    } else {
                        failed = true
                    }
                }
            }

            if (failed) {
                if (!state.failedAutoSleepNotified) {
                    notifier.notifyFailed()
                    store.setFailedAutoSleepNotified(true)
                }
                AutoSleepOutcome.Failed
            } else {
                if (state.failedAutoSleepNotified) store.setFailedAutoSleepNotified(false)
                notifier.cancel()
                AutoSleepOutcome.Slept
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

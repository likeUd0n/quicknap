package com.quicknap.app.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android の使用履歴（UsageStatsManager）から各アプリの最終利用日時を読む */
class UsageRepository(private val context: Context) {

    @Volatile
    private var cachedMap: Map<String, Long>? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** メモリにキャッシュ済みの使用履歴を同期で返す（未取得なら空） */
    fun cachedLastUsedMap(): Map<String, Long> = cachedMap ?: emptyMap()

    /** パッケージ名 → 最終利用日時（ミリ秒）。権限がなければ空。短時間はメモリキャッシュを再利用する */
    suspend fun lastUsedMap(forceRefresh: Boolean = false): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasAccess()) {
            cachedMap = null
            return@withContext emptyMap()
        }
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cachedMap
            if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
                return@withContext cached
            }
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext emptyMap()
        val fresh = runCatching {
            usm.queryAndAggregateUsageStats(now - LOOKBACK_MS, now)
                .mapValues { it.value.lastTimeUsed }
                .filterValues { it > 0L }
        }.getOrDefault(emptyMap())
        cachedMap = fresh
        cachedAtMs = now
        fresh
    }

    private companion object {
        const val LOOKBACK_MS = 120L * 24 * 60 * 60 * 1000
        const val CACHE_TTL_MS = 20_000L
    }
}

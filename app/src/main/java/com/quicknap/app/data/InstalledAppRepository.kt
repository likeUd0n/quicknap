package com.quicknap.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isEnabled: Boolean,
    val icon: ImageBitmap?,
)

class InstalledAppRepository(private val context: Context) {

    private data class CachedAppMeta(
        val label: String,
        val icon: ImageBitmap?,
        val lastKnownEnabled: Boolean,
    )

    private val pm: PackageManager = context.packageManager
    private val metaCache = ConcurrentHashMap<String, CachedAppMeta>()

    /** メモリにキャッシュ済みの管理対象アプリを同期で返す（フォルダの即時表示用） */
    fun cachedManagedApps(packages: Collection<String>): List<LaunchableApp> {
        if (packages.isEmpty()) return emptyList()
        val result = ArrayList<LaunchableApp>(packages.size)
        for (pkg in packages) {
            val meta = metaCache[pkg] ?: continue
            result.add(
                LaunchableApp(
                    packageName = pkg,
                    label = meta.label,
                    isEnabled = meta.lastKnownEnabled,
                    icon = meta.icon,
                )
            )
        }
        return result
    }

    /** 指定した管理対象パッケージだけを読み込む（全アプリ走査を避けて高速に表示する） */
    suspend fun loadManagedApps(packages: Collection<String>): List<LaunchableApp> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyList()
        val result = ArrayList<LaunchableApp>(packages.size)
        for (pkg in packages) {
            val appInfo = getApplicationInfoOrNull(pkg) ?: continue
            val meta = loadMeta(pkg, appInfo)
            result.add(
                LaunchableApp(
                    packageName = pkg,
                    label = meta.label,
                    isEnabled = appInfo.enabled,
                    icon = meta.icon,
                )
            )
        }
        result.sortedWith(
            compareBy<LaunchableApp> { it.isEnabled }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    suspend fun loadUserLaunchableApps(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // 無効化（pm disable-user）済みアプリも含めてランチャー対象を取得する
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, flags)
        }

        val seen = HashSet<String>()
        val result = ArrayList<LaunchableApp>()

        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            if (pkg in EXCLUDED_PACKAGES) continue

            val appInfo = getApplicationInfoOrNull(pkg) ?: continue

            // システムアプリは除外（ユーザーインストールのみ）
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val meta = loadMeta(pkg, appInfo)
            result.add(
                LaunchableApp(
                    packageName = pkg,
                    label = meta.label,
                    isEnabled = appInfo.enabled,
                    icon = meta.icon,
                )
            )
        }

        result.sortedWith(
            compareBy<LaunchableApp> { it.isEnabled }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    private fun loadMeta(pkg: String, appInfo: ApplicationInfo): CachedAppMeta {
        metaCache[pkg]?.let { cached ->
            if (cached.lastKnownEnabled != appInfo.enabled) {
                val updated = cached.copy(lastKnownEnabled = appInfo.enabled)
                metaCache[pkg] = updated
                return updated
            }
            return cached
        }
        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(pkg)
        val icon = runCatching { pm.getApplicationIcon(appInfo).toImageBitmap(ICON_SIZE_PX) }
            .getOrNull()
        return CachedAppMeta(label, icon, appInfo.enabled).also { metaCache[pkg] = it }
    }

    /** 有効なら true、無効（眠っている）なら false、アンインストール済みなら null */
    fun enabledState(packageName: String): Boolean? = getApplicationInfoOrNull(packageName)?.enabled

    private fun getApplicationInfoOrNull(packageName: String): ApplicationInfo? = runCatching {
        // 無効化（pm disable-user）済みアプリも取得できるようにする
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, flags)
        }
    }.getOrNull()

    /**
     * 有効化直後のアプリを待機なしで起動する。
     * getLaunchIntentForPackage() が null を返した場合のみ 100ms × 最大3回リトライ。
     */
    suspend fun launchAppWithRetry(packageName: String): Result<Unit> {
        var intent = pm.getLaunchIntentForPackage(packageName)
        var attempts = 0
        while (intent == null && attempts < MAX_LAUNCH_RETRIES) {
            delay(LAUNCH_RETRY_DELAY_MS)
            intent = pm.getLaunchIntentForPackage(packageName)
            attempts++
        }

        val target = intent ?: return Result.failure(
            IllegalStateException("起動用の画面が見つかりませんでした ($packageName)")
        )
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(target)
        }
    }

    fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openShizukuApp(): Boolean {
        val intent = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isShizukuInstalled(): Boolean = getApplicationInfoOrNull(SHIZUKU_PACKAGE) != null

    /** HyperOS の「自動起動」「バッテリー節約」は Shizuku のアプリ情報画面から変える */
    fun openShizukuDetails() = openAppDetails(SHIZUKU_PACKAGE)

    fun openUsageAccessSettings() {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(withPackage) }.isSuccess
        if (!opened) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val ICON_SIZE_PX = 96
        private const val LAUNCH_RETRY_DELAY_MS = 100L
        private const val MAX_LAUNCH_RETRIES = 3

        private val EXCLUDED_PACKAGES = setOf(
            "com.quicknap.app",
            SHIZUKU_PACKAGE,
        )
    }
}

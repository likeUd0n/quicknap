package com.quicknap.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.quicknap.app.QuicknapApp
import com.quicknap.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ホーム画面のピン留めショートカット（フォルダアイコン）の作成・設定保存・動的更新を管理する。
 * ウィジェットと異なり通常アイコンと同じセル枠に配置されるため、HyperOS 等でも位置・サイズが完全に揃う。
 */
object QuicknapShortcutManager {
    private const val PREF_NAME = "quicknap_shortcuts"
    private const val KEY_CAT_PREFIX = "cat_"
    private const val KEY_SHOW_LABEL_PREFIX = "show_label_"
    private const val SHORTCUT_ID_PREFIX = "qn_folder_"
    private const val INVISIBLE_LABEL = "\u200B"
    private const val ADAPTIVE_ICON_PX = 216
    private val FOLDER_BG_COLOR = Color.parseColor("#4DFFFFFF")

    const val DEFAULT_SHOW_LABEL = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun saveSettings(
        context: Context,
        shortcutId: String,
        categoryId: String?,
        showLabel: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (categoryId == null) remove(KEY_CAT_PREFIX + shortcutId)
            else putString(KEY_CAT_PREFIX + shortcutId, categoryId)
            putBoolean(KEY_SHOW_LABEL_PREFIX + shortcutId, showLabel)
        }.apply()
    }

    fun getCategory(context: Context, shortcutId: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CAT_PREFIX + shortcutId, null)
    }

    fun getShowLabel(context: Context, shortcutId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_LABEL_PREFIX + shortcutId, DEFAULT_SHOW_LABEL)
    }

    /** 新しいピン留めショートカットをホーム画面へ追加する */
    suspend fun pinShortcut(
        context: Context,
        categoryId: String?,
        showLabel: Boolean,
    ): Boolean {
        val appCtx = context.applicationContext
        if (!isSupported(appCtx)) return false
        val shortcutId = SHORTCUT_ID_PREFIX + UUID.randomUUID().toString().take(8)
        saveSettings(appCtx, shortcutId, categoryId, showLabel)
        val info = withContext(Dispatchers.IO) {
            buildShortcutInfo(appCtx, shortcutId, categoryId, showLabel)
        } ?: return false
        return ShortcutManagerCompat.requestPinShortcut(appCtx, info, null)
    }

    /** 指定したピン留めショートカットのアイコン・ラベル・Intent を最新設定で更新する */
    fun updateShortcut(context: Context, shortcutId: String) {
        val appCtx = context.applicationContext
        scope.launch {
            val categoryId = getCategory(appCtx, shortcutId)
            val showLabel = getShowLabel(appCtx, shortcutId)
            val info = buildShortcutInfo(appCtx, shortcutId, categoryId, showLabel) ?: return@launch
            ShortcutManagerCompat.updateShortcuts(appCtx, listOf(info))
        }
    }

    /** ホーム画面に残っているすべての Quicknap ピン留めショートカットを最新状態へ同期する */
    fun updateAll(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            val pinned = ShortcutManagerCompat.getShortcuts(
                appCtx,
                ShortcutManagerCompat.FLAG_MATCH_PINNED,
            ).filter { it.id.startsWith(SHORTCUT_ID_PREFIX) }
            if (pinned.isEmpty()) return@launch

            val updated = pinned.mapNotNull { existing ->
                val categoryId = getCategory(appCtx, existing.id)
                val showLabel = getShowLabel(appCtx, existing.id)
                buildShortcutInfo(appCtx, existing.id, categoryId, showLabel)
            }
            if (updated.isNotEmpty()) {
                ShortcutManagerCompat.updateShortcuts(appCtx, updated)
            }
        }
    }

    private suspend fun buildShortcutInfo(
        context: Context,
        shortcutId: String,
        savedCategoryId: String?,
        showLabel: Boolean,
    ): ShortcutInfoCompat? {
        val app = context.applicationContext as? QuicknapApp ?: return null
        val state = app.store.current()
        val matchedCategory = savedCategoryId?.let { id ->
            state.categories.firstOrNull { it.id == id }
        }
        val effectiveCategoryId = matchedCategory?.id
        val label = if (showLabel) (matchedCategory?.name ?: "Quicknap") else INVISIBLE_LABEL

        val candidates = state.managedApps.values.filter { record ->
            (effectiveCategoryId == null || record.categoryId == effectiveCategoryId) &&
                app.installed.enabledState(record.packageName) != null
        }

        val pm = context.packageManager
        val labelCache = candidates.associate { record ->
            val appLabel = runCatching {
                val info = pm.getApplicationInfo(record.packageName, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(record.packageName)
            record.packageName to appLabel
        }

        val sorted = QuicknapWidgetProvider.sortForWidget(
            candidates,
            state.asleepSortOrder,
            state.manualAsleepOrder,
            labelCache,
        )
        val top4Packages = sorted.take(4).map { it.packageName }
        val bmp = buildFolderBitmap(context, top4Packages)

        val launchIntent = Intent(context, WidgetFolderActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(WidgetFolderActivity.EXTRA_SHORTCUT_ID, shortcutId)
            putExtra(WidgetFolderActivity.EXTRA_CATEGORY_ID, effectiveCategoryId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(bmp))
            .setIntent(launchIntent)
            .build()
    }

    private fun buildFolderBitmap(context: Context, top4Packages: List<String>): Bitmap {
        val size = ADAPTIVE_ICON_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOLDER_BG_COLOR }
        // Adaptive Icon はランチャーが角丸マスクをかけるため全面を半透明背景で塗る
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Adaptive Icon の安全領域（中央 66%）に合わせて 2×2 ミニアイコンを配置する
        val inset = size * 0.22f
        val area = size - inset * 2f

        if (top4Packages.isEmpty()) {
            val emptyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
            if (emptyDrawable != null) {
                emptyDrawable.setBounds(
                    inset.toInt(),
                    inset.toInt(),
                    (inset + area).toInt(),
                    (inset + area).toInt(),
                )
                emptyDrawable.draw(canvas)
            }
            return bmp
        }

        val pm = context.packageManager
        val gap = size * 0.04f
        val cell = (area - gap) / 2f
        for (i in 0 until 4) {
            val pkg = top4Packages.getOrNull(i) ?: continue
            val col = i % 2
            val row = i / 2
            val left = (inset + col * (cell + gap)).toInt()
            val top = (inset + row * (cell + gap)).toInt()
            val right = (left + cell).toInt()
            val bottom = (top + cell).toInt()
            runCatching {
                val drawable = pm.getApplicationIcon(pkg)
                drawable.setBounds(left, top, right, bottom)
                drawable.draw(canvas)
            }
        }
        return bmp
    }
}

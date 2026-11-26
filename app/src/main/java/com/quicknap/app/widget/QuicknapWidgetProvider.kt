package com.quicknap.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.quicknap.app.QuicknapApp
import com.quicknap.app.R
import com.quicknap.app.data.AsleepSortOrder
import com.quicknap.app.data.ManagedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ホーム画面の 1×1 コンパクトフォルダウィジェット。
 * 指定カテゴリ（または「すべて」）の対象アプリ上位4件を 2×2 ミニアイコンで表示し、
 * タップでフォルダポップアップ（WidgetFolderActivity）を開く。
 */
class QuicknapWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                for (id in appWidgetIds) {
                    renderWidget(context.applicationContext, appWidgetManager, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                renderWidget(context.applicationContext, appWidgetManager, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            WidgetPrefs.delete(context, id)
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val MINI_ICON_PX = 64
        private const val FALLBACK_CELL_DP = 56f
        private const val LABEL_HEIGHT_DP = 14f
        private val SLOT_IDS = intArrayOf(R.id.icon_0, R.id.icon_1, R.id.icon_2, R.id.icon_3)

        fun updateAll(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, QuicknapWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scope.launch {
                for (id in ids) {
                    renderWidget(appCtx, manager, id)
                }
            }
        }

        fun updateWidget(context: Context, appWidgetId: Int) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            scope.launch {
                renderWidget(appCtx, manager, appWidgetId)
            }
        }

        private suspend fun renderWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val app = context.applicationContext as? QuicknapApp ?: return
            val state = app.store.current()
            val savedCategoryId = WidgetPrefs.getCategory(context, appWidgetId)
            val matchedCategory = savedCategoryId?.let { id ->
                state.categories.firstOrNull { it.id == id }
            }
            val effectiveCategoryId = matchedCategory?.id
            val label = matchedCategory?.name ?: "Quicknap"

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

            val sorted = sortForWidget(candidates, state.asleepSortOrder, labelCache)
            val top4 = sorted.take(4)

            val views = RemoteViews(context.packageName, R.layout.widget_compact_folder)
            val showLabel = WidgetPrefs.getShowLabel(context, appWidgetId)
            val scalePercent = WidgetPrefs.getIconScalePercent(context, appWidgetId)
            val verticalAlign = WidgetPrefs.getVerticalAlign(context, appWidgetId)

            views.setTextViewText(R.id.widget_label, label)
            views.setViewVisibility(R.id.widget_label, if (showLabel) View.VISIBLE else View.GONE)

            val padPx = computeFramePaddingPx(context, manager, appWidgetId, showLabel, scalePercent)
            views.setViewPadding(R.id.folder_frame, padPx, padPx, padPx, padPx)
            val frameGravity = when (verticalAlign) {
                WidgetVerticalAlign.TOP -> Gravity.CENTER_HORIZONTAL or Gravity.TOP
                WidgetVerticalAlign.CENTER -> Gravity.CENTER
                WidgetVerticalAlign.BOTTOM -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            }
            views.setInt(R.id.folder_frame, "setGravity", frameGravity)

            if (top4.isEmpty()) {
                views.setViewVisibility(R.id.icon_grid, View.GONE)
                views.setViewVisibility(R.id.icon_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.icon_grid, View.VISIBLE)
                views.setViewVisibility(R.id.icon_empty, View.GONE)
                for (i in SLOT_IDS.indices) {
                    val slotId = SLOT_IDS[i]
                    val record = top4.getOrNull(i)
                    if (record == null) {
                        views.setViewVisibility(slotId, View.INVISIBLE)
                    } else {
                        val bmp = runCatching {
                            pm.getApplicationIcon(record.packageName).toMiniBitmap(MINI_ICON_PX)
                        }.getOrNull()
                        if (bmp != null) {
                            views.setImageViewBitmap(slotId, bmp)
                            views.setViewVisibility(slotId, View.VISIBLE)
                        } else {
                            views.setViewVisibility(slotId, View.INVISIBLE)
                        }
                    }
                }
            }

            val tapIntent = Intent(context, WidgetFolderActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(WidgetFolderActivity.EXTRA_CATEGORY_ID, effectiveCategoryId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun sortForWidget(
            apps: List<ManagedApp>,
            order: AsleepSortOrder,
            labels: Map<String, String>,
        ): List<ManagedApp> = when (order) {
            AsleepSortOrder.FREQUENCY -> apps.sortedWith(
                compareByDescending<ManagedApp> { it.wakeCount }
                    .thenByDescending { it.lastWokenAt ?: 0L }
                    .thenBy { labels[it.packageName]?.lowercase() ?: it.packageName }
            )
            AsleepSortOrder.RECENT -> apps.sortedWith(
                compareByDescending<ManagedApp> { it.lastWokenAt ?: 0L }
                    .thenBy { labels[it.packageName]?.lowercase() ?: it.packageName }
            )
            AsleepSortOrder.NAME -> apps.sortedBy {
                labels[it.packageName]?.lowercase() ?: it.packageName
            }
        }

        private fun computeFramePaddingPx(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            showLabel: Boolean,
            scalePercent: Int,
        ): Int {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
            val widthDp = if (minW > 0f) minW else FALLBACK_CELL_DP
            val rawHeightDp = if (maxH > 0f) maxH else FALLBACK_CELL_DP
            val availHeightDp = if (showLabel) (rawHeightDp - LABEL_HEIGHT_DP).coerceAtLeast(36f) else rawHeightDp
            val shortSideDp = min(widthDp, availHeightDp).coerceAtLeast(36f)
            val padDp = ((100 - scalePercent) / 100f) * shortSideDp / 2f
            return (padDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
        }

        private fun Drawable.toMiniBitmap(sizePx: Int): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
            return bmp
        }
    }
}

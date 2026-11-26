package com.quicknap.app.widget

import android.content.Context

/** ホーム画面ウィジェットのセル内での縦位置 */
enum class WidgetVerticalAlign(val label: String) {
    TOP("上"),
    CENTER("中央"),
    BOTTOM("下"),
}

/** ホーム画面ウィジェット ID ごとの表示設定（カテゴリ・ラベル表示・アイコンサイズ・縦位置）を保持する */
object WidgetPrefs {
    private const val PREF_NAME = "quicknap_widgets"
    private const val KEY_CAT_PREFIX = "cat_"
    private const val KEY_SHOW_LABEL_PREFIX = "show_label_"
    private const val KEY_SCALE_PREFIX = "scale_"
    private const val KEY_VALIGN_PREFIX = "valign_"

    const val DEFAULT_SHOW_LABEL = false
    const val DEFAULT_ICON_SCALE_PERCENT = 100
    const val MIN_ICON_SCALE_PERCENT = 70
    const val MAX_ICON_SCALE_PERCENT = 100
    val DEFAULT_VERTICAL_ALIGN = WidgetVerticalAlign.BOTTOM

    fun saveAll(
        context: Context,
        appWidgetId: Int,
        categoryId: String?,
        showLabel: Boolean,
        iconScalePercent: Int,
        verticalAlign: WidgetVerticalAlign = DEFAULT_VERTICAL_ALIGN,
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val clampedScale = iconScalePercent.coerceIn(MIN_ICON_SCALE_PERCENT, MAX_ICON_SCALE_PERCENT)
        prefs.edit().apply {
            if (categoryId == null) remove(KEY_CAT_PREFIX + appWidgetId)
            else putString(KEY_CAT_PREFIX + appWidgetId, categoryId)
            putBoolean(KEY_SHOW_LABEL_PREFIX + appWidgetId, showLabel)
            putInt(KEY_SCALE_PREFIX + appWidgetId, clampedScale)
            putString(KEY_VALIGN_PREFIX + appWidgetId, verticalAlign.name)
        }.apply()
    }

    fun saveCategory(context: Context, appWidgetId: Int, categoryId: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (categoryId == null) remove(KEY_CAT_PREFIX + appWidgetId)
            else putString(KEY_CAT_PREFIX + appWidgetId, categoryId)
        }.apply()
    }

    fun getCategory(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CAT_PREFIX + appWidgetId, null)
    }

    fun getShowLabel(context: Context, appWidgetId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_LABEL_PREFIX + appWidgetId, DEFAULT_SHOW_LABEL)
    }

    fun getIconScalePercent(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SCALE_PREFIX + appWidgetId, DEFAULT_ICON_SCALE_PERCENT)
            .coerceIn(MIN_ICON_SCALE_PERCENT, MAX_ICON_SCALE_PERCENT)
    }

    fun getVerticalAlign(context: Context, appWidgetId: Int): WidgetVerticalAlign {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VALIGN_PREFIX + appWidgetId, null)
        return WidgetVerticalAlign.entries.firstOrNull { it.name == raw } ?: DEFAULT_VERTICAL_ALIGN
    }

    fun delete(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CAT_PREFIX + appWidgetId)
            .remove(KEY_SHOW_LABEL_PREFIX + appWidgetId)
            .remove(KEY_SCALE_PREFIX + appWidgetId)
            .remove(KEY_VALIGN_PREFIX + appWidgetId)
            .apply()
    }
}

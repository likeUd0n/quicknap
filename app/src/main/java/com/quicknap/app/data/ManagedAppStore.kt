package com.quicknap.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SleepReason { SELF, AUTO }

enum class AsleepSortOrder(val label: String) {
    FREQUENCY("よく使う順"),
    RECENT("最近使った順"),
    NAME("名前順"),
}

data class Category(
    val id: String,
    val name: String,
)

/**
 * Quicknap に任せたアプリ1件の記録。
 * 「眠っている／最近起こした」の振り分けは OS の enabled 状態を正とし、ここには理由と日時だけを持つ。
 */
data class ManagedApp(
    val packageName: String,
    val sleepReason: SleepReason? = null,
    /** 自動で眠った時点の設定日数（あとから日数を変えても書き換えない） */
    val sleepThresholdDaysAtSleep: Int? = null,
    val sleptAt: Long? = null,
    val lastWokenAt: Long? = null,
    val categoryId: String? = null,
    val wakeCount: Int = 0,
)

data class QuicknapState(
    val managedApps: Map<String, ManagedApp> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val asleepSortOrder: AsleepSortOrder = AsleepSortOrder.FREQUENCY,
    val autoSleepDays: Int = DEFAULT_AUTO_SLEEP_DAYS,
    val failedAutoSleepNotified: Boolean = false,
    val setupDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_AUTO_SLEEP_DAYS = 3
        const val MIN_AUTO_SLEEP_DAYS = 1
        const val MAX_AUTO_SLEEP_DAYS = 30
    }
}

private val Context.quicknapDataStore by preferencesDataStore(name = "quicknap")

class ManagedAppStore(context: Context) {

    private val dataStore = context.applicationContext.quicknapDataStore

    @Volatile
    private var latestState: QuicknapState? = null

    val state: Flow<QuicknapState> = dataStore.data.map { prefs ->
        prefs.toState().also { latestState = it }
    }

    /** 直近に読み込んだ状態を同期で返す（未読なら null） */
    fun cached(): QuicknapState? = latestState

    suspend fun current(): QuicknapState = state.first()

    suspend fun updateApps(transform: (Map<String, ManagedApp>) -> Map<String, ManagedApp>) {
        dataStore.edit { prefs ->
            val apps = decodeApps(prefs[KEY_MANAGED_APPS])
            prefs[KEY_MANAGED_APPS] = encodeApps(transform(apps))
        }
    }

    suspend fun put(app: ManagedApp) = updateApps { it + (app.packageName to app) }

    suspend fun remove(packageName: String) = updateApps { it - packageName }

    suspend fun setAutoSleepDays(days: Int) {
        dataStore.edit {
            it[KEY_AUTO_SLEEP_DAYS] = days.coerceIn(
                QuicknapState.MIN_AUTO_SLEEP_DAYS,
                QuicknapState.MAX_AUTO_SLEEP_DAYS,
            )
        }
    }

    suspend fun setFailedAutoSleepNotified(value: Boolean) {
        dataStore.edit { it[KEY_FAILED_NOTIFIED] = value }
    }

    suspend fun setSetupDone() {
        dataStore.edit { it[KEY_SETUP_DONE] = true }
    }

    suspend fun setAsleepSortOrder(order: AsleepSortOrder) {
        dataStore.edit { it[KEY_ASLEEP_SORT] = order.name }
    }

    suspend fun addCategory(name: String): Category {
        val trimmed = name.trim()
        val category = Category(id = UUID.randomUUID().toString(), name = trimmed)
        dataStore.edit { prefs ->
            val list = decodeCategories(prefs[KEY_CATEGORIES])
            prefs[KEY_CATEGORIES] = encodeCategories(list + category)
        }
        return category
    }

    suspend fun renameCategory(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val list = decodeCategories(prefs[KEY_CATEGORIES]).map {
                if (it.id == id) it.copy(name = trimmed) else it
            }
            prefs[KEY_CATEGORIES] = encodeCategories(list)
        }
    }

    suspend fun deleteCategory(id: String) {
        dataStore.edit { prefs ->
            val list = decodeCategories(prefs[KEY_CATEGORIES]).filterNot { it.id == id }
            prefs[KEY_CATEGORIES] = encodeCategories(list)
            val apps = decodeApps(prefs[KEY_MANAGED_APPS]).mapValues { (_, app) ->
                if (app.categoryId == id) app.copy(categoryId = null) else app
            }
            prefs[KEY_MANAGED_APPS] = encodeApps(apps)
        }
    }

    suspend fun setAppCategory(packageName: String, categoryId: String?) {
        updateApps { apps ->
            val current = apps[packageName] ?: return@updateApps apps
            apps + (packageName to current.copy(categoryId = categoryId))
        }
    }

    /**
     * 初回のみデフォルトカテゴリを登録し、既存の未分類アプリへ自動割り当てする。
     * 一度初期化したら再作成しない（ユーザーが削除したカテゴリを尊重する）。
     */
    suspend fun ensureDefaultCategories(
        defaults: List<Category>,
        classify: (String) -> String?,
    ) {
        dataStore.edit { prefs ->
            if (prefs[KEY_DEFAULT_CATEGORIES_INITIALIZED] == true) return@edit
            val existing = decodeCategories(prefs[KEY_CATEGORIES])
            val existingById = existing.associateBy { it.id }
            val existingByName = existing.associateBy { it.name.lowercase() }
            val merged = existing.toMutableList()
            val resolvedIdByDefaultId = HashMap<String, String>()

            for (def in defaults) {
                val matched = existingById[def.id] ?: existingByName[def.name.lowercase()]
                if (matched != null) {
                    resolvedIdByDefaultId[def.id] = matched.id
                } else {
                    merged.add(def)
                    resolvedIdByDefaultId[def.id] = def.id
                }
            }
            prefs[KEY_CATEGORIES] = encodeCategories(merged)

            val apps = decodeApps(prefs[KEY_MANAGED_APPS])
            if (apps.isNotEmpty()) {
                val updatedApps = apps.mapValues { (pkg, app) ->
                    if (app.categoryId != null) {
                        app
                    } else {
                        val defaultCatId = classify(pkg)
                        val targetId = defaultCatId?.let { resolvedIdByDefaultId[it] }
                        if (targetId != null) app.copy(categoryId = targetId) else app
                    }
                }
                prefs[KEY_MANAGED_APPS] = encodeApps(updatedApps)
            }
            prefs[KEY_DEFAULT_CATEGORIES_INITIALIZED] = true
        }
    }

    private fun Preferences.toState() = QuicknapState(
        managedApps = decodeApps(this[KEY_MANAGED_APPS]),
        categories = decodeCategories(this[KEY_CATEGORIES]),
        asleepSortOrder = this[KEY_ASLEEP_SORT]
            ?.let { name -> AsleepSortOrder.entries.firstOrNull { it.name == name } }
            ?: AsleepSortOrder.FREQUENCY,
        autoSleepDays = this[KEY_AUTO_SLEEP_DAYS] ?: QuicknapState.DEFAULT_AUTO_SLEEP_DAYS,
        failedAutoSleepNotified = this[KEY_FAILED_NOTIFIED] ?: false,
        setupDone = this[KEY_SETUP_DONE] ?: false,
    )

    private fun encodeApps(apps: Map<String, ManagedApp>): String {
        val root = JSONObject()
        for ((pkg, app) in apps) {
            root.put(
                pkg,
                JSONObject().apply {
                    app.sleepReason?.let { put("sleepReason", it.name) }
                    app.sleepThresholdDaysAtSleep?.let { put("sleepThresholdDaysAtSleep", it) }
                    app.sleptAt?.let { put("sleptAt", it) }
                    app.lastWokenAt?.let { put("lastWokenAt", it) }
                    app.categoryId?.let { put("categoryId", it) }
                    if (app.wakeCount > 0) put("wakeCount", app.wakeCount)
                },
            )
        }
        return root.toString()
    }

    private fun decodeApps(raw: String?): Map<String, ManagedApp> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, ManagedApp>()
        for (pkg in root.keys()) {
            val obj = root.optJSONObject(pkg) ?: continue
            result[pkg] = ManagedApp(
                packageName = pkg,
                sleepReason = obj.optString("sleepReason").takeIf { it.isNotEmpty() }
                    ?.let { name ->
                        if (name == "AUTO_SCREEN_OFF") SleepReason.AUTO
                        else SleepReason.entries.firstOrNull { it.name == name }
                    },
                sleepThresholdDaysAtSleep = obj.optIntOrNull("sleepThresholdDaysAtSleep"),
                sleptAt = obj.optLongOrNull("sleptAt"),
                lastWokenAt = obj.optLongOrNull("lastWokenAt"),
                categoryId = obj.optString("categoryId").takeIf { it.isNotEmpty() },
                wakeCount = obj.optInt("wakeCount", 0).coerceAtLeast(0),
            )
        }
        return result
    }

    private fun encodeCategories(categories: List<Category>): String {
        val arr = JSONArray()
        for (cat in categories) {
            arr.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeCategories(raw: String?): List<Category> {
        if (raw.isNullOrEmpty()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = ArrayList<Category>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: continue
            result.add(Category(id = id, name = name))
        }
        return result
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null
    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key)) optLong(key) else null

    private companion object {
        val KEY_MANAGED_APPS = stringPreferencesKey("managed_apps")
        val KEY_CATEGORIES = stringPreferencesKey("categories")
        val KEY_DEFAULT_CATEGORIES_INITIALIZED = booleanPreferencesKey("default_categories_initialized")
        val KEY_ASLEEP_SORT = stringPreferencesKey("asleep_sort_order")
        val KEY_AUTO_SLEEP_DAYS = intPreferencesKey("auto_sleep_days")
        val KEY_FAILED_NOTIFIED = booleanPreferencesKey("failed_auto_sleep_notified")
        val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
    }
}

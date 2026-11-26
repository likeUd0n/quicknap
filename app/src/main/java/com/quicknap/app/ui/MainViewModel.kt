package com.quicknap.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quicknap.app.QuicknapApp
import com.quicknap.app.data.AsleepSortOrder
import com.quicknap.app.data.Category
import com.quicknap.app.data.LaunchableApp
import com.quicknap.app.data.ManagedApp
import com.quicknap.app.data.QuicknapState
import com.quicknap.app.data.SleepReason
import com.quicknap.app.domain.AppSnapshot
import com.quicknap.app.domain.SleepController
import com.quicknap.app.shizuku.ShizukuStatus
import com.quicknap.app.widget.QuicknapWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

const val CATEGORY_UNCATEGORIZED = "__uncategorized__"

data class HomeRow(
    val app: LaunchableApp,
    val asleep: Boolean,
    val sub: String,
    val categoryId: String? = null,
    val categoryName: String? = null,
)

data class PickRow(
    val app: LaunchableApp,
    val managed: Boolean,
    val asleep: Boolean,
    val sub: String,
    val cautionReason: String?,
)

data class UiState(
    val loading: Boolean = true,
    val setupDone: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus.Unavailable,
    val shizukuInstalled: Boolean = true,
    val usageAccess: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val autoSleepDays: Int = QuicknapState.DEFAULT_AUTO_SLEEP_DAYS,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val asleepSortOrder: AsleepSortOrder = AsleepSortOrder.FREQUENCY,
    val asleepTotalCount: Int = 0,
    val asleepCategoryCounts: Map<String, Int> = emptyMap(),
    val awake: List<HomeRow> = emptyList(),
    val asleep: List<HomeRow> = emptyList(),
    val allManaged: List<HomeRow> = emptyList(),
    val pickMain: List<PickRow> = emptyList(),
    val pickCaution: List<PickRow> = emptyList(),
    val busy: Set<String> = emptySet(),
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuStatus.Ready
    val hasManaged: Boolean get() = awake.isNotEmpty() || asleepTotalCount > 0
}

/** 画面下のトースト。undo があれば「元に戻す」を出す */
data class UiMessage(
    val text: String,
    val undo: (() -> Unit)? = null,
    /** 対象アプリ名。あれば1行目に出し、text を2行目に出す */
    val subject: String? = null,
)

private data class Env(
    val loading: Boolean = true,
    val shizukuInstalled: Boolean = true,
    val usageAccess: Boolean = false,
    val notificationsAllowed: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuicknapApp
    private val controller = app.sleepController

    private val installedApps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val usageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val cautionMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val env = MutableStateFlow(Env())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val ui: StateFlow<UiState> = combine(
        combine(app.store.state, installedApps, usageMap, cautionMap, app.shizuku.status) {
                state, apps, usage, caution, shizuku ->
            Triple(state, apps, Triple(usage, caution, shizuku))
        },
        selectedCategory,
        busy,
        env,
    ) { (state, apps, rest), selectedCat, busySet, e ->
        val (usage, caution, shizuku) = rest
        buildState(state, apps, usage, caution, shizuku, selectedCat).copy(
            loading = e.loading,
            shizukuInstalled = e.shizukuInstalled,
            usageAccess = e.usageAccess,
            notificationsAllowed = e.notificationsAllowed,
            busy = busySet,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private var resumeJob: Job? = null

    /**
     * 画面に戻るたび：まず管理対象アプリだけ即座に表示し、
     * 裏で自動休止・全アプリ一覧（選ぶ画面用）・使用履歴を更新する。
     */
    fun onResume() {
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            app.shizuku.refreshStatus()
            val state = app.store.current()

            // 1. ホーム画面に必要な管理対象アプリだけ先に読み、待ち時間なしで表示する
            val managedPkgs = state.managedApps.keys
            if (managedPkgs.isNotEmpty()) {
                val managed = app.installed.loadManagedApps(managedPkgs)
                if (installedApps.value.isEmpty()) {
                    installedApps.value = managed
                } else {
                    val enabledByPkg = managed.associate { it.packageName to it.isEnabled }
                    installedApps.update { list ->
                        list.map { item ->
                            enabledByPkg[item.packageName]?.let { item.copy(isEnabled = it) } ?: item
                        }
                    }
                }
            }
            refreshEnv(loading = false)

            // 2. 裏で自動休止と選ぶ画面用の全アプリ・使用履歴をそろえる
            if (state.setupDone) controller.runAutoSleep() else controller.reconcile()
            val apps = app.installed.loadUserLaunchableApps()
            installedApps.value = apps
            usageMap.value = app.usage.lastUsedMap()
            cautionMap.value = app.caution.detect(apps.map { it.packageName })
            refreshEnv(loading = false)
        }
    }

    private fun refreshEnv(loading: Boolean = env.value.loading) {
        env.value = Env(
            loading = loading,
            shizukuInstalled = app.installed.isShizukuInstalled(),
            usageAccess = app.usage.hasAccess(),
            notificationsAllowed = app.notifier.canNotify(),
        )
    }

    fun onPermissionResult() = refreshEnv()

    // ---- 一覧 ----

    fun tapHomeRow(row: HomeRow) = runOp(row.app.packageName) {
        val pkg = row.app.packageName
        when {
            !row.asleep -> {
                launchApp(pkg)
                controller.markWoken(pkg)
            }
            ui.value.shizukuReady -> {
                val woke = controller.wake(pkg)
                refreshEnabled(pkg)
                if (woke.isSuccess) {
                    launchApp(pkg)
                } else {
                    emit("起こせませんでした: ${woke.exceptionOrNull()?.message}")
                }
            }
            else -> openAppDetails(pkg)
        }
    }

    fun toggleHomeRow(row: HomeRow, toAwake: Boolean) = runOp(row.app.packageName) {
        val pkg = row.app.packageName
        val snapshot = controller.snapshot(pkg)
        val result = if (toAwake) controller.wake(pkg) else controller.sleepManually(pkg)
        refreshEnabled(pkg)
        if (result.isSuccess) {
            val text = if (toAwake) "有効にしました" else "眠らせました"
            emit(text, subject = row.app.label) { undo(snapshot) }
        } else {
            emit("切り替えに失敗しました: ${result.exceptionOrNull()?.message}")
        }
    }

    /** 「外す」で選んだ行：選ぶ画面で OFF にするのと同じく、まとめて対象から外す */
    fun releaseHomeRows(rows: List<HomeRow>) {
        val targets = rows.filter { it.app.packageName !in busy.value }
        if (targets.isEmpty()) return
        val pkgs = targets.map { it.app.packageName }.toSet()
        busy.update { it + pkgs }
        viewModelScope.launch {
            val snapshots = mutableListOf<AppSnapshot>()
            var failure: Throwable? = null
            try {
                for (row in targets) {
                    val pkg = row.app.packageName
                    val snapshot = controller.snapshot(pkg)
                    val result = controller.release(pkg)
                    refreshEnabled(pkg)
                    if (result.isSuccess) snapshots += snapshot else failure = failure ?: result.exceptionOrNull()
                }
            } finally {
                busy.update { it - pkgs }
            }
            when {
                snapshots.isEmpty() -> emit("戻せませんでした: ${failure?.message}")
                else -> {
                    val subject = if (snapshots.size == 1) targets.first { it.app.packageName == snapshots[0].packageName }.app.label else null
                    val text = if (snapshots.size == 1) "対象から外しました" else "${snapshots.size}件を対象から外しました"
                    emit(if (failure != null) "$text（一部失敗）" else text, subject = subject) {
                        snapshots.forEach { undo(it) }
                    }
                }
            }
        }
    }

    // ---- 選ぶ画面 ----

    fun setPicked(row: PickRow, checked: Boolean) = runOp(row.app.packageName) {
        val pkg = row.app.packageName
        val snapshot = controller.snapshot(pkg)
        val result = if (checked) controller.sleepManually(pkg) else controller.release(pkg)
        refreshEnabled(pkg)
        if (result.isSuccess) {
            val text = if (checked) "眠らせました" else "対象から外しました"
            emit(text, subject = row.app.label) { undo(snapshot) }
        } else {
            emit("切り替えに失敗しました: ${result.exceptionOrNull()?.message}")
        }
    }

    fun changeAutoSleepDays(delta: Int) {
        viewModelScope.launch { controller.setAutoSleepDays(ui.value.autoSleepDays + delta) }
    }

    // ---- カテゴリ・並び替え ----

    fun selectCategory(categoryId: String?) {
        selectedCategory.value = categoryId
    }

    fun setAsleepSortOrder(order: AsleepSortOrder) {
        viewModelScope.launch {
            if (order == AsleepSortOrder.MANUAL) {
                val current = app.store.current()
                if (current.manualAsleepOrder.isEmpty()) {
                    // 初回に手動順へ切り替えたときは、いま見えている順番を初期値として固定する
                    val initial = ui.value.asleep.map { it.app.packageName }
                    app.store.setManualAsleepOrder(initial)
                    QuicknapWidgetProvider.updateAll(app)
                    return@launch
                }
            }
            app.store.setAsleepSortOrder(order)
            QuicknapWidgetProvider.updateAll(app)
        }
    }

    /**
     * 眠っているアプリの並び替えを保存する。
     * カテゴリ絞り込み中の並び替えでも、他カテゴリの順番を壊さないよう該当スロットだけ入れ替える。
     */
    fun reorderAsleep(visibleOrderedPackages: List<String>) {
        if (visibleOrderedPackages.isEmpty()) return
        viewModelScope.launch {
            val fallback = ui.value.allManaged.filter { it.asleep }.map { it.app.packageName }
            app.store.mergeManualAsleepOrder(visibleOrderedPackages, fallbackBase = fallback)
            QuicknapWidgetProvider.updateAll(app)
        }
    }

    fun assignCategory(packageName: String, categoryId: String?) {
        viewModelScope.launch {
            app.store.setAppCategory(packageName, categoryId)
            QuicknapWidgetProvider.updateAll(app)
            val label = installedApps.value.firstOrNull { it.packageName == packageName }?.label
            val catName = ui.value.categories.firstOrNull { it.id == categoryId }?.name
            val msg = if (catName != null) "「$catName」に設定しました" else "未分類に戻しました"
            emit(msg, subject = label)
        }
    }

    /** カテゴリ管理画面からの作成・編集（名前＋所属アプリの一括保存） */
    fun saveCategory(id: String?, name: String, packageNames: Set<String>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val categories = app.store.current().categories
            val duplicate = categories.firstOrNull {
                it.id != id && it.name.equals(trimmed, ignoreCase = true)
            }
            if (duplicate != null) {
                emit("「${duplicate.name}」はすでにあります")
                return@launch
            }
            val targetId = if (id == null) {
                app.store.addCategory(trimmed).id
            } else {
                app.store.renameCategory(id, trimmed)
                id
            }
            app.store.setCategoryApps(targetId, packageNames)
            QuicknapWidgetProvider.updateAll(app)
            val msg = if (id == null) "カテゴリ「$trimmed」を作成しました" else "カテゴリ「$trimmed」を保存しました"
            emit(msg)
        }
    }

    fun createCategory(name: String, assignToPackage: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = app.store.current().categories.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true)
            }
            val category = existing ?: app.store.addCategory(trimmed)
            if (assignToPackage != null) {
                app.store.setAppCategory(assignToPackage, category.id)
                val label = installedApps.value.firstOrNull { it.packageName == assignToPackage }?.label
                emit("「${category.name}」に設定しました", subject = label)
            } else if (existing != null) {
                emit("「${existing.name}」はすでにあります")
            } else {
                emit("カテゴリ「${category.name}」を作成しました")
            }
        }
    }

    fun renameCategory(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            app.store.renameCategory(id, trimmed)
            emit("カテゴリ名を「$trimmed」に変更しました")
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            val name = app.store.current().categories.firstOrNull { it.id == id }?.name
            app.store.deleteCategory(id)
            QuicknapWidgetProvider.updateAll(app)
            if (selectedCategory.value == id) selectedCategory.value = null
            if (name != null) emit("カテゴリ「$name」を削除しました")
        }
    }

    // ---- 初回セットアップ・Shizuku ----

    fun finishSetup() {
        viewModelScope.launch { app.store.setSetupDone() }
    }

    fun requestShizukuPermission() = app.shizuku.requestPermission()

    fun openShizuku() {
        if (!app.installed.openShizukuApp()) emit("Shizuku アプリが見つかりません")
    }

    fun openShizukuDetails() = app.installed.openShizukuDetails()

    fun openUsageAccessSettings() = app.installed.openUsageAccessSettings()

    /** Shizuku 停止中の退避路。設定画面で有効にされたら、Quicknap に戻らずそのまま開く */
    fun openAppDetails(packageName: String) {
        app.manualWake.watch(packageName)
        app.installed.openAppDetails(packageName)
    }

    // ---- 内部 ----

    private fun undo(snapshot: AppSnapshot) = runOp(snapshot.packageName) {
        val result = controller.restore(snapshot)
        refreshEnabled(snapshot.packageName)
        if (result.isFailure) emit("元に戻せませんでした: ${result.exceptionOrNull()?.message}")
    }

    private suspend fun launchApp(packageName: String) {
        val result = app.installed.launchAppWithRetry(packageName)
        if (result.isFailure) emit(result.exceptionOrNull()?.message ?: "起動に失敗しました")
    }

    /** 連打防止：実行中の行は操作を受け付けない */
    private fun runOp(packageName: String, block: suspend () -> Unit) {
        if (packageName in busy.value) return
        busy.update { it + packageName }
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.update { it - packageName }
            }
        }
    }

    private fun refreshEnabled(packageName: String) {
        val enabled = app.installed.enabledState(packageName) ?: return
        installedApps.update { list ->
            list.map { if (it.packageName == packageName) it.copy(isEnabled = enabled) else it }
        }
    }

    private fun emit(text: String, subject: String? = null, undo: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, undo, subject))
    }

    private fun buildState(
        state: QuicknapState,
        apps: List<LaunchableApp>,
        usage: Map<String, Long>,
        caution: Map<String, String>,
        shizuku: ShizukuStatus,
        selectedCategoryId: String?,
    ): UiState {
        val now = System.currentTimeMillis()
        val ready = shizuku == ShizukuStatus.Ready
        val managed = state.managedApps
        val categoryNameById = state.categories.associate { it.id to it.name }

        val homeRows = apps.mapNotNull { a ->
            val record = managed[a.packageName] ?: return@mapNotNull null
            val catId = record.categoryId?.takeIf { it in categoryNameById }
            HomeRow(
                app = a,
                asleep = !a.isEnabled,
                sub = if (a.isEnabled) {
                    awakeSub(record, usage, state.autoSleepDays, ready, now)
                } else {
                    asleepSub(record, now)
                },
                categoryId = catId,
                categoryName = catId?.let { categoryNameById[it] },
            )
        }

        val awakeRows = homeRows.filter { !it.asleep }.sortedBy { it.app.label.lowercase() }
        val allAsleep = homeRows.filter { it.asleep }

        val counts = HashMap<String, Int>()
        for (row in allAsleep) {
            val key = row.categoryId ?: CATEGORY_UNCATEGORIZED
            counts[key] = (counts[key] ?: 0) + 1
        }

        val effectiveSelected = when {
            selectedCategoryId == null -> null
            selectedCategoryId == CATEGORY_UNCATEGORIZED ->
                if ((counts[CATEGORY_UNCATEGORIZED] ?: 0) > 0 && state.categories.isNotEmpty()) CATEGORY_UNCATEGORIZED else null
            selectedCategoryId in categoryNameById -> selectedCategoryId
            else -> null
        }

        val filteredAsleep = when (effectiveSelected) {
            null -> allAsleep
            CATEGORY_UNCATEGORIZED -> allAsleep.filter { it.categoryId == null }
            else -> allAsleep.filter { it.categoryId == effectiveSelected }
        }

        val sortedAsleep = when (state.asleepSortOrder) {
            AsleepSortOrder.FREQUENCY -> filteredAsleep.sortedWith(
                compareByDescending<HomeRow> { managed[it.app.packageName]?.wakeCount ?: 0 }
                    .thenByDescending { row ->
                        managed[row.app.packageName]?.let { controller.lastUsedAt(it, usage) } ?: 0L
                    }
                    .thenBy { it.app.label.lowercase() }
            )
            AsleepSortOrder.RECENT -> filteredAsleep.sortedWith(
                compareByDescending<HomeRow> { row ->
                    managed[row.app.packageName]?.let { controller.lastUsedAt(it, usage) } ?: 0L
                }.thenBy { it.app.label.lowercase() }
            )
            AsleepSortOrder.NAME -> filteredAsleep.sortedBy { it.app.label.lowercase() }
            AsleepSortOrder.MANUAL -> {
                val orderIndex = state.manualAsleepOrder.withIndex().associate { (i, pkg) -> pkg to i }
                filteredAsleep.sortedWith(
                    compareBy<HomeRow> { orderIndex[it.app.packageName] ?: Int.MAX_VALUE }
                        .thenBy { it.app.label.lowercase() }
                )
            }
        }

        val pickRows = apps.map { a ->
            val isManaged = a.packageName in managed
            PickRow(
                app = a,
                managed = isManaged,
                asleep = !a.isEnabled,
                sub = when {
                    isManaged && !a.isEnabled -> "対象 · 眠っている"
                    isManaged -> "対象 · 起きている"
                    else -> "対象外" + (usage[a.packageName]?.let { " · ${usedAgo(it, now)}" } ?: "")
                },
                cautionReason = caution[a.packageName],
            )
        }
        val managedPick = pickRows.filter { it.managed }.sortedBy { it.app.label.lowercase() }
        // 未チェックは最後に使ったのが古い順（履歴なし＝最も古い）
        val byOldest = compareBy<PickRow> { usage[it.app.packageName] ?: 0L }
        val normalPick = pickRows.filter { !it.managed && it.cautionReason == null }.sortedWith(byOldest)
        val cautionPick = pickRows.filter { !it.managed && it.cautionReason != null }.sortedWith(byOldest)

        return UiState(
            setupDone = state.setupDone,
            shizuku = shizuku,
            autoSleepDays = state.autoSleepDays,
            categories = state.categories,
            selectedCategoryId = effectiveSelected,
            asleepSortOrder = state.asleepSortOrder,
            asleepTotalCount = allAsleep.size,
            asleepCategoryCounts = counts,
            awake = awakeRows,
            asleep = sortedAsleep,
            allManaged = homeRows.sortedBy { it.app.label.lowercase() },
            pickMain = normalPick + managedPick,
            pickCaution = cautionPick,
        )
    }

    private fun awakeSub(
        record: ManagedApp,
        usage: Map<String, Long>,
        days: Int,
        ready: Boolean,
        now: Long,
    ): String {
        if (!ready) return "自動休止停止中"
        val lastUsed = controller.lastUsedAt(record, usage).takeIf { it > 0L } ?: now
        val leftMs = days * SleepController.DAY_MS - (now - lastUsed)
        val leftDays = ceil(leftMs.toDouble() / SleepController.DAY_MS).toInt()
        return if (leftDays <= 0) "まもなく自動で眠る" else "あと${leftDays}日で眠る"
    }

    private fun asleepSub(record: ManagedApp, now: Long): String {
        val reason = when (record.sleepReason) {
            SleepReason.AUTO -> "自動(${record.sleepThresholdDaysAtSleep ?: "?"}日)"
            else -> "手動"
        }
        val ago = record.sleptAt?.let { daysAgo(it, now) }
        return if (ago == null) reason else "$reason · ${if (ago == 0) "今日" else "${ago}日前"}"
    }

    private fun usedAgo(time: Long, now: Long): String {
        val ago = daysAgo(time, now)
        return if (ago == 0) "今日使用" else "${ago}日前に使用"
    }

    private fun daysAgo(time: Long, now: Long): Int =
        ((now - time) / SleepController.DAY_MS).toInt().coerceAtLeast(0)
}

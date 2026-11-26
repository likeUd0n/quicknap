package com.quicknap.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import com.quicknap.app.MainActivity
import com.quicknap.app.QuicknapApp
import com.quicknap.app.data.AsleepSortOrder
import com.quicknap.app.data.LaunchableApp
import com.quicknap.app.shizuku.ShizukuStatus
import com.quicknap.app.ui.theme.LocalQuicknapPalette
import com.quicknap.app.ui.theme.QuicknapTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class FolderItem(
    val app: LaunchableApp,
    val asleep: Boolean,
)

/**
 * ホーム画面の 1×1 ウィジェットをタップしたときに開くコンパクトフォルダポップアップ。
 * 対象カテゴリのアプリをグリッドで表示し、1タップで起こしてそのまま起動する。
 */
class WidgetFolderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as QuicknapApp
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val extraCategoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val initialCategoryId = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPrefs.getCategory(this, widgetId) ?: extraCategoryId
        } else {
            extraCategoryId
        }

        // キャッシュがあれば初回フレームから即時表示し、スピナー待ちをなくす
        val cachedState = app.store.cached()
        val cachedCategory = cachedState?.let { s ->
            initialCategoryId?.let { id -> s.categories.firstOrNull { it.id == id } }
        }
        val initialTitle = cachedCategory?.name ?: DEFAULT_FOLDER_TITLE
        val initialItems = if (cachedState != null) {
            val targetCategoryId = cachedCategory?.id
            val targetPackages = cachedState.managedApps.values
                .filter { targetCategoryId == null || it.categoryId == targetCategoryId }
                .map { it.packageName }
            val cachedLaunchables = app.installed.cachedManagedApps(targetPackages)
            if (targetPackages.isEmpty() || cachedLaunchables.isNotEmpty()) {
                sortFolderItems(
                    app = app,
                    state = cachedState,
                    launchables = cachedLaunchables,
                    usage = app.usage.cachedLastUsedMap(),
                )
            } else {
                null
            }
        } else {
            null
        }
        val initialShizukuReady = app.shizuku.status.value == ShizukuStatus.Ready

        setContent {
            QuicknapTheme {
                var loading by remember { mutableStateOf(initialItems == null) }
                var title by remember { mutableStateOf(initialTitle) }
                var items by remember { mutableStateOf(initialItems.orEmpty()) }
                var shizukuReady by remember { mutableStateOf(initialShizukuReady) }
                var launchingPkg by remember { mutableStateOf<String?>(null) }
                var userReordered by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    shizukuReady = app.shizuku.refreshStatus() == ShizukuStatus.Ready
                    val state = app.store.current()
                    val category = initialCategoryId?.let { id ->
                        state.categories.firstOrNull { it.id == id }
                    }
                    title = category?.name ?: DEFAULT_FOLDER_TITLE
                    val targetCategoryId = category?.id
                    val targetPackages = state.managedApps.values
                        .filter { targetCategoryId == null || it.categoryId == targetCategoryId }
                        .map { it.packageName }

                    // 1. まず使用履歴の集計を待たず、管理対象アプリの最新状態だけで即座に一覧を確定する
                    val managedLaunchables = app.installed.loadManagedApps(targetPackages)
                    val needsUsage = state.asleepSortOrder == AsleepSortOrder.FREQUENCY ||
                        state.asleepSortOrder == AsleepSortOrder.RECENT
                    val cachedUsage = if (needsUsage) {
                        app.usage.cachedLastUsedMap()
                    } else {
                        emptyMap()
                    }
                    if (!userReordered) {
                        items = sortFolderItems(app, state, managedLaunchables, cachedUsage)
                    }
                    loading = false

                    // 2. 使用履歴の最新化は裏で行い、並び順が変わった場合だけ反映する
                    if (needsUsage && !userReordered) {
                        val freshUsage = app.usage.lastUsedMap()
                        val resorted = sortFolderItems(app, state, managedLaunchables, freshUsage)
                        if (!userReordered &&
                            resorted.map { it.app.packageName } != items.map { it.app.packageName }
                        ) {
                            items = resorted
                        }
                    }
                }

                FolderPopupContent(
                    title = title,
                    items = items,
                    loading = loading,
                    shizukuReady = shizukuReady,
                    launchingPkg = launchingPkg,
                    canConfigure = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
                    onDismiss = { finish() },
                    onOpenConfig = {
                        startActivity(
                            Intent(this, WidgetConfigActivity::class.java).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                        finish()
                    },
                    onOpenShizuku = {
                        if (app.installed.openShizukuApp()) {
                            finish()
                        } else {
                            showToast("Shizuku アプリが見つかりません")
                        }
                    },
                    onTapItem = { item ->
                        if (launchingPkg != null) return@FolderPopupContent
                        launchingPkg = item.app.packageName
                        scope.launch {
                            handleItemTap(app, item, shizukuReady)
                            launchingPkg = null
                        }
                    },
                    onMoveItem = { fromIndex, toIndex ->
                        if (fromIndex in items.indices && toIndex in items.indices && fromIndex != toIndex) {
                            userReordered = true
                            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                        }
                    },
                    onCommitReorder = {
                        val orderedPackages = items.map { it.app.packageName }
                        scope.launch {
                            app.store.mergeManualAsleepOrder(
                                visibleOrderedPackages = orderedPackages,
                                fallbackBase = orderedPackages,
                            )
                            QuicknapWidgetProvider.updateAll(app)
                        }
                    },
                )
            }
        }
    }

    private suspend fun handleItemTap(app: QuicknapApp, item: FolderItem, shizukuReady: Boolean) {
        val pkg = item.app.packageName
        when {
            !item.asleep -> {
                val launched = app.installed.launchAppWithRetry(pkg)
                app.sleepController.markWoken(pkg)
                if (launched.isFailure) {
                    showToast(launched.exceptionOrNull()?.message ?: "起動に失敗しました")
                } else {
                    finish()
                }
            }
            shizukuReady -> {
                val woke = app.sleepController.wake(pkg)
                if (woke.isSuccess) {
                    val launched = app.installed.launchAppWithRetry(pkg)
                    QuicknapWidgetProvider.updateAll(app)
                    if (launched.isFailure) {
                        showToast(launched.exceptionOrNull()?.message ?: "起動に失敗しました")
                    } else {
                        finish()
                    }
                } else {
                    showToast("起こせませんでした: ${woke.exceptionOrNull()?.message}")
                }
            }
            else -> {
                app.manualWake.watch(pkg)
                app.installed.openAppDetails(pkg)
                finish()
            }
        }
    }

    private fun sortFolderItems(
        app: QuicknapApp,
        state: com.quicknap.app.data.QuicknapState,
        launchables: List<LaunchableApp>,
        usage: Map<String, Long>,
    ): List<FolderItem> {
        val matched = launchables.map { launchable ->
            FolderItem(app = launchable, asleep = !launchable.isEnabled)
        }
        return when (state.asleepSortOrder) {
            AsleepSortOrder.FREQUENCY -> matched.sortedWith(
                compareByDescending<FolderItem> { state.managedApps[it.app.packageName]?.wakeCount ?: 0 }
                    .thenByDescending { item ->
                        state.managedApps[item.app.packageName]
                            ?.let { app.sleepController.lastUsedAt(it, usage) } ?: 0L
                    }
                    .thenBy { it.app.label.lowercase() }
            )
            AsleepSortOrder.RECENT -> matched.sortedWith(
                compareByDescending<FolderItem> { item ->
                    state.managedApps[item.app.packageName]
                        ?.let { app.sleepController.lastUsedAt(it, usage) } ?: 0L
                }.thenBy { it.app.label.lowercase() }
            )
            AsleepSortOrder.NAME -> matched.sortedBy { it.app.label.lowercase() }
            AsleepSortOrder.MANUAL -> {
                val orderIndex = state.manualAsleepOrder.withIndex().associate { (i, pkg) -> pkg to i }
                matched.sortedWith(
                    compareBy<FolderItem> { orderIndex[it.app.packageName] ?: Int.MAX_VALUE }
                        .thenBy { it.app.label.lowercase() }
                )
            }
        }
    }

    private fun showToast(message: String) {
        lifecycleScope.launch {
            Toast.makeText(this@WidgetFolderActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        private const val DEFAULT_FOLDER_TITLE = "眠っているアプリ"
    }
}

@Composable
private fun FolderPopupContent(
    title: String,
    items: List<FolderItem>,
    loading: Boolean,
    shizukuReady: Boolean,
    launchingPkg: String?,
    canConfigure: Boolean,
    onDismiss: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenShizuku: () -> Unit,
    onTapItem: (FolderItem) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onCommitReorder: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val latestItems by rememberUpdatedState(items)
    val latestLaunchingPkg by rememberUpdatedState(launchingPkg)
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var movedDuringDrag by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    // 並び替えモード中は起動を止め、長押しなしでドラッグできるようにする
    var reorderMode by remember { mutableStateOf(false) }
    val canReorder = !loading && items.size >= 2

    LaunchedEffect(canReorder) {
        if (!canReorder) reorderMode = false
    }

    val onReorderDragStart: (Offset) -> Unit = start@{ startOffset ->
        if (latestLaunchingPkg != null || latestItems.size < 2) return@start
        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            startOffset.x >= info.offset.x &&
                startOffset.x <= info.offset.x + info.size.width &&
                startOffset.y >= info.offset.y &&
                startOffset.y <= info.offset.y + info.size.height
        } ?: return@start
        val key = hit.key as? String ?: return@start
        draggingKey = key
        dragOffset = Offset.Zero
        movedDuringDrag = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onReorderDrag: (PointerInputChange, Offset) -> Unit = drag@{ change, dragAmount ->
        val key = draggingKey ?: return@drag
        change.consume()
        dragOffset += dragAmount
        val currentInfo = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?: return@drag
        val centerX = currentInfo.offset.x + currentInfo.size.width / 2f + dragOffset.x
        val centerY = currentInfo.offset.y + currentInfo.size.height / 2f + dragOffset.y

        val targetInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != key &&
                centerX >= info.offset.x &&
                centerX <= info.offset.x + info.size.width &&
                centerY >= info.offset.y &&
                centerY <= info.offset.y + info.size.height
        }
        if (targetInfo != null) {
            val fromIndex = currentInfo.index
            val toIndex = targetInfo.index
            if (fromIndex != toIndex &&
                fromIndex in latestItems.indices &&
                toIndex in latestItems.indices
            ) {
                onMoveItem(fromIndex, toIndex)
                if (fromIndex == 0 || toIndex == 0) {
                    gridState.requestScrollToItem(
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset,
                    )
                }
                dragOffset -= Offset(
                    x = (targetInfo.offset.x - currentInfo.offset.x).toFloat(),
                    y = (targetInfo.offset.y - currentInfo.offset.y).toFloat(),
                )
                movedDuringDrag = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
        val edgePx = 64f
        autoScrollSpeed = when {
            centerY < edgePx -> -12f
            centerY > viewportHeight - edgePx -> 12f
            else -> 0f
        }
    }
    val onReorderDragFinish: () -> Unit = {
        val commit = movedDuringDrag
        draggingKey = null
        dragOffset = Offset.Zero
        movedDuringDrag = false
        autoScrollSpeed = 0f
        if (commit) onCommitReorder()
    }

    LaunchedEffect(autoScrollSpeed) {
        if (autoScrollSpeed == 0f) return@LaunchedEffect
        while (true) {
            gridState.scrollBy(autoScrollSpeed)
            delay(16L)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (draggingKey == null) onDismiss() },
            )
            .padding(horizontal = 16.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 画面の高さに応じて一覧を縦長に表示しつつ、上下のタップ閉じ余白を残す
        val gridMaxHeight = (maxHeight * 0.66f).coerceIn(260.dp, 560.dp)

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SkyBottom,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // アプリアイコンと同じ「午後の空と雲」で、眠っている場所だと伝える（アイコンごとのバッジは出さない）
            Column(modifier = Modifier.drawBehind { drawSkyWithClouds() }.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (reorderMode) "ドラッグで並び替え" else title,
                        color = Color.White,
                        fontSize = if (reorderMode) 15.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (canReorder) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (reorderMode) Color.White else Color.White.copy(alpha = 0.16f))
                                .clickable(role = Role.Button, enabled = draggingKey == null) { reorderMode = !reorderMode }
                                .semantics {
                                    contentDescription = if (reorderMode) "並び替えを終える" else "並び替える"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "≡",
                                color = if (reorderMode) SkyBottom else Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (canConfigure) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable(role = Role.Button, onClick = onOpenConfig)
                                .semantics { contentDescription = "ウィジェット設定" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "⚙",
                                color = Color.White,
                                fontSize = 18.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(role = Role.Button, onClick = onOpenApp)
                            .semantics { contentDescription = "Quicknap を開く" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "↗",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (!loading && !shizukuReady && items.any { it.asleep }) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.warnBg,
                        border = BorderStroke(1.dp, palette.warnLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onOpenShizuku),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Shizuku 停止中",
                                    color = palette.warnInk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "タップして Shizuku を開く",
                                    color = palette.warnInk.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "↗",
                                color = palette.warnInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                when {
                    loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    items.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "このフォルダにアプリはありません",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    }

                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = gridMaxHeight)
                            .pointerInput(reorderMode) {
                                if (reorderMode) {
                                    detectDragGestures(
                                        onDragStart = onReorderDragStart,
                                        onDrag = onReorderDrag,
                                        onDragEnd = onReorderDragFinish,
                                        onDragCancel = onReorderDragFinish,
                                    )
                                } else {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = onReorderDragStart,
                                        onDrag = onReorderDrag,
                                        onDragEnd = onReorderDragFinish,
                                        onDragCancel = onReorderDragFinish,
                                    )
                                }
                            },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items, key = { it.app.packageName }) { item ->
                            val isDragging = draggingKey == item.app.packageName
                            val tileModifier = if (isDragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                    }
                            } else {
                                Modifier.animateItem()
                            }
                            FolderAppTile(
                                label = item.app.label,
                                icon = item.app.icon,
                                asleep = item.asleep,
                                busy = launchingPkg == item.app.packageName,
                                dragging = isDragging,
                                clickEnabled = draggingKey == null && !reorderMode,
                                onClick = { onTapItem(item) },
                                modifier = tileModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderAppTile(
    label: String,
    icon: ImageBitmap?,
    asleep: Boolean,
    busy: Boolean,
    dragging: Boolean,
    clickEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQuicknapPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(enabled = !busy && clickEnabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .alpha(if (asleep) 0.62f else 1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.bgSubtle),
                )
            }

            if (busy) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (asleep) 0.8f else 1f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val SkyTop = Color(0xFF14B8A6)
private val SkyBottom = Color(0xFF0F766E)

/** アプリアイコンと同じティールの空に、うすい雲を2つ浮かべる */
private fun DrawScope.drawSkyWithClouds() {
    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
    val unit = size.width / 100f
    drawCloud(center = Offset(size.width - 16f * unit, 14f * unit), unit = unit * 1.1f, alpha = 0.22f)
    drawCloud(center = Offset(14f * unit, size.height - 10f * unit), unit = unit * 1.4f, alpha = 0.14f)
}

/** アイコンの雲と同じ形（底が平らで、上に大小2つのふくらみ） */
private fun DrawScope.drawCloud(center: Offset, unit: Float, alpha: Float) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = center.x - 18f * unit,
                top = center.y - 2f * unit,
                right = center.x + 18f * unit,
                bottom = center.y + 10f * unit,
                cornerRadius = CornerRadius(6f * unit),
            )
        )
        addOval(Rect(center = Offset(center.x - 7f * unit, center.y + 1f * unit), radius = 8f * unit))
        addOval(Rect(center = Offset(center.x + 5f * unit, center.y - 3f * unit), radius = 11f * unit))
    }
    drawPath(path, Color.White.copy(alpha = alpha))
}

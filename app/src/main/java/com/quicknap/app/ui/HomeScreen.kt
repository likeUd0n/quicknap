package com.quicknap.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quicknap.app.data.AsleepSortOrder
import com.quicknap.app.data.Category
import com.quicknap.app.shizuku.ShizukuStatus
import com.quicknap.app.ui.theme.LocalQuicknapPalette
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    state: UiState,
    onPick: () -> Unit,
    onOpenCategories: (Boolean) -> Unit,
    onTapRow: (HomeRow) -> Unit,
    onToggle: (HomeRow, Boolean) -> Unit,
    selecting: Boolean,
    onSelectingChange: (Boolean) -> Unit,
    reordering: Boolean,
    onReorderingChange: (Boolean) -> Unit,
    onReorderAsleep: (List<String>) -> Unit,
    onRelease: (List<HomeRow>) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onResumeShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onChangeSortOrder: (AsleepSortOrder) -> Unit,
    onAssignCategory: (String, String?) -> Unit,
    onCreateCategory: (String, String?) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacingPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    val autoScrollEdgePx = remember(density) { with(density) { 72.dp.toPx() } }

    var assignTarget by remember { mutableStateOf<HomeRow?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createAssignPackage by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    // 「外す」の選択モードで選んだパッケージ
    var selected by remember { mutableStateOf(emptySet<String>()) }
    // 手動並び替えモード中の表示順とドラッグ状態
    var dragRows by remember { mutableStateOf(state.asleep) }
    var draggingPkg by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.asleep, draggingPkg) {
        if (draggingPkg == null) dragRows = state.asleep
    }

    fun exitSelecting() {
        selected = emptySet()
        onSelectingChange(false)
    }
    fun exitReordering() {
        draggingPkg = null
        dragOffsetY = 0f
        onReorderingChange(false)
    }
    BackHandler(enabled = selecting) { exitSelecting() }
    BackHandler(enabled = reordering) { exitReordering() }

    // 眠っているアプリを戻すには有効化が要るため、Shizuku 停止中は選べない
    fun selectable(row: HomeRow, isBusy: Boolean): Boolean =
        !isBusy && (row.app.isEnabled || state.shizukuReady)

    fun toggleSelect(row: HomeRow, isBusy: Boolean) {
        if (!selectable(row, isBusy)) return
        val pkg = row.app.packageName
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    // 選択モードでは行タップで選ぶだけにし、スイッチの代わりにチェックを出す
    val selectCheck: @Composable (HomeRow, Boolean) -> Unit = { row, isBusy ->
        Checkbox(
            checked = row.app.packageName in selected,
            enabled = selectable(row, isBusy),
            onCheckedChange = { toggleSelect(row, isBusy) },
            colors = CheckboxDefaults.colors(
                checkedColor = palette.accent,
                checkmarkColor = palette.accentInk,
                uncheckedColor = palette.muted,
                // Shizuku 停止中に選べない行は、押せないと分かるよう薄くする
                disabledUncheckedColor = palette.muted.copy(alpha = 0.25f),
            ),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                selecting -> {
                    Text(
                        text = "外すアプリを選ぶ（${selected.size}件）",
                        color = palette.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { exitSelecting() }) {
                        Text(text = "キャンセル", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                reordering -> {
                    Text(
                        text = "順番を並び替え",
                        color = palette.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { exitReordering() }) {
                        Text(text = "完了", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Text(text = "Quicknap", color = palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    HintButton(
                        lines = listOf(
                            "眠らせたアプリは、通知もアイコンも消えます。\nログインや権限は端末に残ります。",
                            "行をタップすると、起きてそのまま開きます。\nスイッチが ON のアプリは眠っています。",
                            "「カテゴリ」から名前の編集やアプリのまとめて追加ができます。\n「外す」から対象から外せます。",
                        ),
                        label = "Quicknap の仕組み",
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.hasManaged) {
                        TextButton(
                            onClick = { onOpenCategories(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(text = "カテゴリ", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onSelectingChange(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(text = "外す", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        when (state.shizuku) {
            ShizukuStatus.Unavailable -> {
                Spacer(Modifier.height(8.dp))
                ShizukuStoppedBanner(onResume = onResumeShizuku)
            }
            ShizukuStatus.PermissionRequired -> {
                Spacer(Modifier.height(8.dp))
                ShizukuPermissionBanner(onRequest = onRequestShizukuPermission)
            }
            ShizukuStatus.Ready -> Unit
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.loading && !state.hasManaged -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.accent)
            }

            !state.hasManaged -> EmptyState(onPick = onPick)

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (selecting) 96.dp else 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "h-awake") {
                    SectionHeader(title = "起きている（有効）", count = state.awake.size, dotColor = palette.accent)
                }
                if (state.awake.isEmpty()) {
                    item(key = "e-awake") { EmptyRow("起きているアプリはありません") }
                }
                items(state.awake, key = { "a:${it.app.packageName}" }) { row ->
                    val isBusy = row.app.packageName in state.busy
                    AppRow(
                        icon = row.app.icon,
                        label = row.app.label,
                        sub = row.sub,
                        subAccent = state.shizukuReady,
                        style = RowStyle.Awake,
                        badge = StateBadge.Awake,
                        isBusy = isBusy,
                        tag = row.categoryName,
                        onTagClick = if (selecting || reordering) null else ({ assignTarget = row }),
                        onClick = if (reordering) null else ({ if (selecting) toggleSelect(row, isBusy) else onTapRow(row) }),
                        onLongClick = if (selecting || reordering) null else ({ assignTarget = row }),
                    ) {
                        if (selecting) {
                            selectCheck(row, isBusy)
                        } else {
                            // ON＝眠らせている（選ぶ画面と同じ向き）
                            QuicknapSwitch(
                                checked = false,
                                enabled = state.shizukuReady && !reordering,
                                onCheckedChange = { onToggle(row, !it) },
                            )
                        }
                    }
                }

                item(key = "h-asleep") {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(
                        title = "眠っている（無効）",
                        count = state.asleep.size,
                        dotColor = palette.sleepInk,
                        hint = listOf(
                            "行をタップすると、有効にしてそのまま開きます。\nスイッチを OFF にすると、開かずに有効にできます。",
                            "「手動順」を選ぶと、右端の ≡ をドラッグして好きな順番に並び替えられます。",
                            "右上の「カテゴリ」または行のカテゴリタグのタップで、カテゴリを編集できます。",
                        ),
                        hintLabel = "眠っている状態の説明",
                        trailing = if (state.asleepTotalCount > 0 && !selecting) {
                            {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (reordering) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = palette.accent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .clickable(role = Role.Button) { exitReordering() },
                                        ) {
                                            Text(
                                                text = "完了",
                                                color = palette.accentInk,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            )
                                        }
                                    } else {
                                        SortMenuButton(
                                            current = state.asleepSortOrder,
                                            onSelect = { order ->
                                                onChangeSortOrder(order)
                                                if (order == AsleepSortOrder.MANUAL && state.asleep.size >= 2) {
                                                    onReorderingChange(true)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }

                if (state.asleepTotalCount > 0 || state.categories.isNotEmpty()) {
                    item(key = "f-asleep") {
                        CategoryFilterBar(
                            categories = state.categories,
                            selectedCategoryId = state.selectedCategoryId,
                            totalCount = state.asleepTotalCount,
                            counts = state.asleepCategoryCounts,
                            onSelect = onSelectCategory,
                            onLongPressCategory = { editTarget = it },
                            onAddCategory = { onOpenCategories(true) },
                        )
                    }
                }

                val displayAsleep = if (reordering) dragRows else state.asleep
                if (state.asleepTotalCount == 0) {
                    item(key = "e-asleep") { EmptyRow("眠っているアプリはありません") }
                } else if (displayAsleep.isEmpty()) {
                    item(key = "e-asleep-filtered") { EmptyRow("このカテゴリに眠っているアプリはありません") }
                }
                items(displayAsleep, key = { "s:${it.app.packageName}" }) { row ->
                    val pkg = row.app.packageName
                    val isBusy = pkg in state.busy
                    val isDragging = reordering && draggingPkg == pkg
                    AppRow(
                        icon = row.app.icon,
                        label = row.app.label,
                        sub = row.sub,
                        style = RowStyle.Asleep,
                        badge = StateBadge.Asleep,
                        isBusy = isBusy,
                        tag = row.categoryName,
                        onTagClick = if (selecting || reordering) null else ({ assignTarget = row }),
                        highlighted = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 12f
                                }
                            },
                        onClick = if (reordering) null else ({ if (selecting) toggleSelect(row, isBusy) else onTapRow(row) }),
                        onLongClick = if (selecting || reordering) null else ({ assignTarget = row }),
                    ) {
                        when {
                            reordering -> DragHandle(
                                active = isDragging,
                                modifier = Modifier.pointerInput(pkg) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingPkg = pkg
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val committed = dragRows.map { it.app.packageName }
                                            draggingPkg = null
                                            dragOffsetY = 0f
                                            onReorderAsleep(committed)
                                        },
                                        onDragCancel = {
                                            draggingPkg = null
                                            dragOffsetY = 0f
                                            dragRows = state.asleep
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val currentIndex = dragRows.indexOfFirst { it.app.packageName == pkg }
                                            if (currentIndex < 0) return@detectDragGestures
                                            val layoutInfo = listState.layoutInfo
                                            val draggedInfo = layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == "s:$pkg" }
                                                ?: return@detectDragGestures
                                            val itemHeight = (draggedInfo.size + spacingPx).coerceAtLeast(1)
                                            if (dragOffsetY > itemHeight * 0.5f && currentIndex < dragRows.lastIndex) {
                                                val next = dragRows.toMutableList()
                                                val moved = next.removeAt(currentIndex)
                                                next.add(currentIndex + 1, moved)
                                                dragRows = next
                                                dragOffsetY -= itemHeight
                                            } else if (dragOffsetY < -itemHeight * 0.5f && currentIndex > 0) {
                                                val next = dragRows.toMutableList()
                                                val moved = next.removeAt(currentIndex)
                                                next.add(currentIndex - 1, moved)
                                                dragRows = next
                                                dragOffsetY += itemHeight
                                            }
                                            // 画面端に近いときは自動でスクロールする
                                            val visualCenter = draggedInfo.offset + draggedInfo.size / 2f + dragOffsetY
                                            if (visualCenter > layoutInfo.viewportEndOffset - autoScrollEdgePx) {
                                                coroutineScope.launch { listState.scrollBy(14f) }
                                            } else if (visualCenter < layoutInfo.viewportStartOffset + autoScrollEdgePx) {
                                                coroutineScope.launch { listState.scrollBy(-14f) }
                                            }
                                        },
                                    )
                                },
                            )
                            selecting -> selectCheck(row, isBusy)
                            state.shizukuReady -> QuicknapSwitch(
                                checked = true,
                                enabled = true,
                                onCheckedChange = { onToggle(row, !it) },
                            )
                            else -> ManualEnableButton(onClick = { onOpenAppDetails(row.app.packageName) })
                        }
                    }
                }
            }
        }
    }

    if (selecting) {
        val count = selected.size
        Button(
            onClick = {
                val rows = (state.awake + state.asleep).filter { it.app.packageName in selected }
                onRelease(rows)
                exitSelecting()
            },
            enabled = count > 0,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.accentInk,
                disabledContainerColor = palette.bgSubtle,
                disabledContentColor = palette.muted,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(52.dp),
        ) {
            Text(
                text = if (count > 0) "対象から外す（${count}件）" else "外すアプリを選んでください",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    }

    assignTarget?.let { target ->
        AssignCategoryDialog(
            appLabel = target.app.label,
            currentCategoryId = target.categoryId,
            categories = state.categories,
            onDismiss = { assignTarget = null },
            onSelect = { catId ->
                onAssignCategory(target.app.packageName, catId)
                assignTarget = null
            },
            onCreateNew = {
                createAssignPackage = target.app.packageName
                assignTarget = null
                showCreateDialog = true
            },
        )
    }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onDismiss = {
                showCreateDialog = false
                createAssignPackage = null
            },
            onConfirm = { name ->
                onCreateCategory(name, createAssignPackage)
                showCreateDialog = false
                createAssignPackage = null
            },
        )
    }

    editTarget?.let { target ->
        EditCategoryDialog(
            category = target,
            onDismiss = { editTarget = null },
            onRename = { newName ->
                onRenameCategory(target.id, newName)
                editTarget = null
            },
            onDelete = {
                onDeleteCategory(target.id)
                editTarget = null
            },
        )
    }
}

@Composable
private fun DragHandle(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQuicknapPalette.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.accentSoft else palette.bgSubtle.copy(alpha = 0.7f))
            .semantics { contentDescription = "ドラッグして並び替え" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "≡",
            color = if (active) palette.accent else palette.muted,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SortMenuButton(
    current: AsleepSortOrder,
    onSelect: (AsleepSortOrder) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = palette.bgSubtle,
            border = BorderStroke(1.dp, palette.line),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(role = Role.Button) { expanded = true },
        ) {
            Text(
                text = "↕ ${current.label}",
                color = palette.ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = palette.bgAwake,
        ) {
            for (order in AsleepSortOrder.entries) {
                val selected = order == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (selected) "✓ ${order.label}" else order.label,
                            color = if (selected) palette.accent else palette.ink,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(order)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryFilterBar(
    categories: List<Category>,
    selectedCategoryId: String?,
    totalCount: Int,
    counts: Map<String, Int>,
    onSelect: (String?) -> Unit,
    onLongPressCategory: (Category) -> Unit,
    onAddCategory: () -> Unit,
) {
    val uncategorizedCount = counts[CATEGORY_UNCATEGORIZED] ?: 0

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        if (categories.isNotEmpty()) {
            item(key = "chip-all") {
                FilterChipPill(
                    text = "すべて ($totalCount)",
                    selected = selectedCategoryId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(categories, key = { "chip-${it.id}" }) { cat ->
                val count = counts[cat.id] ?: 0
                FilterChipPill(
                    text = "${cat.name} ($count)",
                    selected = selectedCategoryId == cat.id,
                    onClick = { onSelect(cat.id) },
                    onLongClick = { onLongPressCategory(cat) },
                )
            }
            if (uncategorizedCount > 0) {
                item(key = "chip-uncat") {
                    FilterChipPill(
                        text = "未分類 ($uncategorizedCount)",
                        selected = selectedCategoryId == CATEGORY_UNCATEGORIZED,
                        onClick = { onSelect(CATEGORY_UNCATEGORIZED) },
                    )
                }
            }
        }
        item(key = "chip-add") {
            FilterChipPill(
                text = "＋ カテゴリ",
                selected = false,
                subtle = true,
                onClick = onAddCategory,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipPill(
    text: String,
    selected: Boolean,
    subtle: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = LocalQuicknapPalette.current
    val bgColor = when {
        selected -> palette.accent
        subtle -> palette.bgScreen
        else -> palette.bgSubtle
    }
    val textColor = when {
        selected -> palette.accentInk
        subtle -> palette.muted
        else -> palette.ink
    }
    val borderColor = when {
        selected -> palette.accent
        else -> palette.line
    }
    val shape = RoundedCornerShape(999.dp)

    Surface(
        shape = shape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(shape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                }
            ),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun AssignCategoryDialog(
    appLabel: String,
    currentCategoryId: String?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreateNew: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.bgAwake,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        title = {
            Column {
                Text(
                    text = appLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "カテゴリを選ぶ",
                    color = palette.muted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CategoryOptionRow(
                    label = "未分類",
                    selected = currentCategoryId == null,
                    onClick = { onSelect(null) },
                )
                for (cat in categories) {
                    CategoryOptionRow(
                        label = cat.name,
                        selected = currentCategoryId == cat.id,
                        onClick = { onSelect(cat.id) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onCreateNew,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "＋ 新しいカテゴリを作る",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "閉じる", color = palette.muted)
            }
        },
    )
}

@Composable
private fun CategoryOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) palette.accentSoft else palette.bgSubtle,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = if (selected) palette.accent else palette.ink,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text(text = "✓", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.bgAwake,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        title = { Text(text = "カテゴリを作成", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                placeholder = { Text("例: 店のポイント、旅行") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(text = "作成", color = if (trimmed.isNotEmpty()) palette.accent else palette.muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "キャンセル", color = palette.muted)
            }
        },
    )
}

@Composable
private fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    var name by remember(category.id) { mutableStateOf(category.name) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.bgAwake,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        title = { Text(text = "カテゴリを編集", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "削除しても、中のアプリは未分類に戻るだけです。",
                    color = palette.muted,
                    fontSize = 11.5.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(text = "保存", color = if (trimmed.isNotEmpty()) palette.accent else palette.muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text(text = "削除", color = palette.warnInk, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "キャンセル", color = palette.muted)
                }
            }
        },
    )
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🌙", fontSize = 36.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(text = "アプリを休ませましょう", color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "使わない間は通知もアイコンも非表示。\nタップ1回ですぐ起きます。",
            color = palette.muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.accentInk),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(text = "眠らせるアプリを選ぶ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** 右下の「アプリを選ぶ」ボタン */
@Composable
fun AddAppFab(onClick: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = palette.accent,
        contentColor = palette.accentInk,
        modifier = Modifier.semantics { contentDescription = "眠らせるアプリを選ぶ" },
    ) {
        Text(text = "＋", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

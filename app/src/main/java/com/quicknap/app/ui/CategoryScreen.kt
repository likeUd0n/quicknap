package com.quicknap.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicknap.app.data.Category
import com.quicknap.app.ui.theme.LocalQuicknapPalette

/**
 * カテゴリの専用管理画面。
 * 一覧でカテゴリをタップして名前変更・削除・所属アプリの一括チェック編集ができる（長押し不要）。
 */
@Composable
fun CategoryScreen(
    state: UiState,
    initialCreate: Boolean,
    onBack: () -> Unit,
    onSaveCategory: (id: String?, name: String, packageNames: Set<String>) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onAddHomeIcon: (String?) -> Unit,
) {
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(initialCreate) }

    val editingCategory = editingCategoryId?.let { id ->
        state.categories.firstOrNull { it.id == id }
    }

    if (creating || editingCategory != null) {
        val isNew = creating
        val target = if (isNew) null else editingCategory
        BackHandler {
            if (initialCreate && isNew) {
                onBack()
            } else {
                creating = false
                editingCategoryId = null
            }
        }
        CategoryEditorView(
            category = target,
            allManaged = state.allManaged,
            onBack = {
                if (initialCreate && isNew) {
                    onBack()
                } else {
                    creating = false
                    editingCategoryId = null
                }
            },
            onSave = { name, selectedPackages ->
                onSaveCategory(target?.id, name, selectedPackages)
                if (initialCreate && isNew) {
                    onBack()
                } else {
                    creating = false
                    editingCategoryId = null
                }
            },
            onDelete = target?.let { cat ->
                {
                    onDeleteCategory(cat.id)
                    editingCategoryId = null
                }
            },
            onAddHomeIcon = target?.let { cat ->
                { onAddHomeIcon(cat.id) }
            },
        )
    } else {
        CategoryListView(
            categories = state.categories,
            allManaged = state.allManaged,
            onBack = onBack,
            onCreate = { creating = true },
            onSelectCategory = { editingCategoryId = it.id },
        )
    }
}

@Composable
private fun CategoryListView(
    categories: List<Category>,
    allManaged: List<HomeRow>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSelectCategory: (Category) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val appsByCategory = remember(allManaged) { allManaged.groupBy { it.categoryId } }
    val uncategorized = appsByCategory[null].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "戻る" },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "←", color = palette.ink, fontSize = 20.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "カテゴリ",
                color = palette.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            HintButton(
                lines = listOf(
                    "カテゴリをタップすると、名前の変更・削除と\nアプリの追加・解除がまとめてできます。",
                ),
                label = "カテゴリ管理の説明",
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCreate) {
                Text(
                    text = "＋ 追加",
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (categories.isEmpty()) {
                item(key = "cat-empty") {
                    EmptyRow("カテゴリはまだありません。「＋ 追加」から作成できます。")
                }
            }
            items(categories, key = { it.id }) { cat ->
                val members = appsByCategory[cat.id].orEmpty()
                CategorySummaryCard(
                    category = cat,
                    members = members,
                    onClick = { onSelectCategory(cat) },
                )
            }
            if (uncategorized.isNotEmpty()) {
                item(key = "uncat-header") {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(
                        title = "未分類のアプリ",
                        count = uncategorized.size,
                        dotColor = palette.muted,
                    )
                }
                item(key = "uncat-card") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = palette.bgAsleep,
                        border = BorderStroke(1.dp, palette.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "上のカテゴリをタップすると、未分類のアプリをまとめて追加できます。",
                                color = palette.muted,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = uncategorized.joinToString("、") { it.app.label },
                                color = palette.ink,
                                fontSize = 12.5.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySummaryCard(
    category: Category,
    members: List<HomeRow>,
    onClick: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val shape = RoundedCornerShape(16.dp)
    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Surface(
        shape = shape,
        color = palette.bgAwake,
        border = BorderStroke(1.dp, palette.line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = category.name,
                        color = palette.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.bgSubtle,
                    ) {
                        Text(
                            text = "${members.size}件",
                            color = palette.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }

                if (members.isEmpty()) {
                    Text(
                        text = "アプリなし · タップして追加",
                        color = palette.muted,
                        fontSize = 12.sp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val preview = members.take(6)
                        for (row in preview) {
                            if (row.app.icon != null) {
                                Image(
                                    bitmap = row.app.icon,
                                    contentDescription = null,
                                    colorFilter = if (row.asleep) grayscaleFilter else null,
                                    alpha = if (row.asleep) 0.75f else 1f,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.bgSubtle),
                                )
                            }
                        }
                        if (members.size > preview.size) {
                            Text(
                                text = "+${members.size - preview.size}",
                                color = palette.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "›",
                color = palette.muted,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CategoryEditorView(
    category: Category?,
    allManaged: List<HomeRow>,
    onBack: () -> Unit,
    onSave: (name: String, selectedPackages: Set<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onAddHomeIcon: (() -> Unit)?,
) {
    val palette = LocalQuicknapPalette.current
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var selected by remember(category?.id) {
        mutableStateOf(
            if (category == null) {
                emptySet()
            } else {
                allManaged.filter { it.categoryId == category.id }.map { it.app.packageName }.toSet()
            }
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    // 編集を開いた時点で「すでに所属しているアプリ」を上に固定し、チェック操作中に並びが飛ばないようにする
    val orderedRows = remember(category?.id, allManaged.size) {
        val initialSelected = if (category == null) {
            emptySet()
        } else {
            allManaged.filter { it.categoryId == category.id }.map { it.app.packageName }.toSet()
        }
        allManaged.sortedWith(
            compareByDescending<HomeRow> { it.app.packageName in initialSelected }
                .thenBy { it.app.label.lowercase() }
        )
    }

    val trimmed = name.trim()
    val canSave = trimmed.isNotEmpty()

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = "戻る" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "←", color = palette.ink, fontSize = 20.sp)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (category == null) "カテゴリを作成" else "カテゴリを編集",
                    color = palette.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSave(trimmed, selected) },
                    enabled = canSave,
                ) {
                    Text(
                        text = "保存",
                        color = if (canSave) palette.accent else palette.muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                label = { Text("カテゴリ名", fontSize = 13.sp) },
                placeholder = { Text("例: 店のポイント、旅行", color = palette.muted, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.bgAwake,
                    unfocusedContainerColor = palette.bgAwake,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.line,
                    focusedTextColor = palette.ink,
                    unfocusedTextColor = palette.ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (onAddHomeIcon != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onAddHomeIcon,
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text(
                        text = "このカテゴリをホーム画面に置く",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionHeader(
                title = "このカテゴリに入れるアプリ",
                count = selected.size,
                dotColor = palette.accent,
            )

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (orderedRows.isEmpty()) {
                    item(key = "no-managed") {
                        EmptyRow("管理対象のアプリがありません")
                    }
                }
                items(orderedRows, key = { it.app.packageName }) { row ->
                    val pkg = row.app.packageName
                    val isChecked = pkg in selected
                    val otherCategory = row.categoryName?.takeIf { row.categoryId != category?.id }
                    val statusText = buildString {
                        append(if (row.asleep) "眠っている" else "起きている")
                        if (otherCategory != null) {
                            append(" · 現在: $otherCategory")
                        } else if (row.categoryId == null) {
                            append(" · 未分類")
                        }
                    }
                    CategoryAppCheckRow(
                        row = row,
                        subText = statusText,
                        checked = isChecked,
                        onToggle = { toggle(pkg) },
                    )
                }

                if (onDelete != null) {
                    item(key = "delete-category") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            border = BorderStroke(1.dp, palette.warnLine.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.warnBg,
                                contentColor = palette.warnInk,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            Text(
                                text = "このカテゴリを削除",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onSave(trimmed, selected) },
            enabled = canSave,
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
                text = if (category == null) "作成する（${selected.size}件）" else "保存する（${selected.size}件）",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (confirmDelete && category != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.bgAwake,
            titleContentColor = palette.ink,
            textContentColor = palette.ink,
            title = {
                Text(
                    text = "「${category.name}」を削除しますか？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "削除しても、中のアプリは未分類に戻るだけです。",
                    color = palette.muted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(text = "削除", color = palette.warnInk, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = "キャンセル", color = palette.muted)
                }
            },
        )
    }
}

@Composable
private fun CategoryAppCheckRow(
    row: HomeRow,
    subText: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val shape = RoundedCornerShape(14.dp)
    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) palette.accentSoft.copy(alpha = 0.45f) else palette.bgAwake)
            .border(
                width = 1.dp,
                color = if (checked) palette.accent.copy(alpha = 0.6f) else palette.line,
                shape = shape,
            )
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.app.icon != null) {
            Image(
                bitmap = row.app.icon,
                contentDescription = null,
                colorFilter = if (row.asleep) grayscaleFilter else null,
                alpha = if (row.asleep) 0.75f else 1f,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.bgSubtle),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.app.label,
                color = palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subText,
                color = palette.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = palette.accent,
                checkmarkColor = palette.accentInk,
                uncheckedColor = palette.muted,
            ),
        )
    }
}

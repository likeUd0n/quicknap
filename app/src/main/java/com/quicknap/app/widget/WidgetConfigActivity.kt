package com.quicknap.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicknap.app.QuicknapApp
import com.quicknap.app.data.LaunchableApp
import com.quicknap.app.data.QuicknapState
import com.quicknap.app.ui.theme.LocalQuicknapPalette
import com.quicknap.app.ui.theme.QuicknapTheme
import kotlin.math.roundToInt

/**
 * 1×1 コンパクトフォルダウィジェットをホーム画面に置くとき（または再設定時）に、
 * カテゴリ・ラベル表示・アイコンサイズを選ぶ画面。
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = application as QuicknapApp
        val initialCategoryId = WidgetPrefs.getCategory(this, appWidgetId)
        val initialShowLabel = WidgetPrefs.getShowLabel(this, appWidgetId)
        val initialScalePercent = WidgetPrefs.getIconScalePercent(this, appWidgetId)
        val initialVerticalAlign = WidgetPrefs.getVerticalAlign(this, appWidgetId)

        enableEdgeToEdge()
        setContent {
            QuicknapTheme {
                val palette = LocalQuicknapPalette.current
                val state by app.store.state.collectAsState(initial = QuicknapState())
                var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
                var showLabel by remember { mutableStateOf(initialShowLabel) }
                var iconScalePercent by remember { mutableIntStateOf(initialScalePercent) }
                var verticalAlign by remember { mutableStateOf(initialVerticalAlign) }
                var managedApps by remember { mutableStateOf<Map<String, LaunchableApp>>(emptyMap()) }

                LaunchedEffect(state.managedApps.keys) {
                    val loaded = app.installed.loadManagedApps(state.managedApps.keys)
                    managedApps = loaded.associateBy { it.packageName }
                }

                val totalCount = state.managedApps.size
                val countByCategory = state.managedApps.values
                    .mapNotNull { it.categoryId }
                    .groupingBy { it }
                    .eachCount()

                val selectedCategoryName = state.categories
                    .firstOrNull { it.id == selectedCategoryId }
                    ?.name
                val previewLabel = selectedCategoryName ?: "Quicknap"
                val previewIcons = remember(state.managedApps, selectedCategoryId, managedApps) {
                    state.managedApps.values
                        .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
                        .take(4)
                        .mapNotNull { managedApps[it.packageName]?.icon }
                }

                Scaffold(
                    containerColor = palette.bgScreen,
                    bottomBar = {
                        Surface(
                            color = palette.bgScreen,
                            border = BorderStroke(1.dp, palette.line),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Button(
                                    onClick = {
                                        confirmSelection(
                                            categoryId = selectedCategoryId,
                                            showLabel = showLabel,
                                            iconScalePercent = iconScalePercent,
                                            verticalAlign = verticalAlign,
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                ) {
                                    Text(
                                        text = "この設定で保存する",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                    ) {
                        item(key = "header") {
                            Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                                Text(
                                    text = "フォルダウィジェットの設定",
                                    color = palette.ink,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "ホーム画面での見た目と、表示するカテゴリを選べます。",
                                    color = palette.muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        item(key = "preview") {
                            WidgetPreviewCard(
                                label = previewLabel,
                                showLabel = showLabel,
                                iconScalePercent = iconScalePercent,
                                verticalAlign = verticalAlign,
                                icons = previewIcons,
                            )
                        }

                        item(key = "appearance") {
                            AppearanceSettingsCard(
                                showLabel = showLabel,
                                onShowLabelChange = { showLabel = it },
                                iconScalePercent = iconScalePercent,
                                onIconScaleChange = { iconScalePercent = it },
                                verticalAlign = verticalAlign,
                                onVerticalAlignChange = { verticalAlign = it },
                            )
                        }

                        item(key = "category_title") {
                            Text(
                                text = "表示するカテゴリ",
                                color = palette.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                        }

                        item(key = "all") {
                            CategoryOptionRow(
                                title = "すべて（眠っているアプリ）",
                                subtitle = "眠らせる対象すべて（${totalCount}件）",
                                selected = selectedCategoryId == null,
                                onClick = { selectedCategoryId = null },
                            )
                        }

                        items(state.categories, key = { it.id }) { cat ->
                            val count = countByCategory[cat.id] ?: 0
                            CategoryOptionRow(
                                title = cat.name,
                                subtitle = "${count}件のアプリ",
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                            )
                        }

                        if (state.categories.isEmpty()) {
                            item(key = "hint") {
                                Text(
                                    text = "※ Quicknap のホーム画面でカテゴリを作ると、カテゴリ別のフォルダを置けます。",
                                    color = palette.muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                )
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    private fun confirmSelection(
        categoryId: String?,
        showLabel: Boolean,
        iconScalePercent: Int,
        verticalAlign: WidgetVerticalAlign,
    ) {
        WidgetPrefs.saveAll(this, appWidgetId, categoryId, showLabel, iconScalePercent, verticalAlign)
        QuicknapWidgetProvider.updateWidget(this, appWidgetId)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
private fun WidgetPreviewCard(
    label: String,
    showLabel: Boolean,
    iconScalePercent: Int,
    verticalAlign: WidgetVerticalAlign,
    icons: List<ImageBitmap>,
) {
    val baseCellDp = 58.dp
    val cellHeightDp = 70.dp
    val scaledBoxDp = baseCellDp * (iconScalePercent / 100f)
    val widgetAlignment = when (verticalAlign) {
        WidgetVerticalAlign.TOP -> Alignment.TopCenter
        WidgetVerticalAlign.CENTER -> Alignment.Center
        WidgetVerticalAlign.BOTTOM -> Alignment.BottomCenter
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF181C24),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "ホーム画面プレビュー（隣のアイコンとの比較）",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // 比較用の標準アイコン（ランチャー同様にセル内で下揃え）
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .width(baseCellDp)
                            .height(cellHeightDp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(baseCellDp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF334155)),
                        ) {
                            Text(
                                text = "他",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (showLabel) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "他のアプリ",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }

                // Quicknap フォルダプレビュー
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = widgetAlignment,
                        modifier = Modifier
                            .width(baseCellDp)
                            .height(cellHeightDp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(scaledBoxDp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x4DFFFFFF))
                                .padding(5.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    PreviewMiniTile(icons.getOrNull(0), Modifier.weight(1f))
                                    PreviewMiniTile(icons.getOrNull(1), Modifier.weight(1f))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    PreviewMiniTile(icons.getOrNull(2), Modifier.weight(1f))
                                    PreviewMiniTile(icons.getOrNull(3), Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (showLabel) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(68.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMiniTile(icon: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.22f)),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    showLabel: Boolean,
    onShowLabelChange: (Boolean) -> Unit,
    iconScalePercent: Int,
    onIconScaleChange: (Int) -> Unit,
    verticalAlign: WidgetVerticalAlign,
    onVerticalAlignChange: (WidgetVerticalAlign) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.bgAwake,
        border = BorderStroke(1.dp, palette.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "フォルダ名を表示",
                        color = palette.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "OFFにすると他のアイコンと見た目を揃えやすくなります",
                        color = palette.muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = showLabel,
                    onCheckedChange = onShowLabelChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.accent,
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "アイコンの大きさ",
                    color = palette.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (iconScalePercent != WidgetPrefs.DEFAULT_ICON_SCALE_PERCENT) {
                    Text(
                        text = "標準に戻す",
                        color = palette.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onIconScaleChange(WidgetPrefs.DEFAULT_ICON_SCALE_PERCENT) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${iconScalePercent}%",
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Slider(
                value = iconScalePercent.toFloat(),
                onValueChange = { onIconScaleChange(it.roundToInt()) },
                valueRange = WidgetPrefs.MIN_ICON_SCALE_PERCENT.toFloat()..WidgetPrefs.MAX_ICON_SCALE_PERCENT.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                ),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "小さめ (${WidgetPrefs.MIN_ICON_SCALE_PERCENT}%)",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = "中 (85%)",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = "標準 (${WidgetPrefs.DEFAULT_ICON_SCALE_PERCENT}%)",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "縦の位置",
                color = palette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "隣のアイコンと高さがずれるときに調整します",
                color = palette.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (option in WidgetVerticalAlign.entries) {
                    val selected = option == verticalAlign
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) palette.accentSoft else palette.bgSubtle,
                        border = BorderStroke(1.dp, if (selected) palette.accent else palette.line),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onVerticalAlignChange(option) },
                    ) {
                        Text(
                            text = option.label,
                            color = if (selected) palette.accent else palette.ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.accentSoft else palette.bgAwake,
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (selected) palette.accent else palette.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = palette.muted,
                    fontSize = 12.sp,
                )
            }
            if (selected) {
                Text(
                    text = "選択中",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

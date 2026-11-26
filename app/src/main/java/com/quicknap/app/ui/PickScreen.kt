package com.quicknap.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicknap.app.data.QuicknapState
import com.quicknap.app.ui.theme.LocalQuicknapPalette

@Composable
fun PickScreen(
    state: UiState,
    onBack: () -> Unit,
    onPicked: (PickRow, Boolean) -> Unit,
    onChangeDays: (Int) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var cautionOpen by rememberSaveable { mutableStateOf(false) }

    // 画面を開いた時点で「まだ管理対象でない（眠っていない）アプリ」だけを表示対象・並び順として固定する
    // （段階ロードで管理対象だけが先に来る場合があるため、未管理アプリが揃ってから固定する）
    val loaded = state.pickMain.any { !it.managed } || state.pickCaution.isNotEmpty()
    val frozen = remember(loaded) {
        FrozenOrder(
            mainOrder = state.pickMain
                .filter { !it.managed }
                .mapIndexed { i, r -> r.app.packageName to i }
                .toMap(),
            cautionOrder = state.pickCaution
                .filter { !it.managed }
                .mapIndexed { i, r -> r.app.packageName to i }
                .toMap(),
        )
    }

    val q = query.trim()
    val all = state.pickMain + state.pickCaution
    val matched = remember(all, q) { all.filter { it.app.label.contains(q, ignoreCase = true) } }
    val main = remember(matched, frozen) {
        matched.filter { it.app.packageName in frozen.mainOrder }
            .sortedBy { frozen.mainOrder[it.app.packageName] ?: Int.MAX_VALUE }
    }
    val caution = remember(matched, frozen) {
        matched.filter { it.app.packageName in frozen.cautionOrder }
            .sortedBy { frozen.cautionOrder[it.app.packageName] }
    }
    // 検索中は注意アプリも見えるように自動で開く
    val showCaution = cautionOpen || (q.isNotEmpty() && caution.isNotEmpty())

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(text = "眠らせるアプリ", color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            HintButton(
                lines = listOf("スイッチを ON にすると、すぐ眠らせます。\nOFF にすると対象から外れます。"),
                label = "選ぶ画面の説明",
            )
        }

        Spacer(Modifier.height(8.dp))
        DaysStrip(days = state.autoSleepDays, onChange = onChangeDays)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("アプリ名で検索", color = palette.muted, fontSize = 14.sp) },
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
        Spacer(Modifier.height(10.dp))

        if (state.loading && state.pickMain.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (main.isEmpty() && caution.isEmpty()) {
                item(key = "empty") {
                    EmptyRow(if (q.isEmpty()) "追加できるアプリはありません" else "見つかりませんでした")
                }
            }
            items(main, key = { "m:${it.app.packageName}" }) { row ->
                PickAppRow(row = row, state = state, onPicked = onPicked)
            }
            if (caution.isNotEmpty()) {
                item(key = "caution-head") {
                    CautionFoldRow(count = caution.size, open = showCaution, onToggle = { cautionOpen = !showCaution })
                }
                if (showCaution) {
                    items(caution, key = { "c:${it.app.packageName}" }) { row ->
                        PickAppRow(row = row, state = state, onPicked = onPicked)
                    }
                }
            }
        }
    }
}

private data class FrozenOrder(
    val mainOrder: Map<String, Int>,
    val cautionOrder: Map<String, Int>,
)

@Composable
private fun PickAppRow(row: PickRow, state: UiState, onPicked: (PickRow, Boolean) -> Unit) {
    // Shizuku 停止中は pm が要る操作を止め、「最近起こした」を対象から外す操作だけ許す
    val canToggle = state.shizukuReady || (row.managed && !row.asleep)
    val caution = !row.managed && row.cautionReason != null
    AppRow(
        icon = row.app.icon,
        label = row.app.label,
        sub = if (caution) "注意 · ${row.cautionReason}" else row.sub,
        style = when {
            caution -> RowStyle.Caution
            row.managed && row.asleep -> RowStyle.Asleep
            else -> RowStyle.Awake
        },
        badge = when {
            !row.managed -> StateBadge.None
            row.asleep -> StateBadge.Asleep
            else -> StateBadge.Awake
        },
        isBusy = row.app.packageName in state.busy,
        onClick = if (canToggle) ({ onPicked(row, !row.managed) }) else null,
    ) {
        QuicknapSwitch(
            checked = row.managed,
            enabled = canToggle,
            onCheckedChange = { onPicked(row, it) },
        )
    }
}

@Composable
private fun DaysStrip(days: Int, onChange: (Int) -> Unit) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.bgAwake,
        border = BorderStroke(1.dp, palette.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "自動休止", color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            HintButton(
                lines = listOf("全アプリ共通（1〜30日）。\n最後に使ってからこの日数が経つと、\n自動で眠ります。"),
                label = "自動休止日数の説明",
            )
            Spacer(Modifier.weight(1f))
            StepButton(
                text = "−",
                label = "1日減らす",
                enabled = days > QuicknapState.MIN_AUTO_SLEEP_DAYS,
                onClick = { onChange(-1) },
            )
            Text(
                text = "${days}日",
                color = palette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            StepButton(
                text = "＋",
                label = "1日増やす",
                enabled = days < QuicknapState.MAX_AUTO_SLEEP_DAYS,
                onClick = { onChange(1) },
            )
        }
    }
}

@Composable
private fun StepButton(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) palette.accent else palette.muted.copy(alpha = 0.4f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CautionFoldRow(count: Int, open: Boolean, onToggle: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "注意が必要なアプリ（${count}件）",
            color = palette.warnInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        HintButton(
            lines = listOf("決済・認証・アラーム・キーボードなど、\n眠らせると困りやすいアプリです。"),
            label = "注意が必要なアプリの説明",
        )
        Spacer(Modifier.weight(1f))
        Text(text = if (open) "閉じる ▴" else "表示 ▾", color = palette.warnInk, fontSize = 12.sp)
    }
}

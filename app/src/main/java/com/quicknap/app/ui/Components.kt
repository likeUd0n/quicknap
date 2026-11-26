package com.quicknap.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.quicknap.app.ui.theme.LocalQuicknapPalette

enum class RowStyle { Awake, Asleep, Caution }

enum class StateBadge { Awake, Asleep, None }

/** アプリ1行。起きている＝明るいカード・カラーアイコン、眠っている＝沈んだ色・破線枠・モノクロ */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    icon: ImageBitmap?,
    label: String,
    sub: String,
    style: RowStyle,
    badge: StateBadge,
    isBusy: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    tag: String? = null,
    subAccent: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    val shape = RoundedCornerShape(16.dp)
    val asleep = style == RowStyle.Asleep
    val bgColor = when (style) {
        RowStyle.Awake -> palette.bgAwake
        RowStyle.Asleep -> palette.bgAsleep
        RowStyle.Caution -> palette.warnBg
    }
    val dashColor = palette.line

    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(bgColor)
    val rowModifier = when (style) {
        RowStyle.Awake -> base.border(1.dp, palette.line, shape)
        RowStyle.Caution -> base.border(1.dp, palette.warnLine.copy(alpha = 0.55f), shape)
        RowStyle.Asleep -> base.drawBehind {
            drawRoundRect(
                color = dashColor,
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                ),
            )
        }
    }

    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Row(
        modifier = rowModifier
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        enabled = !isBusy,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(enabled = !isBusy, role = Role.Button, onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    colorFilter = if (asleep) grayscaleFilter else null,
                    alpha = if (asleep) 0.7f else 1f,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.bgSubtle),
                )
            }

            if (badge != StateBadge.None) {
                val awake = badge == StateBadge.Awake
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (awake) palette.accent else palette.sleepInk)
                        .border(2.dp, bgColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (awake) "✓" else "🌙",
                        color = if (awake) palette.accentInk else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (asleep) palette.muted else palette.ink,
                fontSize = 14.sp,
                fontWeight = if (asleep) FontWeight.SemiBold else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isBusy) "切り替え中…" else sub,
                    color = when {
                        style == RowStyle.Caution -> palette.warnInk
                        asleep -> palette.sleepInk
                        subAccent -> palette.accent
                        else -> palette.muted
                    },
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tag != null && !isBusy) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.bgSubtle,
                    ) {
                        Text(
                            text = tag,
                            color = palette.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = palette.accent,
            )
        } else {
            trailing()
        }
    }
}

@Composable
fun QuicknapSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalQuicknapPalette.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = palette.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = palette.sleepInk.copy(alpha = 0.35f),
            uncheckedBorderColor = palette.sleepInk.copy(alpha = 0.5f),
        ),
    )
}

/** Shizuku 停止中の退避路：Android のアプリ情報から手動で有効にする */
@Composable
fun ManualEnableButton(onClick: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, palette.warnLine.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.warnBg,
            contentColor = palette.warnInk,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(text = "設定で有効化 ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    dotColor: Color,
    hint: List<String>? = null,
    hintLabel: String = "説明",
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalQuicknapPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(text = title, color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(999.dp), color = palette.bgSubtle) {
            Text(
                text = "$count",
                color = palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        if (hint != null) HintButton(lines = hint, label = hintLabel)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun EmptyRow(message: String) {
    val palette = LocalQuicknapPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgAsleep)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = palette.muted, fontSize = 12.sp)
    }
}

/** 画面上の説明文の代わりに置く「?」。タップで短い補足を出す */
@Composable
fun HintButton(lines: List<String>, label: String) {
    val palette = LocalQuicknapPalette.current
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val provider = remember(density) {
        with(density) { HintPositionProvider(gapPx = 6.dp.roundToPx(), marginPx = 12.dp.roundToPx()) }
    }

    Box {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = !open }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.muted.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // 行の上下の余白を詰めて、? の字面を円の中心にそろえる
                Text(
                    text = "?",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        lineHeight = 11.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
        if (open) {
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.ink,
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (line in lines) {
                            Text(text = line, color = palette.bgScreen, fontSize = 12.5.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

private class HintPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(marginPx, maxX)
        var y = anchorBounds.bottom + gapPx
        if (y + popupContentSize.height > windowSize.height - marginPx) {
            y = (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

@Composable
fun ShizukuStoppedBanner(onResume: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.warnBg,
        border = BorderStroke(1.dp, palette.warnLine.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚠ Shizuku が停止中",
                    color = palette.warnInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                HintButton(
                    lines = listOf(
                        "再起動などで Shizuku が止まると、\n自動休止とスイッチが使えません。",
                        "「再開する」で Shizuku を起動するか、\n各アプリの「設定で有効化 ↗」から\n手動で有効にできます。",
                    ),
                    label = "Shizuku 停止中の説明",
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.warnLine,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(text = "再開する", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "トグル切替と自動休止が止まっています。",
                color = palette.warnInk,
                fontSize = 11.5.sp,
            )
        }
    }
}

@Composable
fun ShizukuPermissionBanner(onRequest: () -> Unit) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.accentSoft,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Shizuku の利用許可が必要です",
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.accentInk,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(text = "許可する", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 画面下のトースト。アプリ名があれば1行目（長ければ省略）、結果を2行目に出して文中で折り返さないようにする */
class QuicknapSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val subject: String?,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

@Composable
fun QuicknapSnackbar(data: SnackbarData) {
    val palette = LocalQuicknapPalette.current
    val subject = (data.visuals as? QuicknapSnackbarVisuals)?.subject
    Snackbar(
        modifier = Modifier.padding(12.dp),
        containerColor = palette.ink,
        contentColor = palette.bgScreen,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = { data.performAction() }) {
                    Text(text = label, color = palette.accentSoft, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        },
    ) {
        Column {
            if (subject != null) {
                Text(text = subject, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = data.visuals.message, fontSize = 13.sp)
        }
    }
}

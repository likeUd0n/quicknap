package com.quicknap.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicknap.app.shizuku.ShizukuStatus
import com.quicknap.app.ui.theme.LocalQuicknapPalette

/** 初回起動時に、必要な3つの権限を1画面でまとめて案内する */
@Composable
fun SetupScreen(
    state: UiState,
    onShizukuAction: () -> Unit,
    onOpenShizukuDetails: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = LocalQuicknapPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "はじめに", color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "たまにしか使わないアプリに、\nちょっと昼寝してもらうアプリです。\n使うには、次の3つを許可してください。",
            color = palette.muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(4.dp))

        val (shizukuDone, shizukuAction) = when {
            !state.shizukuInstalled -> false to null
            state.shizuku == ShizukuStatus.Ready -> true to null
            state.shizuku == ShizukuStatus.PermissionRequired -> false to "許可する"
            else -> false to "開く"
        }
        SetupItem(
            number = 1,
            title = "Shizuku",
            description = if (state.shizukuInstalled) {
                "アプリを眠らせる・起こすのに使います。\n先に Shizuku を開始してください。"
            } else {
                "アプリを眠らせる・起こすのに使います。\nShizuku をインストールしてください。"
            },
            done = shizukuDone,
            actionLabel = shizukuAction,
            onAction = onShizukuAction,
        ) {
            if (state.shizukuInstalled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.warnBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
                        Text(
                            text = "HyperOS では Shizuku の設定で\n「自動起動」を ON、\n「バッテリー節約」を「制限なし」に。",
                            color = palette.warnInk,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                        TextButton(
                            onClick = onOpenShizukuDetails,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(text = "Shizuku のアプリ情報を開く ↗", color = palette.warnInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        SetupItem(
            number = 2,
            title = "使用状況へのアクセス",
            description = "最後に使った日を調べて、\n自動で眠らせる時期を決めます。",
            done = state.usageAccess,
            actionLabel = "設定を開く",
            onAction = onOpenUsageAccess,
        )

        SetupItem(
            number = 3,
            title = "通知",
            description = "自動で眠らせられなかったときだけ、\n1回お知らせします。",
            done = state.notificationsAllowed,
            actionLabel = "許可する",
            onAction = onRequestNotifications,
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.accentInk),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "はじめる", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (!(shizukuDone && state.usageAccess && state.notificationsAllowed)) {
            Text(
                text = "あとから許可しても使えます。",
                color = palette.muted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SetupItem(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val palette = LocalQuicknapPalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.bgAwake,
        border = BorderStroke(1.dp, if (done) palette.accent.copy(alpha = 0.5f) else palette.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$number",
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    color = palette.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                when {
                    done -> Text(text = "✓ 完了", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    actionLabel != null -> Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentInk,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Text(text = description, color = palette.muted, fontSize = 12.5.sp, lineHeight = 19.sp)
            extra()
        }
    }
}

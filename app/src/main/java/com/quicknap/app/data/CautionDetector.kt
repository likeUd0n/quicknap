package com.quicknap.app.data

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 眠らせると困りやすいアプリ（非推奨）を判定する。
 * OS から取れる情報（IME・ホーム・アラーム・ユーザー補助・端末管理）と、決済・認証・パスワード管理の軽量パターンで判定する。
 * ウィジェット提供の有無は使わない（ほとんどのアプリが持っており、実際に置かれているかは他アプリから取れないため）。
 */
class CautionDetector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** パッケージ名 → 注意の理由（短いラベル） */
    suspend fun detect(candidates: Collection<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, String>()
        fun mark(pkg: String?, reason: String) {
            if (pkg != null && pkg in candidates && pkg !in result) result[pkg] = reason
        }

        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.inputMethodList?.forEach { mark(it.packageName, "キーボード") }
        }
        runCatching {
            queryActivityPackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                .forEach { mark(it, "ホームアプリ") }
        }
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java)
                ?.activeAdmins?.forEach { mark(it.packageName, "端末管理") }
        }
        runCatching {
            context.getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.forEach { mark(it.resolveInfo?.serviceInfo?.packageName, "ユーザー補助") }
        }
        runCatching {
            queryActivityPackages(Intent(AlarmClock.ACTION_SET_ALARM)).forEach { mark(it, "アラーム") }
        }

        for (pkg in candidates) {
            if (pkg in result) continue
            if (requestsPermission(pkg, Manifest.permission.USE_EXACT_ALARM)) {
                mark(pkg, "アラーム")
                continue
            }
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault("")
            when {
                PASSWORD_PATTERN.containsMatchIn(pkg) || PASSWORD_PATTERN.containsMatchIn(label) ->
                    mark(pkg, "パスワード管理")
                PAYMENT_PACKAGE.containsMatchIn(pkg) || PAYMENT_LABEL.containsMatchIn(label) ->
                    mark(pkg, "決済・認証")
            }
        }

        result
    }

    private fun queryActivityPackages(intent: Intent): List<String> {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { it.activityInfo?.packageName }
    }

    private fun requestsPermission(pkg: String, permission: String): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.contains(permission) == true
    }.getOrDefault(false)

    private companion object {
        val PAYMENT_PACKAGE = Regex(
            "bank|wallet|authenticator|authy|aegis|jpki|mynaportal|paypay|\\.pay(\\.|$)|" +
                "smbc|mufg|mizuho|resona|jpbank|" +
                "credit|rakuten[_.]?card|vpass|smcc|myjcb|eposcard|saison|dcard|" +
                "osaifu|felica|quicpay|rakuten\\.edy",
            RegexOption.IGNORE_CASE,
        )
        val PASSWORD_PATTERN = Regex(
            "bitwarden|1password|keepass|lastpass|dashlane|protonpass|proton\\.pass|パスワード",
            RegexOption.IGNORE_CASE,
        )
        val PAYMENT_LABEL = Regex(
            "銀行|ペイ|Pay|払い|ウォレット|Wallet|認証|Authenticator|Aegis|ワンタイム|" +
                "マイナ|JPKI|証券|Suica|PASMO|ICOCA|Edy|QUICPay|WAON|nanaco|おサイフ|" +
                "クレジット|楽天カード|三井住友カード|dカード|イオンカード|PayPayカード|" +
                "ビューカード|エポス|セゾン|オリコ|Vpass|JCB|アメックス|AMEX",
        )
    }
}

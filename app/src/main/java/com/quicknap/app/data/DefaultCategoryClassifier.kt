package com.quicknap.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * 設定不要で使い始められるよう、デフォルトカテゴリと軽量な自動分類ルールを提供する。
 * 完璧な分類ではなく「初期状態のたたき台」として機能し、ユーザーが後から自由に編集・削除できる。
 */
class DefaultCategoryClassifier(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun classify(packageName: String, labelOverride: String? = null): String? {
        val appInfo = getApplicationInfoOrNull(packageName)
        val label = labelOverride ?: appInfo?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        }.orEmpty()
        val osCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        } else {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return classifyByMetadata(packageName, label, osCategory)
    }

    private fun getApplicationInfoOrNull(packageName: String): ApplicationInfo? = runCatching {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, flags)
        }
    }.getOrNull()

    companion object {
        const val ID_STORE = "default_store"
        const val ID_UTILITY = "default_utility"
        const val ID_SNS = "default_sns"
        const val ID_MEDIA = "default_media"
        const val ID_GAME = "default_game"

        val DEFAULT_CATEGORIES: List<Category> = listOf(
            Category(id = ID_STORE, name = "ストア"),
            Category(id = ID_UTILITY, name = "ユーティリティ"),
            Category(id = ID_SNS, name = "SNS"),
            Category(id = ID_MEDIA, name = "メディア"),
            Category(id = ID_GAME, name = "ゲーム"),
        )

        internal fun classifyByMetadata(
            packageName: String,
            label: String,
            osCategory: Int,
        ): String? {
            // 1. OS のゲーム分類は精度が高いため最優先
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                osCategory == ApplicationInfo.CATEGORY_GAME
            ) {
                return ID_GAME
            }

            // 2. パッケージ名・アプリ名によるキーワード分類（店・ポイント・日本アプリ重視）
            if (GAME_PACKAGE.containsMatchIn(packageName)) return ID_GAME
            if (STORE_PACKAGE.containsMatchIn(packageName) || STORE_LABEL.containsMatchIn(label)) {
                return ID_STORE
            }
            if (SNS_PACKAGE.containsMatchIn(packageName) || SNS_LABEL.containsMatchIn(label)) {
                return ID_SNS
            }
            if (MEDIA_PACKAGE.containsMatchIn(packageName) || MEDIA_LABEL.containsMatchIn(label)) {
                return ID_MEDIA
            }
            if (UTILITY_PACKAGE.containsMatchIn(packageName) || UTILITY_LABEL.containsMatchIn(label)) {
                return ID_UTILITY
            }

            // 3. キーワードに当たらない場合は Android の標準カテゴリをフォールバックに使う
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return when (osCategory) {
                    ApplicationInfo.CATEGORY_SOCIAL -> ID_SNS
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_IMAGE,
                    -> ID_MEDIA
                    ApplicationInfo.CATEGORY_PRODUCTIVITY,
                    ApplicationInfo.CATEGORY_MAPS,
                    -> ID_UTILITY
                    else -> null
                }
            }
            return null
        }

        private val GAME_PACKAGE = Regex(
            "hoyoverse|mihoyo|supercell|bandainamco|cygames|kairosoft|mojang\\.minecraft|" +
                "roblox|nianticlabs|pokemon|proseka|prsk|musedash|slaythespire|deadcells|" +
                "idolmaster|gakumas|shinycolors|playdigious|nexon|yostar|hypergryph",
            RegexOption.IGNORE_CASE,
        )

        private val STORE_PACKAGE = Regex(
            "mcdonalds|misterdonut|misdo|burgerking|starbucks|veloce|doutor|tullys|komeda|" +
                "sukiya|yoshinoya|matsuya|kurasushi|sushiro|hamasushi|saizeriya|skylark|gusto|" +
                "marugame|cocoichi|seveneleven|\\.sej\\.|famima|familymart|lawson|ministop|" +
                "aeon|uniqlo|gu_global|muji|nitori|daiso|seria|donki|majica|" +
                "yodobashi|biccamera|yamada|edion|joshin|" +
                "matsukiyo|welcia|tsuruha|sugi|cosmos|sundrug|cocokara|" +
                "rakuten\\.android|amazon\\.mShop|mercari|zozo|qoo10|shein|temu|aliexpress|" +
                "ponta|dpoint|vpoint|tpoint|\\.point(\\.|$)|coupon|member|" +
                "hotpepper|tabelog|gurunavi|ubereats|demaecan|wolt|" +
                "kinokuniya|tsutaya|bookoff|hardoff|ikea|costco|kaldi|workman|shimamura|" +
                "cainz|kohnan|seiyu|itoyokado|yaoko|belc|shopping|\\.store(\\.|$)|\\.shop(\\.|$)",
            RegexOption.IGNORE_CASE,
        )

        private val STORE_LABEL = Regex(
            "マクドナルド|マック|ミスド|ミスタードーナツ|バーガーキング|スタバ|スターバックス|" +
                "ベローチェ|ドトール|タリーズ|コメダ|すき家|吉野家|松屋|くら寿司|スシロー|はま寿司|" +
                "サイゼ|ガスト|丸亀|ココイチ|セブン|ファミマ|ローソン|ミニストップ|" +
                "イオン|ユニクロ|無印|ニトリ|ダイソー|セリア|ドンキ|" +
                "ヨドバシ|ビックカメラ|ヤマダ|エディオン|ジョーシン|" +
                "マツキヨ|ウエルシア|ツルハ|スギ薬局|サンドラッグ|ココカラ|" +
                "楽天市場|Amazon ショッピング|メルカリ|ZOZO|ポイント|クーポン|会員証|" +
                "公式アプリ|ドラッグ|薬局|ストア|ショップ|モール|通販|出前館|Uber Eats",
        )

        private val SNS_PACKAGE = Regex(
            "instagram|twitter|\\.x\\.android|threads|facebook|tiktok|musically|snapchat|" +
                "discord|reddit|bsky|mastodon|misskey|pixiv|pinterest|tumblr|weibo|" +
                "naver\\.line|whatsapp|telegram|signal|slack|bereal|mixi|ameba",
            RegexOption.IGNORE_CASE,
        )

        private val SNS_LABEL = Regex(
            "Instagram|インスタ|Twitter|Threads|Facebook|TikTok|Discord|Reddit|" +
                "Bluesky|Mastodon|pixiv|Pinterest|LINE|WhatsApp|Telegram|Signal|Slack|BeReal|mixi",
        )

        private val MEDIA_PACKAGE = Regex(
            "twitch|amazon\\.avod|primevideo|netflix|unext|abema|tver|youtube|nicovideo|niconico|" +
                "bilibili|dazn|hulu|disneyplus|dmm\\.tv|lemino|telasa|spotify|soundcloud|radiko|" +
                "audible|kindle|kobo|bookwalker|manga|comic|jumpplus|videolan\\.vlc|mxtech|plex|" +
                "podcast|showroom|twitcasting|mirrativ|iriam|kakuyomu",
            RegexOption.IGNORE_CASE,
        )

        private val MEDIA_LABEL = Regex(
            "Twitch|Prime Video|プライムビデオ|Netflix|U-NEXT|ABEMA|TVer|YouTube|ニコニコ|" +
                "Hulu|Disney|DAZN|Spotify|radiko|Kindle|BOOK WALKER|マンガ|漫画|コミック|" +
                "動画|ビデオ|配信|ラジオ|ポッドキャスト|VLC|カクヨム",
        )

        private val UTILITY_PACKAGE = Regex(
            "epson|canon|brother|hp\\.printer|ricoh|print|scanner|scan|" +
                "recorder|voicerecorder|soundrecorder|dictaphone|" +
                "calculator|flashlight|torch|compass|qrcode|barcode|translate|deepl|dictionary|" +
                "filemanager|solidexplorer|cxinventor|archive|\\.pdf|adobe\\.reader|" +
                "notion|evernote|notally|obsidian|ticktick|weather|tenki|" +
                "navitime|ekispert|jorudan|transit|vpn|wireguard|tailscale|cloudflare|adguard|" +
                "speedtest|remote|termux|povo|jcom|ymobile|uqmobile|mineo|iijmio|ekinet|smartex",
            RegexOption.IGNORE_CASE,
        )

        private val UTILITY_LABEL = Regex(
            "Epson|エプソン|Canon|キヤノン|Brother|ブラザー|プリンタ|印刷|スキャン|Scanner|" +
                "Recorder|レコーダー|録音|ボイス|電卓|翻訳|DeepL|辞書|ファイル|PDF|QR|" +
                "メモ|ノート|天気|乗換|駅すぱあと|VPN|リモコン|タイマー|ユーティリティ|ツール|" +
                "povo|J:COM|マイページ",
        )
    }
}

package com.quicknap.app

import android.app.Application
import com.quicknap.app.data.CautionDetector
import com.quicknap.app.data.DefaultCategoryClassifier
import com.quicknap.app.data.InstalledAppRepository
import com.quicknap.app.data.ManagedAppStore
import com.quicknap.app.data.UsageRepository
import com.quicknap.app.domain.AutoSleepNotifier
import com.quicknap.app.domain.ManualWakeWatcher
import com.quicknap.app.domain.SleepController
import com.quicknap.app.shizuku.ShizukuPackageController
import com.quicknap.app.widget.QuicknapWidgetProvider
import com.quicknap.app.work.AutoSleepWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class QuicknapApp : Application() {

    lateinit var store: ManagedAppStore
        private set
    lateinit var installed: InstalledAppRepository
        private set
    lateinit var usage: UsageRepository
        private set
    lateinit var caution: CautionDetector
        private set
    lateinit var classifier: DefaultCategoryClassifier
        private set
    lateinit var shizuku: ShizukuPackageController
        private set
    lateinit var notifier: AutoSleepNotifier
        private set
    lateinit var sleepController: SleepController
        private set
    lateinit var manualWake: ManualWakeWatcher
        private set

    override fun onCreate() {
        super.onCreate()
        store = ManagedAppStore(this)
        installed = InstalledAppRepository(this)
        usage = UsageRepository(this)
        caution = CautionDetector(this)
        classifier = DefaultCategoryClassifier(this)
        shizuku = ShizukuPackageController().also { it.register() }
        notifier = AutoSleepNotifier(this).also { it.ensureChannel() }
        sleepController = SleepController(store, installed, usage, shizuku, notifier, classifier)
        manualWake = ManualWakeWatcher(this)
        AutoSleepWorker.schedule(this)
        // カテゴリや対象アプリの変更をホーム画面の 1×1 ウィジェットへ自動反映する
        appScope.launch {
            store.state.drop(1).collect {
                QuicknapWidgetProvider.updateAll(this@QuicknapApp)
            }
        }
        // 初回のみデフォルトカテゴリを登録し、既存の未分類アプリを自動分類する
        // 管理対象アプリのアイコン・名前も先にキャッシュし、フォルダやホーム画面を即表示できるようにする
        appScope.launch(Dispatchers.IO) {
            store.ensureDefaultCategories(DefaultCategoryClassifier.DEFAULT_CATEGORIES) { pkg ->
                classifier.classify(pkg)
            }
            installed.loadManagedApps(store.current().managedApps.keys)
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

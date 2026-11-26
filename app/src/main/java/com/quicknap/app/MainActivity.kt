package com.quicknap.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quicknap.app.shizuku.ShizukuStatus
import com.quicknap.app.ui.AddAppFab
import com.quicknap.app.ui.HomeScreen
import com.quicknap.app.ui.MainViewModel
import com.quicknap.app.ui.PickScreen
import com.quicknap.app.ui.QuicknapSnackbar
import com.quicknap.app.ui.QuicknapSnackbarVisuals
import com.quicknap.app.ui.SetupScreen
import com.quicknap.app.ui.theme.LocalQuicknapPalette
import com.quicknap.app.ui.theme.QuicknapTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QuicknapTheme {
                val palette = LocalQuicknapPalette.current
                val state by viewModel.ui.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val lifecycleOwner = LocalLifecycleOwner.current
                var showPick by rememberSaveable { mutableStateOf(false) }
                // ホームの「外す」選択モード中は「アプリを選ぶ」ボタンを隠す
                var selecting by rememberSaveable { mutableStateOf(false) }

                // 外部（Shizuku・設定画面・起こしたアプリ）から戻ったときに状態を同期する
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // トーストは新しいものが来たら差し替え、「元に戻す」は6秒間だけ出す
                LaunchedEffect(Unit) {
                    var current: Job? = null
                    viewModel.messages.collect { message ->
                        current?.cancel()
                        current = launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = withTimeoutOrNull(if (message.undo != null) UNDO_MS else INFO_MS) {
                                snackbarHostState.showSnackbar(
                                    QuicknapSnackbarVisuals(
                                        message = message.text,
                                        actionLabel = if (message.undo != null) "元に戻す" else null,
                                        subject = message.subject,
                                    )
                                )
                            }
                            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
                        }
                    }
                }

                BackHandler(enabled = state.setupDone && showPick) { showPick = false }

                Scaffold(
                    containerColor = palette.bgScreen,
                    floatingActionButton = {
                        if (state.setupDone && !showPick && !selecting && state.hasManaged) {
                            AddAppFab(onClick = { showPick = true })
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            QuicknapSnackbar(data)
                        }
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when {
                            !state.setupDone && !state.loading -> SetupScreen(
                                state = state,
                                onShizukuAction = {
                                    if (state.shizuku == ShizukuStatus.PermissionRequired) {
                                        viewModel.requestShizukuPermission()
                                    } else {
                                        viewModel.openShizuku()
                                    }
                                },
                                onOpenShizukuDetails = viewModel::openShizukuDetails,
                                onOpenUsageAccess = viewModel::openUsageAccessSettings,
                                onRequestNotifications = {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onFinish = viewModel::finishSetup,
                            )

                            showPick -> PickScreen(
                                state = state,
                                onBack = { showPick = false },
                                onPicked = viewModel::setPicked,
                                onChangeDays = viewModel::changeAutoSleepDays,
                            )

                            else -> HomeScreen(
                                state = state,
                                onPick = { showPick = true },
                                onTapRow = viewModel::tapHomeRow,
                                onToggle = viewModel::toggleHomeRow,
                                selecting = selecting,
                                onSelectingChange = { selecting = it },
                                onRelease = viewModel::releaseHomeRows,
                                onOpenAppDetails = viewModel::openAppDetails,
                                onResumeShizuku = viewModel::openShizuku,
                                onRequestShizukuPermission = viewModel::requestShizukuPermission,
                                onSelectCategory = viewModel::selectCategory,
                                onChangeSortOrder = viewModel::setAsleepSortOrder,
                                onAssignCategory = viewModel::assignCategory,
                                onCreateCategory = viewModel::createCategory,
                                onRenameCategory = viewModel::renameCategory,
                                onDeleteCategory = viewModel::deleteCategory,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val UNDO_MS = 6_000L
        const val INFO_MS = 3_000L
    }
}

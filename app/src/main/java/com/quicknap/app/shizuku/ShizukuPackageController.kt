package com.quicknap.app.shizuku

import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

enum class ShizukuStatus {
    Unavailable,
    PermissionRequired,
    Ready,
}

/**
 * ShizukuBinderWrapper 経由で Android の package サービス（IPackageManager）を直接呼び出す。
 * UserService（別プロセス起動）を使わないため、Android 16 / HyperOS 3.0 でもタイムアウトせず高速に動作する。
 */
class ShizukuPackageController {

    private val _status = MutableStateFlow(computeStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _status.value = ShizukuStatus.Unavailable
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                refreshStatus()
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    _status.value = ShizukuStatus.PermissionRequired
                }
            }
        }

    fun register() {
        ensureHiddenApiBypass()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshStatus()
    }

    fun unregister() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun refreshStatus(): ShizukuStatus {
        val current = computeStatus()
        _status.value = current
        return current
    }

    /** プロセス起動直後は binder の到着が遅れるため、少し待ってから状態を確定する（定期実行用） */
    suspend fun awaitStatus(timeoutMs: Long): ShizukuStatus {
        val deadline = System.currentTimeMillis() + timeoutMs
        var current = refreshStatus()
        while (current == ShizukuStatus.Unavailable && System.currentTimeMillis() < deadline) {
            delay(200)
            current = refreshStatus()
        }
        return current
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            _status.value = ShizukuStatus.Unavailable
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _status.value = ShizukuStatus.Ready
            return
        }
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (refreshStatus() != ShizukuStatus.Ready) {
                    return@withContext Result.failure(
                        IllegalStateException("Shizuku が利用できない状態です")
                    )
                }

                try {
                    ensureHiddenApiBypass()

                    val rawBinder = SystemServiceHelper.getSystemService("package")
                        ?: return@withContext Result.failure(
                            IllegalStateException("package サービスの取得に失敗しました")
                        )
                    val wrappedBinder = ShizukuBinderWrapper(rawBinder)

                    val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
                    val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                    val iPackageManager = asInterface.invoke(null, wrappedBinder)
                        ?: return@withContext Result.failure(
                            IllegalStateException("IPackageManager の取得に失敗しました")
                        )

                    val targetMethod = findSetApplicationEnabledSetting(iPackageManager.javaClass)
                        ?: return@withContext Result.failure(
                            NoSuchMethodException("setApplicationEnabledSetting が見つかりません")
                        )

                    val newState = if (enabled) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    }

                    targetMethod.isAccessible = true
                    when (targetMethod.parameterTypes.size) {
                        5 -> targetMethod.invoke(
                            iPackageManager,
                            packageName,
                            newState,
                            0,
                            USER_ID_OWNER,
                            CALLING_PACKAGE_SHELL,
                        )
                        4 -> targetMethod.invoke(
                            iPackageManager,
                            packageName,
                            newState,
                            0,
                            USER_ID_OWNER,
                        )
                        else -> return@withContext Result.failure(
                            IllegalStateException("未対応の引数数: ${targetMethod.parameterTypes.size}")
                        )
                    }

                    Result.success(Unit)
                } catch (e: InvocationTargetException) {
                    val cause = e.cause ?: e
                    Result.failure(
                        IllegalStateException("${cause.javaClass.simpleName}: ${cause.message}", cause)
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private fun findSetApplicationEnabledSetting(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull { it.name == "setApplicationEnabledSetting" }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun computeStatus(): ShizukuStatus {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.Unavailable
            } else if (Shizuku.isPreV11()) {
                ShizukuStatus.Unavailable
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.Ready
            } else {
                ShizukuStatus.PermissionRequired
            }
        } catch (_: Throwable) {
            ShizukuStatus.Unavailable
        }
    }

    companion object {
        private const val REQUEST_CODE_SHIZUKU = 1001
        private const val USER_ID_OWNER = 0
        private const val CALLING_PACKAGE_SHELL = "com.android.shell"

        @Volatile
        private var hiddenApiBypassed = false

        private fun ensureHiddenApiBypass() {
            if (!hiddenApiBypassed) {
                runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
                hiddenApiBypassed = true
            }
        }
    }
}

package com.example.myapplication.agent.privilege

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * 特权执行门面：探测三种后端、按 ROOT > SHIZUKU > DIRECT 选定、转发命令、代办 Shizuku 授权。
 * 所有系统调用都收敛成状态，不让异常冒到 UI。
 */
class PrivilegeManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val directShell: PrivilegedShell = DirectShell(),
    private val rootShell: RootShell = RootShell(),
    private val shizukuShell: ShizukuShell = ShizukuShell(context),
) {
    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    @Volatile
    private var pendingRequestCode: Int = NO_REQUEST_CODE

    @Volatile
    private var listenersRegistered: Boolean = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == pendingRequestCode) {
            pendingRequestCode = NO_REQUEST_CODE
            refreshAsync()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshAsync() }

    private val binderDeadListener = Shizuku.OnBinderDeadListener { refreshAsync() }

    /** 启动时探测一次，并把 Shizuku binder 的到达/死亡接到状态刷新上。 */
    fun start() {
        registerShizukuListeners()
        refreshAsync()
    }

    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /** 重新探测三种后端；root 探测自带 2 秒超时，Shizuku 未安装时直接跳过。 */
    suspend fun refresh(): PrivilegeStatus {
        val rootAvailable = rootShell.isAvailable()
        val installed = isShizukuInstalled()
        val ready = installed && shizukuShell.isReady()
        val next = PrivilegeSelection
            .status(rootAvailable, installed, ready)
            .copy(lastProbeOutput = _status.value.lastProbeOutput)
        _status.value = next
        return next
    }

    /** 用当前后端跑一次 id，返回输出；失败时返回原因文本，不抛。 */
    suspend fun probe(): String {
        val backend = _status.value.activeBackend
        val result = try {
            shellFor(backend).run(PROBE_COMMAND)
        } catch (t: Throwable) {
            ShellResult.failure(t.describe())
        }
        val output = result.stdout.ifBlank { result.stderr }.ifBlank { "exit=${result.exitCode}" }
        _status.update { it.copy(lastProbeOutput = output) }
        return output
    }

    fun shellFor(backend: PrivilegeBackend): PrivilegedShell = when (backend) {
        PrivilegeBackend.ROOT -> rootShell
        PrivilegeBackend.SHIZUKU -> shizukuShell
        PrivilegeBackend.DIRECT -> directShell
    }

    fun activeShell(): PrivilegedShell = shellFor(_status.value.activeBackend)

    /**
     * Shizuku 授权时序：注册 OnRequestPermissionResultListener → requestPermission(code) →
     * 回调命中 requestCode → 重新探测并刷新状态。调用落在 activity 主线程上，
     * Shizuku 弹窗与监听回调都按主线程交付。
     */
    fun requestShizukuPermission(activity: Activity, requestCode: Int) {
        activity.runOnUiThread {
            try {
                registerShizukuListeners()
                pendingRequestCode = requestCode
                when {
                    !Shizuku.pingBinder() -> refreshAsync()
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> refreshAsync()
                    else -> Shizuku.requestPermission(requestCode)
                }
            } catch (t: Throwable) {
                pendingRequestCode = NO_REQUEST_CODE
                refreshAsync()
            }
        }
    }

    /** 解绑全部监听，避免 Manager 比界面活得久时泄漏。 */
    fun release() {
        try {
            if (listenersRegistered) {
                Shizuku.removeRequestPermissionResultListener(permissionListener)
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
            }
        } catch (_: Throwable) {
        } finally {
            listenersRegistered = false
        }
        shizukuShell.release()
    }

    private fun registerShizukuListeners() {
        if (listenersRegistered) return
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            listenersRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun isShizukuInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PrivilegeSelection.SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    private companion object {
        const val NO_REQUEST_CODE = -1
        const val PROBE_COMMAND = "id"
    }
}

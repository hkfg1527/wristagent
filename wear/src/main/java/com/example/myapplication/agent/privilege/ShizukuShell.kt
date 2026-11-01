package com.example.myapplication.agent.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Shizuku 后端：pingBinder → checkSelfPermission → bindUserService 到 [UserService]。
 * 任一步失败、绑定超时、远端异常都返回 [ShellResult.failure]，绝不抛。
 */
class ShizukuShell(
    private val context: Context,
    private val bindTimeoutMs: Long = BIND_TIMEOUT_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : PrivilegedShell {

    @Volatile
    private var cachedService: IUserService? = null

    @Volatile
    private var pendingConnection: ServiceConnection? = null

    @Volatile
    private var binderDeadListenerRegistered = false

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        cachedService = null
        pendingConnection = null
    }

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            if (!isReady()) {
                ShellResult.failure(unavailableReason())
            } else {
                val service = service()
                if (service == null) {
                    ShellResult.failure("shizuku user service is unavailable")
                } else {
                    UserServiceProtocol.parse(service.runCommand(command))
                }
            }
        } catch (t: Throwable) {
            ShellResult.failure(t.describe())
        }
    }

    /** binder 就绪且已授权才为 true；Shizuku 未运行或抛异常一律 false。 */
    fun isReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** 解绑并清缓存，供上层销毁时调用。 */
    fun release() {
        cachedService = null
        val connection = pendingConnection
        pendingConnection = null
        try {
            if (connection != null) Shizuku.unbindUserService(serviceArgs(), connection, true)
        } catch (_: Throwable) {
        }
        try {
            if (binderDeadListenerRegistered) Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
        binderDeadListenerRegistered = false
    }

    private fun unavailableReason(): String = try {
        if (!Shizuku.pingBinder()) "shizuku service is not running" else "shizuku permission is not granted"
    } catch (t: Throwable) {
        t.describe()
    }

    private suspend fun service(): IUserService? {
        cachedService?.let { return it }
        return withTimeoutOrNull(bindTimeoutMs) { bind() }
    }

    private suspend fun bind(): IUserService? = suspendCancellableCoroutine { cont ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = try {
                    IUserService.Stub.asInterface(binder)
                } catch (t: Throwable) {
                    null
                }
                cachedService = service
                if (cont.isActive) cont.resume(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                cachedService = null
            }

            override fun onBindingDied(name: ComponentName?) {
                cachedService = null
                if (cont.isActive) cont.resume(null)
            }

            override fun onNullBinding(name: ComponentName?) {
                cachedService = null
                if (cont.isActive) cont.resume(null)
            }
        }
        pendingConnection = connection
        registerBinderDeadListener()
        try {
            Shizuku.bindUserService(serviceArgs(), connection)
        } catch (t: Throwable) {
            pendingConnection = null
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun registerBinderDeadListener() {
        if (binderDeadListenerRegistered) return
        try {
            Shizuku.addBinderDeadListener(binderDeadListener)
            binderDeadListenerRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun serviceArgs(): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(context, UserService::class.java))
            .daemon(false)
            .processNameSuffix(PROCESS_NAME_SUFFIX)
            .debuggable(false)
            .version(SERVICE_VERSION)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val BIND_TIMEOUT_MS = 5_000L
        const val PROCESS_NAME_SUFFIX = "privilege"
        const val SERVICE_VERSION = 1
    }
}

package com.example.myapplication.agent.privilege

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Shizuku UserService：由 Shizuku 以 shell 身份拉起的特权进程，命令经 [IUserService] 进来。
 * 声明为不导出、无权限、无 intent-filter；只有 Shizuku 服务端会绑定它。
 */
class UserService : Service() {

    private val binder = object : IUserService.Stub() {

        override fun runCommand(command: String): String {
            val result = try {
                ProcessExec.exec(listOf("sh", "-c", command), COMMAND_TIMEOUT_MS)
            } catch (t: Throwable) {
                ShellResult.failure(t.describe())
            }
            return UserServiceProtocol.format(result.exitCode, result.stdout, result.stderr)
        }

        override fun destroy() {
            try {
                System.exit(0)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private companion object {
        const val COMMAND_TIMEOUT_MS = 15_000L
    }
}

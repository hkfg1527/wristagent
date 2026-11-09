package com.example.myapplication.agent.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 网页控制台的宿主服务。
 *
 * 背景：控制台原本只在 MainActivity.onCreate 启动、onDestroy 停止，没有前台服务保活。
 * 进程被系统回收后 8080 立即消失，手机侧表现为「加载很久然后失败」——
 * 实测 force-stop 后 adb forward 会在 17ms 内关连接（没有监听者），
 * Wi-Fi 直连则是 2.5 秒后连接失败，与用户看到的现象完全一致。
 */
class ConsoleService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(portText = "启动中"))
        server.start()
        val port = runCatching { server.port }.getOrDefault(-1)
        notify(port)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { server.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val server by lazy { ConsoleServer.create(applicationContext) }

    private fun notify(port: Int) {
        val text = if (port > 0) "端口 $port · 四位码见设置页" else "启动失败：端口被占用"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(portText: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "网页控制台",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持手机浏览器可以访问手表上的控制台" }
            manager?.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("网页控制台运行中")
            .setContentText(portText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "console"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            val intent = Intent(context, ConsoleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConsoleService::class.java))
        }
    }
}

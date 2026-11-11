package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.example.myapplication.R
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.agent.remote.ConsoleToken
import com.example.myapplication.agent.remote.ConsoleTokenStore
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网页控制台入口：把「服务在跑 / 端口几 / 完整地址（含令牌）」摊开给用户，
 * 点一下地址即复制 —— 手表上没法手打 URL，复制是最省事的路径。
 *
 * 令牌明文只在这里出现（服务端对外只给 tokenMasked）：这是本机 UI，
 * 设备已经在手上了，再遮一次只会让用户没法用。
 */
@Composable
fun RemoteConsoleContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 全进程只有一个 ConsoleServer（MainActivity 启动时 create + start）：这里只取回同一实例，
    // 不再 create 第二份监听套接字与线程池。
    val server = remember { ConsoleServer.peek() ?: ConsoleServer.create(context.applicationContext) }
    val tokenStore = remember { ConsoleTokenStore(context.applicationContext) }
    // 端口/运行态不是 Compose 状态，进入本页时读一次快照；点按钮后再刷新一次。
    var refreshTick by remember { mutableIntStateOf(0) }
    val token = remember(refreshTick) { tokenStore.getOrCreate() }
    // 端口读的是真正绑上的那个（8080 被占用时会是 8081 / 8082），不是配置里的首选端口
    val port = remember(refreshTick) { server.port }
    val preferredPort = remember(refreshTick) { server.preferredPort }
    val portStatus = ConsolePortStatus.of(preferredPort, port)
    val running = portStatus != ConsolePortStatus.NOT_RUNNING
    val ip = remember(refreshTick) { localIpv4() ?: "0.0.0.0" }
    // 令牌走查询参数 t：控制台前端在 initToken() 里用 URLSearchParams 取 t 后立刻从地址栏抹掉
    val url = "http://$ip:$port/?t=$token"

    SettingsSubPage(title = stringResource(R.string.settings_remote_console_title), onBack = onBack) {
        section(stringResource(R.string.settings_remote_console_title))

        row {
            SettingsStatusRow(
                title = stringResource(R.string.settings_remote_console_title),
                value = when (portStatus) {
                    ConsolePortStatus.NOT_RUNNING ->
                        stringResource(R.string.settings_remote_console_not_running)
                    ConsolePortStatus.STABLE ->
                        stringResource(R.string.settings_remote_console_running, port)
                    ConsolePortStatus.DRIFTED ->
                        stringResource(
                            R.string.settings_remote_console_port_drifted_short,
                            preferredPort,
                            port
                        )
                },
                // 漂移只染右端 6dp 状态点，整行与取值文字都不铺色
                statusTone = if (portStatus == ConsolePortStatus.DRIFTED) {
                    SettingsStatusTone.WARN
                } else {
                    null
                }
            )
        }

        if (portStatus == ConsolePortStatus.DRIFTED) {
            row {
                SettingsCaptionRow(
                    text = stringResource(
                        R.string.settings_remote_console_port_drifted,
                        preferredPort,
                        port
                    )
                )
            }
        }

        if (running) {
            row {
                SettingsCaptionRow(
                    text = url,
                    mono = true,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(url))
                        context.vibrateTick()
                    }
                )
            }
            row {
                SettingsCaptionRow(text = stringResource(R.string.settings_remote_console_copy_hint))
            }
        }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_remote_console_token),
                value = ConsoleToken.mask(token)
            )
        }

        row {
            SettingsCaptionRow(text = stringResource(R.string.settings_remote_console_hint))
        }

        standaloneRow {
            SettingsActionButton(
                label = stringResource(
                    if (running) {
                        R.string.settings_remote_console_refresh_port
                    } else {
                        R.string.settings_remote_console_start
                    }
                ),
                onClick = {
                    context.vibrateTick()
                    // start() 内部幂等；这里读实时状态而不是渲染快照，连点也不会二次绑定端口
                    if (!server.isRunning) server.start()
                    refreshTick++
                }
            )
        }
    }
}

/** 取本机第一个非回环 IPv4：不依赖 WifiManager（也就免掉额外权限）。 */
private fun localIpv4(): String? = try {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
} catch (_: Throwable) {
    null
}

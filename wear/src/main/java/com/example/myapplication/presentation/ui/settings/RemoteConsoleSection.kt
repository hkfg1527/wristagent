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
    val server = remember { ConsoleServer.create(context.applicationContext) }
    val token = remember { ConsoleTokenStore(context.applicationContext).getOrCreate() }
    // 端口/运行态不是 Compose 状态，进入本页时读一次快照；点按钮后再刷新一次。
    var refreshTick by remember { mutableIntStateOf(0) }
    val port = remember(refreshTick) { server.port }
    val running = remember(refreshTick) { server.isRunning }
    val ip = remember(refreshTick) { localIpv4() ?: "0.0.0.0" }
    val url = "http://$ip:$port/?t=$token"

    SettingsSubPage(title = stringResource(R.string.settings_remote_console_title), onBack = onBack) {
        section(stringResource(R.string.settings_remote_console_title))

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_remote_console_title),
                value = if (running) {
                    stringResource(R.string.settings_remote_console_running, port)
                } else {
                    stringResource(R.string.settings_remote_console_stopped)
                }
            )
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

        row {
            SettingsActionButton(
                label = if (running) "重新读取端口" else "启动控制台",
                onClick = {
                    context.vibrateTick()
                    if (!running) server.start()
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

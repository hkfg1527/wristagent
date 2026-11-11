package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ProviderProfileBook

/**
 * 供应商 Profile 子页：列表 + 切换 + 复制当前配置 + 改名 + 删除。
 *
 * 语义边界：Profile 只是"一键套用"的快捷方式 —— 切换时把 baseUrl / apiKey / modelName
 * 写回既有的三个单值偏好，所有既有读取路径（模型轮换、请求体、控制台字段）完全不变，
 * 因此这里不新增任何"当前供应商"的运行期概念，只在列表里标出 selected。
 */
@Composable
fun ProviderProfilesContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    var book by remember { mutableStateOf(preferences.providerProfiles()) }
    var renaming by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    val active = book.active()

    fun refresh() {
        book = preferences.providerProfiles()
        onApplied()
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_provider_profiles),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_providers_saved))

        if (book.profiles.isEmpty()) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_providers_empty)) }
        } else {
            book.profiles.forEach { profile ->
                // 掩码在组合期取一次：空密钥时整段不出现，不留下一个只有空格的尾部槽位
                val maskedKey = SettingsValueMath
                    .maskSecret(profile.apiKey)
                    .takeIf { it.isNotEmpty() }
                row {
                    SettingsRadioRow(
                        label = profile.name,
                        // 副标题只露模型名与掩码密钥：圆屏上不整串铺开 apiKey。
                        // 单行 + 尾部省略号 —— deepseek-reasoner 不许从词中间折成
                        // "deepseek-reaso" + "ner"；掩码不参与截断，模型名超长时先截模型名。
                        subtitle = profile.modelName,
                        subtitleTail = maskedKey,
                        subtitleMaxLines = 1,
                        selected = profile.id == book.activeProfileId,
                        onSelect = {
                            context.vibrateTick()
                            preferences.applyProviderProfile(profile.id)
                            refresh()
                        }
                    )
                }
            }
        }

        section(stringResource(R.string.settings_providers_actions))

        row {
            // 自动名称在组合期取好：onClick 不是 @Composable，不能在里面调 stringResource
            val nextDefaultName = stringResource(
                R.string.settings_providers_default_name,
                book.profiles.size + 1
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_providers_copy),
                value = stringResource(
                    R.string.settings_providers_capacity,
                    book.profiles.size,
                    ProviderProfileBook.MaxProfiles
                ),
                onClick = {
                    context.vibrateTick()
                    // 自动名称走资源：en/zh 两套界面都不会突然冒出一个中文默认名
                    preferences.createProviderProfileFromCurrent(name = nextDefaultName)
                    refresh()
                }
            )
        }

        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_providers_rename),
                value = active?.name ?: stringResource(R.string.settings_providers_none_active),
                onClick = {
                    val target = active
                    if (target != null) {
                        context.vibrateTick()
                        renameDraft = target.name
                        renaming = true
                    }
                }
            )
        }

        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_providers_delete),
                onClick = {
                    val target = book.activeProfileId
                    if (target != null) {
                        context.vibrateConfirm()
                        preferences.deleteProviderProfile(target)
                        refresh()
                    }
                }
            )
        }
    }

    if (renaming) {
        // 输入沿用设置页既有文本框对话框：逐字符回写到 draft，确认/取消都在 onDismiss 落库
        SettingsTextDialog(
            title = stringResource(R.string.settings_providers_rename_title),
            initial = renameDraft,
            placeholder = stringResource(R.string.settings_providers_rename_placeholder),
            onValueChange = { renameDraft = it },
            onDismiss = {
                renaming = false
                val target = book.activeProfileId
                if (target != null && renameDraft.isNotBlank()) {
                    preferences.renameProviderProfile(target, renameDraft)
                    refresh()
                }
            }
        )
    }
}

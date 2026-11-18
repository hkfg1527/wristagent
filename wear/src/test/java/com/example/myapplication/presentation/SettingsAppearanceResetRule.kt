package com.example.myapplication.presentation

import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.WearAppearance
import org.junit.rules.ExternalResource

/**
 * 外观单例的用例隔离。
 *
 * `WearAppearance` 是 Kotlin `object`（全进程一份），而 Robolectric 在同一个 sandbox 里
 * 跨测试类复用它 —— 一个用例把风格切到 `M3_LIST`，下一个用例渲染出来的就是官方件版本：
 * 行外壳、开关、滑杆全换了一套。断言的失败表现往往是"找不到节点"，看起来像控件坏了，
 * 实际是环境被上一个用例改过。0.0.0.90 的测试有效性审查点名了这条。
 *
 * 用法：在测试类里加一行 ——
 * ```
 * @get:Rule(order = 0) val appearance = SettingsAppearanceResetRule()
 * ```
 * 新写的、会渲染设置页的 Robolectric 用例都应该带上它。
 */
class SettingsAppearanceResetRule : ExternalResource() {

    override fun before() {
        reset()
    }

    override fun after() {
        reset()
    }

    private fun reset() {
        WearAppearance.style = SettingsStyle.Default
        WearAppearance.fontScale = 1f
        WearAppearance.accentOverride = null
    }
}

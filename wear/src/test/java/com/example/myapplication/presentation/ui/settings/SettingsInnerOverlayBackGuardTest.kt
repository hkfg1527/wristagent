package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内层浮层必须能吃掉系统返回键。
 *
 * 真机 bug（2026-11-17 验收矩阵补测发现）：`网络与请求 → 网络体检` 按返回直接跳回**设置根页**，
 * 父页被跳过；而另一个内层页 `系统提示词` 是正常回父页的 —— 同一层级的两种行为。
 *
 * 根因：`网络体检` 是 `SettingsSubPage` 的 `overlay` + 页内 `inner` 状态，
 * 但系统返回键走的是外层 `SettingsScreen` 的 `BackHandler`（`activeDialog = null`），
 * 没人先关 overlay。修法：`SettingsSubPage` 新增 `overlayBack`，浮层打开时它注册
 * 一个更内层的 `BackHandler`，先关浮层。
 */
class SettingsInnerOverlayBackGuardTest {

    private val shared = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
    )
    private val network = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/settings/NetworkRequestSection.kt"
    )

    @Test
    fun subPageExposesAnOverlayBackHookThatRegistersABackHandler() {
        assertTrue(
            "SettingsSubPage 必须暴露 overlayBack 参数",
            shared.contains("overlayBack: (() -> Unit)? = null")
        )
        assertTrue(
            "overlayBack 非空时必须注册 BackHandler",
            shared.contains("if (overlayBack != null) {") && shared.contains("BackHandler { overlayBack.invoke() }")
        )
    }

    @Test
    fun networkDiagnosticsOverlayTakesTheBackKeyBeforeThePage() {
        assertTrue(
            "网络体检浮层必须把 overlayBack 传进来（否则返回会跳过父页）",
            network.contains("overlayBack = if (inner)")
        )
        // 传的条件必须与 overlay 的显示条件一致，否则"浮层开着但返回不关它"
        assertTrue(
            "overlayBack 的启用条件必须与 overlay 相同（都是 inner）",
            network.contains("if (inner)") && network.contains("overlay = {") && network.contains("if (inner) {")
        )
    }
}

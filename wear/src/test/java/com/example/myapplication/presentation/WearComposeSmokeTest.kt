package com.example.myapplication.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose 的落地探针：证明「JVM 上能真的渲染并查到节点」。
 *
 * 为什么值得有：手表只有一块，USB 一掉线所有 UI 验证就停摆（2026-11-18 22:05 就是这么停的）。
 * 这条测试跑通之后，"控件点下去变没变""文案在不在"这类判据不必再等真机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearComposeSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersChineseTextInsideWearTheme() {
        compose.setContent {
            MaterialTheme {
                Text("设置")
            }
        }
        compose.onNodeWithText("设置").assertExists()
    }
}

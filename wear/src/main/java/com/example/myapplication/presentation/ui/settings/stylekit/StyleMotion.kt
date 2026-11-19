package com.example.myapplication.presentation.ui.settings.stylekit

import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.WearAppearance

/**
 * 一套风格的动效参数（入场 / 退场 / 磁滑 / 边缘缩放）。
 *
 * 为什么单独立表：这四类参数以前散在渲染代码里（有的写死 4dp、有的恒等于 1 变成空操作），
 * 同一套风格在不同页面上表现不一致。现在按风格给值，渲染侧只读这里 ——
 * 和 `SettingsStylePolicy`（结构槽位）分工一致：那边管"长什么样"，这里管"怎么动"。
 *
 * 单位：位移 dp、时长 ms、错峰 ms、缩放/透明度为倍率。
 */
data class StyleMotion(
    /** 入场纵向下移量（正数=从下方浮上来）。 */
    val enterSlideDp: Float,
    /** 单行入场时长。 */
    val enterDurationMs: Int,
    /** 相邻两行入场的时间差。 */
    val staggerMs: Int,
    /** 退场纵向位移量（正数=向下沉出）。 */
    val exitSlideDp: Float,
    /** 单行退场时长。 */
    val exitDurationMs: Int,
    /** 松手后是否吸附到最近一行（表冠与手势共用）。 */
    val snapToRows: Boolean,
    /** 贴边时卡片最小缩放（1f = 不缩放）。 */
    val edgeScaleMin: Float,
    /** 贴边时最小透明度。 */
    val edgeAlphaMin: Float,
    /** 入场起始缩放（1f = 不缩放）。 */
    val enterScaleFrom: Float = 1f,
    /**
     * 磁滑吸附节距（dp）：松手后按这个间距吸附到最近一行。
     *
     * 取"行高 + 行间隙"的实际节距，吸附才会正好停在一行上；各套风格行高不同，所以逐套给值。
     * [snapToRows] 为 false 时忽略。
     */
    val snapPitchDp: Float = 64f
) {
    init {
        require(enterDurationMs in 60..600) { "入场时长越界：$enterDurationMs" }
        require(exitDurationMs in 60..600) { "退场时长越界：$exitDurationMs" }
        require(staggerMs in 0..120) { "错峰越界：$staggerMs" }
        require(edgeScaleMin in 0.75f..1f) { "边缘缩放越界：$edgeScaleMin" }
        require(edgeAlphaMin in 0.3f..1f) { "边缘透明度越界：$edgeAlphaMin" }
        require(enterScaleFrom in 0.75f..1f) { "入场缩放越界：$enterScaleFrom" }
        require(snapPitchDp in 36f..120f) { "吸附节距越界：$snapPitchDp" }
    }
}

object StyleMotions {

    /**
     * 每套风格一行；缺一套编译期就过不了。
     *
     * 设计依据见 `docs/UI-REBUILD-PLAN.md` §2：三套自绘档要"有性格"（气泡浮、方角硬、终端不吸附），
     * 三套官方档克制（官方默认节奏，不额外加位移）。
     */
    fun of(style: SettingsStyle): StyleMotion = when (style) {
        // 气泡：从下方轻轻浮上来，慢一点、错峰大一点，贴合"软"的气质
        SettingsStyle.BALLOON -> StyleMotion(
            enterSlideDp = 8f,
            enterDurationMs = 260,
            staggerMs = 40,
            exitSlideDp = 16f,
            exitDurationMs = 200,
            snapToRows = true,
            edgeScaleMin = 0.94f,
            edgeAlphaMin = 0.5f,
            snapPitchDp = 72f
        )
        // 方角：短促、干脆，位移小、错峰小
        SettingsStyle.SQUARE -> StyleMotion(
            enterSlideDp = 6f,
            enterDurationMs = 160,
            staggerMs = 24,
            exitSlideDp = 10f,
            exitDurationMs = 140,
            snapToRows = true,
            edgeScaleMin = 0.96f,
            edgeAlphaMin = 0.5f
        )
        // 大卡片：缩放浮入（卡片自己的语言），贴边缩得最明显
        SettingsStyle.GOOGLE_CARD -> StyleMotion(
            enterSlideDp = 0f,
            enterDurationMs = 240,
            staggerMs = 50,
            exitSlideDp = 8f,
            exitDurationMs = 180,
            snapToRows = true,
            edgeScaleMin = 0.90f,
            edgeAlphaMin = 0.4f,
            enterScaleFrom = 0.92f,
            snapPitchDp = 80f
        )
        // 终端：命令行语义 —— 连续滚动**不吸附**，只有淡出，没有位移与缩放
        SettingsStyle.TERMINAL -> StyleMotion(
            enterSlideDp = 0f,
            enterDurationMs = 120,
            staggerMs = 16,
            exitSlideDp = 0f,
            exitDurationMs = 100,
            snapToRows = false,
            edgeScaleMin = 1f,
            edgeAlphaMin = 0.5f,
            snapPitchDp = 44f
        )
        // 官方列表：不给额外位移，保持库的默认节奏
        SettingsStyle.M3_LIST -> StyleMotion(
            enterSlideDp = 0f,
            enterDurationMs = 200,
            staggerMs = 0,
            exitSlideDp = 0f,
            exitDurationMs = 160,
            snapToRows = true,
            edgeScaleMin = 0.95f,
            edgeAlphaMin = 0.5f,
            snapPitchDp = 72f
        )
        // 官方转场：缩放交给 SurfaceTransformation，这里只声明吸附与贴边
        SettingsStyle.M3_TRANSFORM -> StyleMotion(
            enterSlideDp = 0f,
            enterDurationMs = 220,
            staggerMs = 0,
            exitSlideDp = 0f,
            exitDurationMs = 180,
            snapToRows = true,
            edgeScaleMin = 0.90f,
            edgeAlphaMin = 0.4f,
            snapPitchDp = 72f
        )
        // 霓虹：快、亮、有回弹感（缩放浮入），贴边缩得明显
        SettingsStyle.NEON -> StyleMotion(
            enterSlideDp = 0f,
            enterDurationMs = 180,
            staggerMs = 30,
            exitSlideDp = 6f,
            exitDurationMs = 140,
            snapToRows = true,
            edgeScaleMin = 0.88f,
            edgeAlphaMin = 0.42f,
            enterScaleFrom = 0.94f,
            snapPitchDp = 72f
        )
        // 手帐：缓慢、几乎不位移，像翻纸
        SettingsStyle.PAPER -> StyleMotion(
            enterSlideDp = 3f,
            enterDurationMs = 320,
            staggerMs = 55,
            exitSlideDp = 8f,
            exitDurationMs = 240,
            snapToRows = true,
            edgeScaleMin = 0.98f,
            edgeAlphaMin = 0.6f
        )
        // 磨砂玻璃：慢速淡入 + 轻微上浮，通透
        SettingsStyle.GLASS -> StyleMotion(
            enterSlideDp = 6f,
            enterDurationMs = 300,
            staggerMs = 45,
            exitSlideDp = 10f,
            exitDurationMs = 220,
            snapToRows = true,
            edgeScaleMin = 0.92f,
            edgeAlphaMin = 0.45f,
            snapPitchDp = 72f
        )
        // 弧列：沿弧线扫入（位移最大），贴边只淡出不缩放（弧线本身已在动）
        SettingsStyle.ARC -> StyleMotion(
            enterSlideDp = 12f,
            enterDurationMs = 280,
            staggerMs = 50,
            exitSlideDp = 12f,
            exitDurationMs = 200,
            snapToRows = true,
            edgeScaleMin = 1f,
            edgeAlphaMin = 0.35f,
            snapPitchDp = 68f
        )
    }

    /** 当前风格生效的动效参数。渲染侧只读它。 */
    val current: StyleMotion get() = of(WearAppearance.style)
}

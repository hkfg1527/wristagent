package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.abs

/**
 * 字号三档：**整体缩放**既有 5 级字阶（12 / 15 / 18 / 24 / 30），不新增档位、不改字阶本身。
 *
 * 缩放发生在取样式的那一步（见 [com.example.myapplication.presentation.ui.theme.Wear] 的
 * Text* 访问器），所以 WearTypography 里仍然只有那 5 个 sp 数值，
 * WearTypographyAuthorityTest 与 SettingsDesignMathTest 的字阶断言都不受影响。
 */
enum class FontScaleTier(val scale: Float, val label: String, val percent: String) {
    STANDARD(1f, "标准", "100%"),
    LARGE(1.15f, "大", "115%"),
    XLARGE(1.30f, "特大", "130%");

    companion object {
        /** 存储里的浮点值 -> 最近的一档：跨版本写坏了也不会停在两档之间。 */
        fun nearest(scale: Float): FontScaleTier =
            values().minByOrNull { abs(it.scale - scale) } ?: STANDARD
    }
}

/**
 * 强调色预设：**运行时覆盖**，不是新颜色令牌。
 *
 * PaletteAuditTest 只数 WearTokens.kt 里 `object Palette` 的 `Color(0x…)` 常量，
 * 这里的取值是"用户可选的主题色"，默认一档与令牌 [Wear.Palette.Accent] 同值，
 * 因此没改过设置时像素与旧版逐位一致。每一档都过黑底 WCAG AA（见 AppearanceSettingsTest）。
 */
enum class AccentPreset(val argb: Long, val label: String) {
    CYAN(0xFF4FC3F7, "青"),
    MINT(0xFF7BE495, "绿"),
    VIOLET(0xFFC79BFF, "紫"),
    ROSE(0xFFFF9EB5, "粉"),
    AMBER(0xFFFFAB70, "橙");

    val color: Color get() = Color(argb.toInt())

    companion object {
        /** 默认档与令牌同值：`Wear.Palette.Accent` 仍是 `#4FC3F7`。 */
        val Default = CYAN

        /** 当前强调色 -> 预设档；不是预设值（将来换了令牌）时落到默认档。 */
        fun of(color: Color): AccentPreset =
            values().firstOrNull { it.argb.toInt() == color.toArgb() } ?: Default
    }
}

/**
 * 运行时的外观覆盖：全站唯一真值，界面只读它。
 *
 * 为什么状态在这里而不是 theme：theme 侧只声明"可覆盖点"（CompositionLocal）与兜底读取，
 * 真正的值与持久化留在设置域 —— agent 包的 AgentPreferences 不在本次改动边界内。
 * 两个字段都是 Compose 快照状态：读过它的组合会在改动后重跑，字号与强调色因此真的生效，
 * 而不是只写进存储里等人重启。
 */
object WearAppearance {

    /** 字号缩放系数：1 = 标准档。 */
    var fontScale: Float by mutableFloatStateOf(FontScaleTier.STANDARD.scale)

    /** 强调色覆盖：null = 用令牌默认值 [Wear.Palette.Accent]。 */
    var accentOverride: Color? by mutableStateOf(null)

    /** 当前强调色：覆盖值优先，默认回到令牌。 */
    val accent: Color get() = accentOverride ?: Wear.Palette.Accent

    /** 把字阶里的一档按当前缩放放大；[base] 是该档的 sp 数值。 */
    fun scaleSp(base: Float): Float = base * fontScale

    /** 恢复默认：内存与界面一起回到标准档 + 令牌强调色。 */
    internal fun reset() {
        fontScale = FontScaleTier.STANDARD.scale
        accentOverride = null
    }
}

/**
 * 外观的落盘：一份独立的极简偏好文件。
 *
 * 不并进 AgentPreferences 的原因只有一个 —— agent 包不在本次改动边界内。
 * 所有读写都经 [WearAppearance]，界面不持有第二份真值。
 */
class AppearanceStore internal constructor(private val prefs: SharedPreferences) {

    fun fontScale(): Float =
        FontScaleTier.nearest(prefs.getFloat(KEY_FONT_SCALE, FontScaleTier.STANDARD.scale)).scale

    fun accent(): Color? =
        prefs.getInt(KEY_ACCENT, NoAccent).takeIf { it != NoAccent }?.let { Color(it) }

    fun saveFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    fun saveAccent(color: Color) {
        prefs.edit().putInt(KEY_ACCENT, color.toArgb()).apply()
    }

    /** 把存储值灌进全局状态；界面随后只跟 [WearAppearance] 打交道。 */
    fun install(): AppearanceStore {
        WearAppearance.fontScale = fontScale()
        WearAppearance.accentOverride = accent()
        return this
    }

    companion object {
        const val FileName = "wear_appearance"

        internal const val KEY_FONT_SCALE = "font_scale"
        internal const val KEY_ACCENT = "accent_argb"

        /** 没有强调色覆盖时的哨兵：`getInt` 没有 null 可用。 */
        private const val NoAccent = Int.MIN_VALUE

        fun of(context: Context): AppearanceStore = AppearanceStore(
            context.applicationContext.getSharedPreferences(FileName, Context.MODE_PRIVATE)
        )
    }
}

/**
 * 进程内只灌一次：第一个读到 [WearAppearance] 的组合负责把存储里的值取回来。
 *
 * 没有 Application / 启动初始化入口可挂（MainActivity、chat 包、资源目录都在本次边界之外），
 * 所以挂在读路径上；[installed] 闸住之后，后续读取是纯内存，不碰磁盘。
 */
internal object AppearanceBootstrap {

    @Volatile
    private var installed = false

    fun ensure(context: Context) {
        if (installed) return
        installed = true
        AppearanceStore.of(context).install()
    }

    /** 测试用：让下一次 [ensure] 重新灌一遍。 */
    internal fun reset() {
        installed = false
    }
}

/** 字号档写入：内存与存储一起改，整站字号立刻跟着变。 */
fun applyFontScaleTier(context: Context, tier: FontScaleTier) {
    WearAppearance.fontScale = tier.scale
    AppearanceStore.of(context).saveFontScale(tier.scale)
}

/** 强调色写入：同上。 */
fun applyAccentPreset(context: Context, preset: AccentPreset) {
    WearAppearance.accentOverride = preset.color
    AppearanceStore.of(context).saveAccent(preset.color)
}

/**
 * 外观页的两条分组标题与"自定义"后缀。
 *
 * 文案没能进资源文件：资源目录在本次改动的禁止清单里，这里只能先写字面量
 * （与 OnboardingScreen 的既有做法一致），待补 strings_settings.xml 时一并迁移。
 */
internal const val FontScaleSectionLabel = "字号"
internal const val AccentSectionLabel = "强调色"

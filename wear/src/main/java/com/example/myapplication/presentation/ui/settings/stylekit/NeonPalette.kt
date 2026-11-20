package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 霓虹档的配色与发光几何（`docs/UI-REBUILD-PLAN.md` §2 NEON）。
 *
 * 为什么单独一份：P7 的实测发现这套风格"只有深灰卡片 + 白字"，最饱和色饱和度仅 16 ——
 * 也就是说招牌特征（发光描边 + 霓虹色）根本没画出来。这份文件就是它的唯一真源。
 */
object NeonPalette {

    /** 主描边：青。选它的原因是它在近黑底上亮度最高、最像霓虹灯管。 */
    val Stroke = Color(0xFF00E5FF)

    /** 次色：品红。用于选中/强调，与青色形成对比。 */
    val Accent = Color(0xFFFF2D95)

    /** 发光层颜色（用主描边色，靠多层低透明度堆出光晕）。 */
    val Glow = Stroke

    /** 底板：比纯黑略亮一点点，否则发光无处附着。 */
    val Background = Color(0xFF05070A)

    /** 描边宽度。 */
    val StrokeWidth: Dp = 1.5.dp

    /**
     * 外发光的层叠：由内向外，宽度递增、透明度递减。
     *
     * 纯函数，便于单测钉住"越往外越淡、总层数固定"这两条性质。
     */
    fun glowLayers(count: Int = 4, step: Dp = 2.dp, baseAlpha: Float = 0.30f, falloff: Float = 0.55f): List<Pair<Dp, Float>> {
        val safeCount = count.coerceIn(1, 8)
        return (1..safeCount).map { level ->
            (step * level) to (baseAlpha * Math.pow(falloff.toDouble(), (level - 1).toDouble()).toFloat())
        }
    }
}

/**
 * 给内容加一圈霓虹描边 + 外发光。
 *
 * 做法：`drawBehind` 里先画多层半透明圆角矩形（发光），再画一圈实心描边；
 * 内容本身仍走各自的行渲染，所以不影响既有的行高/宽度约束。
 */
@Composable
fun Modifier.neonFrame(radius: Dp = 26.dp): Modifier {
    val layers = NeonPalette.glowLayers()
    return this
        .clip(RoundedCornerShape(radius))
        .background(NeonPalette.Background)
        .drawBehind {
            val r = radius.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            for ((width, alpha) in layers) {
                val w = width.toPx()
                drawRoundRect(
                    color = NeonPalette.Glow.copy(alpha = alpha),
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = w)
                )
            }
            drawRoundRect(
                color = NeonPalette.Stroke,
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = NeonPalette.StrokeWidth.toPx())
            )
        }
}

/** 供 [Box] 使用的便捷包装（内容照旧）。 */
@Composable
fun NeonFrameBox(radius: Dp = 26.dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.neonFrame(radius)) { content() }
}

/**
 * 画在**卡片外侧**的霓虹发光 + 画在**卡片之上**的描边。
 *
 * 为什么不复用 [neonFrame]：那个把光画在自己背景之下，卡片表面会盖住它（0.0.0.151 实测只剩边缘漏光）。
 * 这里改成：发光用负偏移画到边界之外，描边在内容之后叠一层 —— 两者都不会被卡片遮住。
 */
fun Modifier.neonGlowCard(radius: Dp = 26.dp, spread: Dp = 6.dp): Modifier {
    val layers = NeonPalette.glowLayers(count = 5, step = 2.dp)
    return this
        .drawBehind {
            val r = radius.toPx()
            for ((width, alpha) in layers) {
                val w = width.toPx()
                drawRoundRect(
                    color = NeonPalette.Glow.copy(alpha = alpha),
                    topLeft = Offset(-w / 2f, -w / 2f),
                    size = Size(size.width + w, size.height + w),
                    cornerRadius = CornerRadius(r + w / 2f, r + w / 2f),
                    style = Stroke(width = w)
                )
            }
            // 再补一圈稍宽的柔光，让光晕不那么硬
            val halo = spread.toPx()
            drawRoundRect(
                color = NeonPalette.Glow.copy(alpha = 0.10f),
                topLeft = Offset(-halo / 2f, -halo / 2f),
                size = Size(size.width + halo, size.height + halo),
                cornerRadius = CornerRadius(r + halo / 2f, r + halo / 2f),
                style = Stroke(width = halo)
            )
        }
        .drawWithContent {
            drawContent()
            drawRoundRect(
                color = NeonPalette.Stroke,
                cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                style = Stroke(width = NeonPalette.StrokeWidth.toPx())
            )
        }
}
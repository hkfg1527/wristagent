package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.sqrt

/**
 * 圆屏卡片的弦长几何：卡片宽度随所在屏幕高度变化，中间宽、上下窄。
 *
 * 内容基准仍是 [SettingsDesignMath.ContentWidthDp]（176dp）：屏幕中心最多扩到 216dp，
 * 向上下边缘按圆的弦长连续收缩，不再设 176dp 下限。外壳与内容共享同一个弦宽，
 * 因此卡片填满两侧的同时，文字、取值和控件也一起膨胀/收缩。
 */
object SettingsCurveMath {
    /** 卡片外壳的最大宽度：233dp 屏上比内容基准宽 40dp。 */
    const val MaxCardWidthDp = 216f

    /** 内容基准宽度：内容横向比例以它为 1.0，窄屏上外壳也退回这个宽度。 */
    const val MinCardWidthDp = SettingsDesignMath.ContentWidthDp

    /** 卡片离屏幕圆周的安全余量。 */
    const val ScreenEdgeMarginDp = 4f

    /** 卡片宽度：屏越窄越早退回内容盒宽度。 */
    fun cardWidthDp(screenWidthDp: Float): Float =
        (screenWidthDp - 16f).coerceIn(MinCardWidthDp, MaxCardWidthDp)

    /** 列表内容盒两侧到屏边的内缩：233dp 屏为 12.5dp，192dp 屏为 8dp。 */
    fun screenInsetDp(screenWidthDp: Float): Float =
        ((screenWidthDp - cardWidthDp(screenWidthDp)) / 2f).coerceAtLeast(0f)

    /**
     * 距屏幕中心 [yFromCenterDp] 处的卡片半宽。
     *
     * 圆半径取屏高一半减去 [ScreenEdgeMarginDp]；半宽只受 [maxHalfDp] 限制，
     * 一直连续收缩到圆周，不会在内容宽度处提前夹平。
     */
    fun halfWidthDp(
        yFromCenterDp: Float,
        screenHeightDp: Float,
        maxHalfDp: Float
    ): Float {
        val radius = (screenHeightDp / 2f - ScreenEdgeMarginDp).coerceAtLeast(0f)
        val chord = sqrt((radius * radius - yFromCenterDp * yFromCenterDp).coerceAtLeast(0f))
        return chord.coerceIn(0f, maxHalfDp)
    }

    /**
     * 内容层的横向膨胀系数：屏心约 1.23，越靠近上下边缘越继续收缩，不再停在 1.0。
     *
     * 卡片外壳与内容共享同一个弦宽，因此文字、右槽取值和圆点会一起膨胀/收缩，
     * 而不是只有背景变宽、内容仍固定在 176dp。
     */
    fun contentScaleX(
        topInRootPx: Float,
        rowHeightPx: Float,
        screenHeightPx: Float,
        minHalfPx: Float,
        maxHalfPx: Float
    ): Float {
        val contentCenterY = topInRootPx + rowHeightPx / 2f
        val centerY = screenHeightPx / 2f
        val radius = (screenHeightPx / 2f - ScreenEdgeMarginDp).coerceAtLeast(0f)
        val dy = contentCenterY - centerY
        val chord = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        val half = chord.coerceIn(0f, maxHalfPx)
        return (half / minHalfPx).coerceIn(0f, maxHalfPx / minHalfPx)
    }
}

/**
 * 把一行卡片的背景构造成圆弦曲面。
 *
 * 左右边取圆周上 1/3、2/3 处的点作为三次贝塞尔控制点；相邻行共享同一个边界 y，
 * 因此上一行的底边半宽与下一行的顶边半宽完全相等，切线也连续，整组拼成一条圆弧曲面。
 *
 * 组首 / 组尾 / 单行用 14dp 椭圆穹顶封口；中间行只保留连续侧弧。
 */
object SettingsCurveGeometry {
    /** 封口浅弧高度：12dp，单段三次曲线与侧边共用切线，不做第二个圆拱。 */
    const val CapHeightDp = 12f

    /** 整页主气球：沿屏幕圆周采样成一条闭合贝塞尔曲线，顶底只留 6dp 浅弧。 */
    fun groupPath(
        size: Size,
        topInRootPx: Float,
        screenHeightPx: Float,
        screenMarginPx: Float,
        capHeightPx: Float
    ): Path {
        if (size.height <= 0f || size.width <= 0f) return Path()
        val centerY = screenHeightPx / 2f
        val radius = (screenHeightPx / 2f - screenMarginPx).coerceAtLeast(0f)
        val cx = size.width / 2f
        val cap = capHeightPx.coerceIn(0f, size.height / 2f)
        val topY = cap
        val bottomY = size.height - cap
        fun halfAt(localY: Float): Float {
            val dy = topInRootPx + localY - centerY
            val chord = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
            return chord.coerceIn(0f, size.width / 2f)
        }
        val halfTop = halfAt(topY)
        val halfBottom = halfAt(bottomY)
        val steps = 48
        val points = ArrayList<Offset>(steps * 2 + 4)
        points += Offset(cx, 0f)
        points += Offset(cx - halfTop, topY)
        for (i in 1 until steps) {
            val y = topY + (bottomY - topY) * i / steps
            points += Offset(cx - halfAt(y), y)
        }
        points += Offset(cx - halfBottom, bottomY)
        points += Offset(cx, size.height)
        points += Offset(cx + halfBottom, bottomY)
        for (i in steps - 1 downTo 1) {
            val y = topY + (bottomY - topY) * i / steps
            points += Offset(cx + halfAt(y), y)
        }
        points += Offset(cx + halfTop, topY)
        return smoothClosedPath(points)
    }

    private fun smoothClosedPath(points: List<Offset>): Path {
        val path = Path()
        if (points.size < 3) return path
        val n = points.size
        path.moveTo(points[0].x, points[0].y)
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        path.close()
        return path
    }

    fun cardPath(
        size: Size,
        topInRootPx: Float,
        screenHeightPx: Float,
        screenMarginPx: Float,
        capHeightPx: Float,
        roundTop: Boolean,
        roundBottom: Boolean
    ): Path {
        val centerY = screenHeightPx / 2f
        val radius = (screenHeightPx / 2f - screenMarginPx).coerceAtLeast(0f)
        val maxHalf = size.width / 2f
        fun halfAt(yPx: Float): Float {
            val dy = yPx - centerY
            val chord = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
            return chord.coerceIn(0f, maxHalf)
        }

        val cap = capHeightPx.coerceIn(0f, size.height / 2f)
        val topCap = if (roundTop) cap else 0f
        val bottomCap = if (roundBottom) cap else 0f
        val topY = topCap
        val bottomY = size.height - bottomCap
        val cx = size.width / 2f
        val halfTop = halfAt(topInRootPx + topY)
        val halfBottom = halfAt(topInRootPx + bottomY)
        val lTop = cx - halfTop
        val rTop = cx + halfTop
        val lBottom = cx - halfBottom
        val rBottom = cx + halfBottom
        fun xAt(yLocal: Float): Float = cx - halfAt(topInRootPx + yLocal)
        fun xRightAt(yLocal: Float): Float = cx + halfAt(topInRootPx + yLocal)

        return Path().apply {
            moveTo(lBottom, bottomY)
            // 左侧：沿圆周取 1/3、2/3 处两个控制点，三次曲线在行边界延续同一个切线。
            val leftThirdY = bottomY + (topY - bottomY) / 3f
            val leftTwoThirdY = bottomY + (topY - bottomY) * 2f / 3f
            cubicTo(
                xAt(leftThirdY), leftThirdY,
                xAt(leftTwoThirdY), leftTwoThirdY,
                lTop, topY
            )
            if (roundTop) {
                // 一段浅弧从左边直接连到右边；两个控制点保持竖向切线，和侧边共用同一条曲线。
                cubicTo(lTop, topY - cap * 1.33f, rTop, topY - cap * 1.33f, rTop, topY)
            } else {
                lineTo(rTop, 0f)
            }
            // 右侧：从上往下沿圆周连续弯曲。
            val rightThirdY = topY + (bottomY - topY) / 3f
            val rightTwoThirdY = topY + (bottomY - topY) * 2f / 3f
            cubicTo(
                xRightAt(rightThirdY), rightThirdY,
                xRightAt(rightTwoThirdY), rightTwoThirdY,
                rBottom, bottomY
            )
            if (roundBottom) {
                cubicTo(rBottom, bottomY + cap * 1.33f, lBottom, bottomY + cap * 1.33f, lBottom, bottomY)
            } else {
                lineTo(lBottom, size.height)
            }
            close()
        }
    }
}

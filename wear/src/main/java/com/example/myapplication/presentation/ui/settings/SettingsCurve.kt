package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.hypot
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
 * 因此上一行的底边半宽与下一行顶边半宽完全相等，切线也连续，整组拼成一条圆弧曲面。
 *
 * 组首 / 组尾 / 单行用 20dp 浅穹顶封口；中间行只保留连续侧弧。
 */
object SettingsCurveGeometry {
    /** 收口最小高度：20dp，实际高度由半宽反解，保证封口是一段圆拱而不是平盖。 */
    const val CapHeightDp = 20f

    /** 收口高度上限 = 最小高度的倍数：48dp，超过就会挤掉内容。 */
    const val CapMaxRatio = 2.4f

    /** 气球内容距顶/底的最小内缩：大于 [CapHeightDp] × [CapMaxRatio]，首末行不会坐进收口里。 */
    const val CapInsetDp = 56f

    /** 收口高度与半宽的比例：0.5 即 2:1，再扁就只剩一条平盖。 */
    private const val CapAspect = 0.5f

    /** 侧壁切分数：每段跨角小于 4°，肉眼不可分辨，也不产生折面。 */
    private const val WallSlices = 16

    /** 四分之一圆的三次曲线控制点系数。 */
    private const val ArcKappa = 0.5522848f

    /**
     * 整页主气球的轮廓段。
     *
     * 一条闭合曲线：宽度 = 屏幕圆弦 × 收口窗口。窗口是四分之一圆，顶点处半宽归零、
     * 切线水平；窗口终点导数为 0，所以它与侧壁的切线严格接上 —— 肩部既无折角，
     * 也不会像"另画一段拱"那样把控制点甩到宽度极值之外鼓出一小块。
     */
    internal fun outlineSegments(
        widthPx: Float,
        heightPx: Float,
        topInRootPx: Float,
        screenHeightPx: Float,
        screenMarginPx: Float,
        capHeightPx: Float
    ): List<BalloonSegment> {
        if (widthPx <= 0f || heightPx <= 0f) return emptyList()
        val radius = (screenHeightPx / 2f - screenMarginPx).coerceAtLeast(0f)
        if (radius <= 0f) return emptyList()
        val cx = widthPx / 2f
        val centerLocal = screenHeightPx / 2f - topInRootPx

        fun chord(localY: Float): Float {
            val dy = localY - centerLocal
            return sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        }

        // 整组最大弦长等比缩到内容盒：轮廓仍是圆弧，但不会越过盒宽。
        val nearestY = centerLocal.coerceIn(0f, heightPx)
        val scale = (cx / chord(nearestY).coerceAtLeast(0.001f)).coerceAtMost(1f)
        fun half(localY: Float): Float = (chord(localY) * scale).coerceIn(0f, cx)

        val cap = balloonCapPx(heightPx, cx, centerLocal, radius, capHeightPx)
        if (cap <= 0f) return emptyList()

        fun taperTop(localY: Float): Float {
            val t = (localY / cap).coerceIn(0f, 1f)
            return sqrt((1f - (1f - t) * (1f - t)).coerceAtLeast(0f))
        }

        fun taperBottom(localY: Float): Float {
            val t = ((heightPx - localY) / cap).coerceIn(0f, 1f)
            return sqrt((1f - (1f - t) * (1f - t)).coerceAtLeast(0f))
        }

        fun width(localY: Float): Float =
            (half(localY) * taperTop(localY) * taperBottom(localY)).coerceIn(0f, cx)

        fun widthSlope(localY: Float): Float {
            val chordAt = chord(localY).coerceAtLeast(0.001f)
            val halfBase = (chordAt * scale).coerceIn(0f, cx)
            val halfSlope = (centerLocal - localY) / chordAt * scale
            val tTop = (localY / cap).coerceIn(0f, 1f)
            val tBottom = ((heightPx - localY) / cap).coerceIn(0f, 1f)
            val uTop = taperTop(localY).coerceAtLeast(0.01f)
            val uBottom = taperBottom(localY).coerceAtLeast(0.01f)
            val dTop = if (tTop < 1f) (1f - tTop) / (uTop * cap) else 0f
            val dBottom = if (tBottom < 1f) -(1f - tBottom) / (uBottom * cap) else 0f
            return halfSlope * uTop * uBottom + halfBase * dTop * uBottom + halfBase * uTop * dBottom
        }

        // 结点：收口段密、侧壁段疏。收口在顶点附近变化最快。
        val fractions = floatArrayOf(0f, 0.12f, 0.26f, 0.44f, 0.66f, 0.85f, 1f)
        val knots = ArrayList<Float>(fractions.size * 2 + WallSlices)
        fractions.forEach { knots += cap * it }
        for (i in 1 until WallSlices) {
            val y = cap + (heightPx - 2f * cap) * i / WallSlices
            if (y > knots.last() + 0.01f) knots += y
        }
        for (i in fractions.indices.reversed()) {
            val y = heightPx - cap * fractions[i]
            if (y > knots.last() + 0.01f) knots += y
        }

        val right = ArrayList<BalloonSegment>(knots.size + 1)
        // 顶点那一段单独给水平切线：解析导数在顶点是无穷大，换成等价的水平控制点。
        val firstY = knots[1]
        val firstSlope = widthSlope(firstY)
        val firstHalf = width(firstY)
        val firstNorm = hypot(firstSlope, 1f)
        right += BalloonSegment(
            x0 = cx, y0 = 0f,
            c1x = cx + firstHalf * ArcKappa, c1y = 0f,
            c2x = cx + firstHalf - firstSlope * firstY / firstNorm / 3f,
            c2y = firstY - firstY / firstNorm / 3f,
            x1 = cx + firstHalf, y1 = firstY
        )
        var fromY = firstY
        var fromX = cx + firstHalf
        var fromSlope = firstSlope
        for (i in 2 until knots.size) {
            val y = knots[i]
            if (y <= fromY + 0.01f) continue
            val x = cx + width(y)
            val s = widthSlope(y)
            right += wallSlice(fromX, fromY, fromSlope, x, y, s)
            fromX = x
            fromY = y
            fromSlope = s
        }
        // 底点收尾：最后一段同样以水平切线落到 (cx, heightPx)。
        val lastSpan = heightPx - fromY
        val lastNorm = hypot(fromSlope, 1f)
        right += BalloonSegment(
            x0 = fromX, y0 = fromY,
            c1x = fromX + fromSlope * lastSpan / lastNorm / 3f,
            c1y = fromY + lastSpan / lastNorm / 3f,
            c2x = cx + (fromX - cx) * ArcKappa, c2y = heightPx,
            x1 = cx, y1 = heightPx
        )

        // 左半边直接镜像右半边（含反向），左右必然逐段对称。
        val segments = ArrayList<BalloonSegment>(right.size * 2)
        segments += right
        for (index in right.indices.reversed()) {
            val s = right[index]
            segments += BalloonSegment(
                x0 = 2f * cx - s.x1, y0 = s.y1,
                c1x = 2f * cx - s.c2x, c1y = s.c2y,
                c2x = 2f * cx - s.c1x, c2y = s.c1y,
                x1 = 2f * cx - s.x0, y1 = s.y0
            )
        }
        return segments
    }

    /**
     * 收口段的高度：由半宽反解，半宽越大收口越长。
     *
     * 固定的 20dp 会把宽气球的封口压成平盖加一个尖；这里按比例放大，
     * 上限 [CapMaxRatio] 倍，且永远不超过该处半宽 —— 否则窄气球会被收成一个尖。
     */
    internal fun balloonCapPx(
        heightPx: Float,
        halfWidthPx: Float,
        centerLocalPx: Float,
        radiusPx: Float,
        capHeightPx: Float
    ): Float {
        if (heightPx <= 0f || capHeightPx <= 0f || radiusPx <= 0f) return 0f
        val capMax = (capHeightPx * CapMaxRatio).coerceAtMost(heightPx / 2f)
        val chordAtCenter = sqrt(
            (radiusPx * radiusPx - centerLocalPx.coerceIn(-radiusPx, radiusPx) *
                centerLocalPx.coerceIn(-radiusPx, radiusPx)).coerceAtLeast(0.001f)
        )
        val scale = (halfWidthPx / chordAtCenter).coerceAtMost(1f)
        fun shoulderHalf(candidate: Float): Float {
            val dy = candidate - centerLocalPx
            val chordAt = sqrt((radiusPx * radiusPx - dy * dy).coerceAtLeast(0f))
            return (chordAt * scale).coerceIn(0f, halfWidthPx)
        }
        var cap = capHeightPx.coerceIn(0f, capMax)
        repeat(6) {
            val shoulder = shoulderHalf(cap)
            cap = (shoulder * CapAspect).coerceIn(capHeightPx, capMax)
            cap = cap.coerceAtMost(shoulder.coerceAtLeast(0.5f))
            cap = cap.coerceAtMost(capMax)
        }
        return cap
    }

    /** 整页主气球：一条闭合轮廓，顶底为收口。 */
    fun groupPath(
        size: Size,
        topInRootPx: Float,
        screenHeightPx: Float,
        screenMarginPx: Float,
        capHeightPx: Float
    ): Path = buildGroupPath(
        path = Path(),
        size = size,
        topInRootPx = topInRootPx,
        screenHeightPx = screenHeightPx,
        screenMarginPx = screenMarginPx,
        capHeightPx = capHeightPx
    )

    /**
     * 把轮廓重建进调用方给的 [path]（先 reset）。
     *
     * 滚动时轮廓每帧都要变（宽度跟着屏幕 y 走），复用同一个 Path 就不必每帧新分配一个
     * —— 表上滚动掉帧主要来自这种逐帧小分配，而不是 GPU。
     */
    fun buildGroupPath(
        path: Path,
        size: Size,
        topInRootPx: Float,
        screenHeightPx: Float,
        screenMarginPx: Float,
        capHeightPx: Float
    ): Path {
        path.reset()
        val segments = outlineSegments(
            widthPx = size.width,
            heightPx = size.height,
            topInRootPx = topInRootPx,
            screenHeightPx = screenHeightPx,
            screenMarginPx = screenMarginPx,
            capHeightPx = capHeightPx
        )
        if (segments.isEmpty()) return path
        path.moveTo(segments[0].x0, segments[0].y0)
        for (segment in segments) {
            path.cubicTo(segment.c1x, segment.c1y, segment.c2x, segment.c2y, segment.x1, segment.y1)
        }
        path.close()
        return path
    }

    /** 一段侧壁：两端切线取解析导数（沿行进方向的 dx/dy），切片之间与收口之间都是 G1 连续。 */
    private fun wallSlice(
        x0: Float,
        y0: Float,
        s0: Float,
        x1: Float,
        y1: Float,
        s1: Float
    ): BalloonSegment {
        val deltaY = y1 - y0
        val n0 = hypot(s0, 1f)
        val n1 = hypot(s1, 1f)
        return BalloonSegment(
            x0 = x0, y0 = y0,
            c1x = x0 + s0 * deltaY / n0 / 3f,
            c1y = y0 + deltaY / n0 / 3f,
            c2x = x1 - s1 * deltaY / n1 / 3f,
            c2y = y1 - deltaY / n1 / 3f,
            x1 = x1, y1 = y1
        )
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

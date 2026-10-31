package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.ApprovalVisualState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 预计算的静态点阵几何：六个平行 FloatArray 逐点平铺存放，绘制期按下标只读访问。
 * 用基本类型数组而不是对象列表，避免逐帧装箱与指针追逐。
 */
private class MatrixGrid(
    val count: Int,
    val widthPx: Float,
    val heightPx: Float,
    val radiusPx: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    val distCenter: FloatArray,
    val edgeFade: FloatArray,
    val distFromButton: FloatArray,
    val projCoord: FloatArray,
)

private val EMPTY_FLOATS = FloatArray(0)

private val EMPTY_GRID = MatrixGrid(
    count = 0,
    widthPx = 0f,
    heightPx = 0f,
    radiusPx = 0f,
    xs = EMPTY_FLOATS,
    ys = EMPTY_FLOATS,
    distCenter = EMPTY_FLOATS,
    edgeFade = EMPTY_FLOATS,
    distFromButton = EMPTY_FLOATS,
    projCoord = EMPTY_FLOATS,
)

private val COLOR_DOT_BASE = Color(0xFFE2E8F0)
private val COLOR_DOT_SHOCK = Color(0xFFFFFFFF) // 纯净亮白微晶光芒，彻底消除廉价土味蓝
private val COLOR_DOT_GREEN = Color(0xFF10B981)
private val COLOR_DOT_CASCADE = Color(0xFFEF4444)
private val COLOR_DOT_WHITE = Color.White

private const val HALF_PI = (Math.PI / 2.0).toFloat()

/**
 * 单次构建整个屏幕的点阵几何 (仅在画布尺寸或网格间距变化时执行一次)。
 * 圆心坐标、到圆心距离、边缘衰减、到底栏波源距离、掠影投影坐标全部提前算好。
 */
private fun buildMatrixGrid(
    width: Float,
    height: Float,
    stepPx: Float,
    micOriginX: Float,
    micOriginY: Float,
): MatrixGrid {
    val maxRadius = minOf(width, height) / 2f
    if (width <= 0f || height <= 0f || maxRadius <= 0f || stepPx <= 0f) return EMPTY_GRID

    val centerX = width / 2f
    val centerY = height / 2f
    val halfCount = (maxRadius / stepPx).toInt() + 1
    val side = halfCount * 2 + 1
    val capacity = side * side
    val xs = FloatArray(capacity)
    val ys = FloatArray(capacity)
    val distCenterArr = FloatArray(capacity)
    val edgeFadeArr = FloatArray(capacity)
    val distFromBtnArr = FloatArray(capacity)
    val projCoordArr = FloatArray(capacity)

    var index = 0
    for (kx in -halfCount..halfCount) {
        val x = centerX + kx * stepPx
        for (ky in -halfCount..halfCount) {
            val y = centerY + ky * stepPx
            val dx = x - centerX
            val dy = y - centerY
            val distCenter = sqrt(dx * dx + dy * dy)
            if (distCenter <= maxRadius) {
                val normDist = (distCenter / maxRadius).coerceIn(0f, 1f)
                val bx = x - micOriginX
                val by = y - micOriginY
                xs[index] = x
                ys[index] = y
                distCenterArr[index] = distCenter
                edgeFadeArr[index] = (1.0f - normDist * normDist).coerceIn(0f, 1f)
                distFromBtnArr[index] = sqrt(bx * bx + by * by)
                projCoordArr[index] = x + y * 0.62f
                index++
            }
        }
    }

    return MatrixGrid(
        count = index,
        widthPx = width,
        heightPx = height,
        radiusPx = maxRadius,
        xs = xs,
        ys = ys,
        distCenter = distCenterArr,
        edgeFade = edgeFadeArr,
        distFromButton = distFromBtnArr,
        projCoord = projCoordArr,
    )
}

/**
 * 纯圆表盘高亮大微晶方形点阵渲染引擎 (浮于遮罩之上 · 加大高质感光点 · 强烈水波纹扩散 · 四态光相变)
 * 1. 方形阵列：严格基于直角坐标系 (x, y) 均匀栅格排布，以圆心为基准对称排布；
 * 2. 覆盖全圆：完全铺满圆形表盘的所有区域（包括四周圆弧月半弧），无任何方形裁剪框；
 * 3. 几何预缓存：所有点位与距离在组合期一次性算入 FloatArray，绘制期不再分配对象、不再求平方根；
 * 4. 动态行波节流：闲置待机时停止思考波动帧驱动，杜绝后台空转耗电。
 */
@Composable
fun UnifiedMatrixCanvas(
    modifier: Modifier = Modifier,
    approvalVisualState: ApprovalVisualState = ApprovalVisualState.NONE,
    isTaskCompletedTrigger: Boolean = false, // 触发完工掠影反光
    thinkingIntensity: Float = 0f,           // 0f ~ 1f 思考引擎强度
    isRecordingVoice: Boolean = false,       // 录音状态
    shockwaveProgress: Float = -1f,          // 底栏激荡冲击波 (0f ~ 1f)
    gridSpacingDp: Float = 16f               // 网格物理间距 (dp)
) {
    // 3.0s 沉稳深邃呼吸过渡 (加大呼吸振幅)
    val infiniteTransition = rememberInfiniteTransition(label = "matrixBreathTransition")
    val breathCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathCycle"
    )

    // 思考同心行波物理相位：无限平滑周期循环，绝不重置打断，避免启动时闪烁
    val thinkingWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingWavePhase"
    )

    // 红瀑下倾进度动画 (0f ~ 1f)
    val cascadeAnim = remember { Animatable(0f) }
    // 完工表镜掠影动画 (-0.4f ~ 1.4f)
    val glintAnim = remember { Animatable(-0.5f) }

    LaunchedEffect(approvalVisualState) {
        when (approvalVisualState) {
            ApprovalVisualState.PENDING_CASCADE -> {
                cascadeAnim.snapTo(0f)
                cascadeAnim.animateTo(1f, tween(360, easing = LinearEasing))
            }
            ApprovalVisualState.REJECTED_EBB -> {
                cascadeAnim.animateTo(0f, tween(260, easing = LinearEasing))
            }
            ApprovalVisualState.APPROVED_GREEN -> {
                cascadeAnim.animateTo(0f, tween(400, easing = LinearEasing))
            }
            ApprovalVisualState.NONE -> {
                cascadeAnim.snapTo(0f)
            }
        }
    }

    LaunchedEffect(isTaskCompletedTrigger) {
        if (isTaskCompletedTrigger) {
            glintAnim.snapTo(-0.4f)
            glintAnim.animateTo(1.4f, tween(380, easing = LinearEasing))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val stepPx = with(density) { gridSpacingDp.dp.toPx() }.coerceAtLeast(10f)
        val micOriginOffsetPx = with(density) { 34.dp.toPx() }
        val bounded = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val boxWidthPx = if (bounded) constraints.maxWidth.toFloat() else 0f
        val boxHeightPx = if (bounded) constraints.maxHeight.toFloat() else 0f

        // 几何只在 (画布尺寸, 网格间距) 变化时重建一次，其余帧直接复用
        val grid = remember(boxWidthPx, boxHeightPx, stepPx, micOriginOffsetPx) {
            buildMatrixGrid(
                width = boxWidthPx,
                height = boxHeightPx,
                stepPx = stepPx,
                micOriginX = boxWidthPx / 2f,
                micOriginY = boxHeightPx - micOriginOffsetPx,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = grid.count
            if (count == 0) return@Canvas

            val w = size.width
            val h = size.height
            val maxRadius = minOf(w, h) / 2f
            val scaleX = if (grid.widthPx > 0f) w / grid.widthPx else 1f
            val scaleY = if (grid.heightPx > 0f) h / grid.heightPx else 1f

            val xs = grid.xs
            val ys = grid.ys
            val distCenterArr = grid.distCenter
            val edgeFadeArr = grid.edgeFade
            val distFromButtonArr = grid.distFromButton
            val projCoordArr = grid.projCoord

            val baseRadius = 2.3.dp.toPx()
            val shockBand = 54.dp.toPx()
            val rippleBandWidth = 50.dp.toPx()
            val cascadeBand = 60.dp.toPx()
            val glintBandWidth = 48.dp.toPx()
            val dynamicUnit = 0.8.dp.toPx()

            // ---- 逐帧不变量的行波参数，一次性算好 ----
            val glintPosition = glintAnim.value * (w + h)
            val cascadeCurrentY = cascadeAnim.value * h
            val hasShock = shockwaveProgress in 0f..1.2f
            val shockTravel = shockwaveProgress * maxRadius * 2.3f
            val shockEnergy = (1f - shockwaveProgress * 0.35f).coerceIn(0f, 1f)
            val hasRipple = thinkingIntensity > 0.01f
            val rippleTravel = (thinkingWavePhase * maxRadius * 1.4f) % (maxRadius * 1.25f)
            val rippleReach = maxRadius * 1.25f
            val hasGlint = glintAnim.value in -0.3f..1.3f
            val hasCascade = cascadeAnim.value > 0.01f
            val approvedGreen = approvalVisualState == ApprovalVisualState.APPROVED_GREEN
            val breathBase = 0.24f + 0.28f * breathCycle

            var i = 0
            while (i < count) {
                val edgeFade = edgeFadeArr[i]

                // 1. 基础待机与呼吸明暗 (提升基础对比度与明亮度)
                val baseAlpha = if (isRecordingVoice) 0.18f * edgeFade else breathBase * edgeFade

                // 2. 从底部按钮向全屏扩散的强烈水波纹 (Button Ripple)
                var shockAlpha = 0f
                var shockScale = 0f
                if (hasShock) {
                    val distDiff = abs(distFromButtonArr[i] - shockTravel)
                    if (distDiff < shockBand) {
                        val falloff = cos((distDiff / shockBand) * HALF_PI).let { it * it }
                        shockAlpha = falloff * shockEnergy * 1.1f * (0.35f + 0.65f * edgeFade)
                        shockScale = falloff * shockEnergy * 2.2f
                    }
                }

                // 3. 思考状态下的同心持续呼吸波
                var rippleAlpha = 0f
                var rippleScale = 0f
                if (hasRipple) {
                    val distDiff = abs(distCenterArr[i] - rippleTravel)
                    if (distDiff < rippleBandWidth) {
                        val falloff = cos((distDiff / rippleBandWidth) * HALF_PI).let { it * it }
                        val energy = (1f - distCenterArr[i] / rippleReach).coerceIn(0.1f, 1f)
                        rippleAlpha = falloff * energy * 0.85f * thinkingIntensity * edgeFade
                        rippleScale = falloff * energy * 1.6f * thinkingIntensity
                    }
                }

                // 4. 完工掠影反光 (Glint: 30 度冷白反光扫过)
                var glintAlpha = 0f
                var glintScale = 0f
                if (hasGlint) {
                    val distGlint = abs(projCoordArr[i] - glintPosition)
                    if (distGlint < glintBandWidth) {
                        val falloff = cos((distGlint / glintBandWidth) * HALF_PI).let { it * it }
                        glintAlpha = falloff * 0.95f * edgeFade
                        glintScale = falloff * 2.0f
                    }
                }

                // 5. 红瀑 / 翡翠绿光相变
                var isParticleInCascade = false
                var cascadeRatio = 0f
                if (hasCascade && ys[i] <= cascadeCurrentY) {
                    isParticleInCascade = true
                    val distFromFront = abs(cascadeCurrentY - ys[i])
                    cascadeRatio = (1f - (distFromFront / cascadeBand).coerceIn(0f, 1f))
                }

                // 颜色与混合决定
                val particleColor = when {
                    approvedGreen -> COLOR_DOT_GREEN.copy(alpha = (0.85f * edgeFade).coerceIn(0f, 1f))
                    isParticleInCascade -> COLOR_DOT_CASCADE.copy(
                        alpha = ((0.46f + 0.54f * cascadeRatio) * edgeFade).coerceIn(0f, 1f)
                    )
                    glintAlpha > 0.05f -> COLOR_DOT_WHITE.copy(alpha = (baseAlpha + glintAlpha).coerceIn(0f, 1f))
                    shockAlpha > 0.05f -> COLOR_DOT_SHOCK.copy(alpha = (baseAlpha + shockAlpha).coerceIn(0f, 1f))
                    else -> COLOR_DOT_BASE.copy(alpha = (baseAlpha + rippleAlpha).coerceIn(0f, 1f))
                }

                val dynamicRadius = baseRadius +
                    (rippleScale * 1.0f + glintScale * 1.2f + shockScale * 1.5f) * dynamicUnit

                drawCircle(
                    color = particleColor,
                    radius = dynamicRadius,
                    center = Offset(xs[i] * scaleX, ys[i] * scaleY)
                )
                i++
            }
        }
    }
}

package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.ApprovalVisualState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 预计算并缓存的静态微晶点阵几何节点 (零 GC 开销 · 消除逐帧 sqrt 与 pow)
 */
private class MatrixGridPoint(
    val x: Float,
    val y: Float,
    val distCenter: Float,
    val edgeFade: Float,
    val distFromButton: Float,
    val projCoord: Float
)

private val COLOR_DOT_BASE = Color(0xFFE2E8F0)
private val COLOR_DOT_SHOCK = Color(0xFF7DD3FC)
private val COLOR_DOT_GREEN = Color(0xFF10B981)
private val COLOR_DOT_CASCADE = Color(0xFFEF4444)
private val COLOR_DOT_WHITE = Color.White

/**
 * 纯圆表盘高亮大微晶方形点阵渲染引擎 (浮于遮罩之上 · 加大高质感光点 · 强烈水波纹扩散 · 四态光相变)
 * 1. 方形阵列：严格基于直角坐标系 (x, y) 均匀栅格排布，以圆心为基准对称排布；
 * 2. 覆盖全圆：完全铺满圆形表盘的所有区域（包括四周圆弧月半弧），无任何方形裁剪框；
 * 3. 几何预缓存：所有点位与距离零每帧平方根重复计算，杜绝卡顿与内存抖动；
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

    var cachedPoints by remember { mutableStateOf<List<MatrixGridPoint>>(emptyList()) }
    var cachedWidth by remember { mutableFloatStateOf(0f) }
    var cachedHeight by remember { mutableFloatStateOf(0f) }
    var cachedSpacing by remember { mutableFloatStateOf(0f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f
        val maxRadius = minOf(w, h) / 2f
        val stepPx = gridSpacingDp.dp.toPx().coerceAtLeast(10f)

        // 底部按键物理中心（波纹扩散发源地）
        val micOriginX = centerX
        val micOriginY = h - 34.dp.toPx()

        // 完工掠影当前斜线位置
        val glintPosition = glintAnim.value * (w + h)
        val glintBandWidth = 48.dp.toPx()

        // 红瀑当前扫描线 Y
        val cascadeCurrentY = cascadeAnim.value * h

        // 尺寸或间距变化时单次预计算所有网格静态几何节点
        if (w != cachedWidth || h != cachedHeight || stepPx != cachedSpacing) {
            val list = ArrayList<MatrixGridPoint>()
            val halfCount = (maxRadius / stepPx).toInt() + 1
            for (kx in -halfCount..halfCount) {
                val x = centerX + kx * stepPx
                for (ky in -halfCount..halfCount) {
                    val y = centerY + ky * stepPx
                    val dx = x - centerX
                    val dy = y - centerY
                    val distCenter = sqrt(dx * dx + dy * dy)
                    if (distCenter <= maxRadius) {
                        val normDist = (distCenter / maxRadius).coerceIn(0f, 1f)
                        val edgeFade = (1.0f - normDist * normDist).coerceIn(0f, 1f)
                        val distFromBtn = sqrt((x - micOriginX) * (x - micOriginX) + (y - micOriginY) * (y - micOriginY))
                        val proj = x + y * 0.62f
                        list.add(MatrixGridPoint(x, y, distCenter, edgeFade, distFromBtn, proj))
                    }
                }
            }
            cachedPoints = list
            cachedWidth = w
            cachedHeight = h
            cachedSpacing = stepPx
        }

        val points = cachedPoints
        val baseRadius = 2.3.dp.toPx()
        val shockBand = 54.dp.toPx()
        val rippleBandWidth = 50.dp.toPx()
        val dynamicUnit = 0.8.dp.toPx()

        for (i in points.indices) {
            val pt = points[i]
            val edgeFade = pt.edgeFade

            // 1. 基础待机与呼吸明暗 (提升基础对比度与明亮度)
            val baseAlpha = if (isRecordingVoice) {
                0.18f * edgeFade
            } else {
                (0.24f + 0.28f * breathCycle) * edgeFade
            }

            // 2. 从底部按钮向全屏扩散的强烈水波纹 (Button Ripple)
            var shockAlpha = 0f
            var shockScale = 0f
            if (shockwaveProgress in 0f..1.2f) {
                val travel = shockwaveProgress * maxRadius * 2.3f
                val distDiff = abs(pt.distFromButton - travel)
                if (distDiff < shockBand) {
                    val falloff = cos((distDiff / shockBand) * (Math.PI.toFloat() / 2f)).let { it * it }
                    val energy = (1f - shockwaveProgress * 0.35f).coerceIn(0f, 1f)
                    shockAlpha = falloff * energy * 1.1f * (0.35f + 0.65f * edgeFade)
                    shockScale = falloff * energy * 2.2f
                }
            }

            // 3. 思考状态下的同心持续呼吸波
            var rippleAlpha = 0f
            var rippleScale = 0f
            if (thinkingIntensity > 0.01f) {
                val travel = (thinkingWavePhase * maxRadius * 1.4f) % (maxRadius * 1.25f)
                val distDiff = abs(pt.distCenter - travel)
                if (distDiff < rippleBandWidth) {
                    val falloff = cos((distDiff / rippleBandWidth) * (Math.PI.toFloat() / 2f)).let { it * it }
                    val energy = (1f - pt.distCenter / (maxRadius * 1.25f)).coerceIn(0.1f, 1f)
                    rippleAlpha = falloff * energy * 0.85f * thinkingIntensity * edgeFade
                    rippleScale = falloff * energy * 1.6f * thinkingIntensity
                }
            }

            // 4. 完工掠影反光 (Glint: 30 度冷白反光扫过)
            var glintAlpha = 0f
            var glintScale = 0f
            if (glintAnim.value in -0.3f..1.3f) {
                val distGlint = abs(pt.projCoord - glintPosition)
                if (distGlint < glintBandWidth) {
                    val falloff = cos((distGlint / glintBandWidth) * (Math.PI.toFloat() / 2f)).let { it * it }
                    glintAlpha = falloff * 0.95f * edgeFade
                    glintScale = falloff * 2.0f
                }
            }

            // 5. 红瀑 / 翡翠绿光相变
            var isParticleInCascade = false
            var cascadeRatio = 0f
            if (cascadeAnim.value > 0.01f && pt.y <= cascadeCurrentY) {
                isParticleInCascade = true
                val distFromFront = abs(cascadeCurrentY - pt.y)
                val cascadeBand = 60.dp.toPx()
                cascadeRatio = (1f - (distFromFront / cascadeBand).coerceIn(0f, 1f))
            }

            // 颜色与混合决定
            val particleColor = when {
                approvalVisualState == ApprovalVisualState.APPROVED_GREEN -> {
                    COLOR_DOT_GREEN.copy(alpha = (0.85f * edgeFade).coerceIn(0f, 1f))
                }
                isParticleInCascade -> {
                    val redAlpha = (0.46f + 0.54f * cascadeRatio) * edgeFade
                    COLOR_DOT_CASCADE.copy(alpha = redAlpha.coerceIn(0f, 1f))
                }
                glintAlpha > 0.05f -> {
                    COLOR_DOT_WHITE.copy(alpha = (baseAlpha + glintAlpha).coerceIn(0f, 1f))
                }
                shockAlpha > 0.05f -> {
                    COLOR_DOT_SHOCK.copy(alpha = (baseAlpha + shockAlpha).coerceIn(0f, 1f))
                }
                else -> {
                    COLOR_DOT_BASE.copy(alpha = (baseAlpha + rippleAlpha).coerceIn(0f, 1f))
                }
            }

            val dynamicRadius = baseRadius + (rippleScale * 1.0f + glintScale * 1.2f + shockScale * 1.5f) * dynamicUnit

            drawCircle(
                color = particleColor,
                radius = dynamicRadius,
                center = Offset(pt.x, pt.y)
            )
        }
    }
}

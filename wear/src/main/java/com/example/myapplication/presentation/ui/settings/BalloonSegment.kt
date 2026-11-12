package com.example.myapplication.presentation.ui.settings

/**
 * 一条三次贝塞尔轮廓段。
 *
 * 只描述几何，不依赖 Compose，便于在 JVM 单测里逐点验证曲线形状。
 */
internal data class BalloonSegment(
    val x0: Float,
    val y0: Float,
    val c1x: Float,
    val c1y: Float,
    val c2x: Float,
    val c2y: Float,
    val x1: Float,
    val y1: Float
)

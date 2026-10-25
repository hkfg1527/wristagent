package com.example.myapplication.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 顶部弧形时间显示 (极坐标逐字弧形贴边排版，居中对称)
 */
@Composable
fun TopClockBar(
    timeText: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val w = size.width
        val centerX = w / 2f
        val centerY = centerX // 手表圆心在 (centerX, centerX)
        val arcRadius = centerX - 14.dp.toPx()

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#E2E8F0")
                textSize = 13.sp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
                )
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val textLength = timeText.length
            val midIndex = (textLength - 1) / 2f
            val charAngleStepDeg = 4.6f

            for (i in 0 until textLength) {
                val charStr = timeText[i].toString()
                val angleDeg = -90f + (i - midIndex) * charAngleStepDeg
                val angleRad = angleDeg * (Math.PI.toFloat() / 180f)

                val charX = centerX + arcRadius * kotlin.math.cos(angleRad)
                val charY = centerY + arcRadius * kotlin.math.sin(angleRad)
                val rotationDeg = angleDeg + 90f

                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(charX, charY)
                canvas.nativeCanvas.rotate(rotationDeg)
                canvas.nativeCanvas.drawText(charStr, 0f, paint.textSize / 3f, paint)
                canvas.nativeCanvas.restore()
            }
        }
    }
}

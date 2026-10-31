package com.example.myapplication.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 顶部时间：只保留时间文本本身，无装饰弧线与胶囊。
 */
@Composable
fun TopClockBar(
    timeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Wear.Space.Sm),
        contentAlignment = Alignment.TopCenter
    ) {
        BasicText(
            text = timeText,
            style = Wear.TextDisplay,
            maxLines = 1
        )
    }
}

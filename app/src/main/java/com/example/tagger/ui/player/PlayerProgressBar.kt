package com.example.tagger.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 格式化毫秒为 mm:ss 显示字符串
 */
fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * 播放器进度条组件
 * 封装 Slider、当前时间/总时长格式化、拖动 seek
 */
@Composable
fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
    timeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val safeDuration = duration.coerceAtLeast(0L)
    val safePosition = currentPosition.coerceIn(0L, safeDuration)
    val progress = if (safeDuration > 0) {
        safePosition.toFloat() / safeDuration.toFloat()
    } else {
        0f
    }
    var sliderProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress, isDragging) {
        if (!isDragging) {
            sliderProgress = progress
        }
    }

    val displayPosition = if (isDragging && safeDuration > 0) {
        (sliderProgress * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        safePosition
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度 Slider
        Slider(
            value = sliderProgress,
            onValueChange = { newProgress ->
                isDragging = true
                sliderProgress = newProgress.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (safeDuration > 0) {
                    onSeekTo((sliderProgress * safeDuration).toLong())
                }
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = trackColor
            )
        )

        // 时间显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationMs(displayPosition),
                style = MaterialTheme.typography.labelMedium,
                color = timeColor
            )
            Text(
                text = formatDurationMs(safeDuration),
                style = MaterialTheme.typography.labelMedium,
                color = timeColor
            )
        }
    }
}

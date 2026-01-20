package com.example.tagger.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tagger.core.video.ExtractionProgress
import com.example.tagger.core.video.ExtractionResult
import com.example.tagger.core.video.ExtractionState
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGreen
import com.example.tagger.ui.theme.AppleRed

/**
 * Dialog showing extraction progress and result.
 */
@Composable
fun ExtractionProgressDialog(
    state: ExtractionState,
    onDismiss: () -> Unit,
    onImportAudio: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = {
            // Only allow dismiss when completed or failed
            if (state is ExtractionState.Completed || state is ExtractionState.Failed) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state is ExtractionState.Completed || state is ExtractionState.Failed,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is ExtractionState.Analyzing -> AnalyzingContent()
                    is ExtractionState.Ready -> AnalyzingContent() // Show analyzing while preparing
                    is ExtractionState.Extracting -> ExtractingContent(state.progress)
                    is ExtractionState.Completed -> CompletedContent(
                        result = state.result,
                        onDismiss = onDismiss,
                        onImportAudio = onImportAudio
                    )
                    is ExtractionState.Failed -> FailedContent(
                        error = state.error,
                        onDismiss = onDismiss
                    )
                    is ExtractionState.Idle -> {} // Should not happen
                }
            }
        }
    }
}

@Composable
private fun AnalyzingContent() {
    CircularProgressIndicator(
        modifier = Modifier.size(64.dp),
        color = AppPrimaryColor,
        strokeWidth = 5.dp
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "正在分析视频...",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "请稍候",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExtractingContent(progress: ExtractionProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.percent / 100f,
        label = "progress"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(80.dp),
            color = AppPrimaryColor,
            strokeWidth = 6.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = progress.progressText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppPrimaryColor
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "正在提取音轨...",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = progress.timeText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    progress.speed?.let { speed ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "速度: $speed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletedContent(
    result: ExtractionResult.Success,
    onDismiss: () -> Unit,
    onImportAudio: (() -> Unit)?
) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = AppleGreen
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "提取成功",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = AppleGreen
    )

    Spacer(modifier = Modifier.height(16.dp))

    // File info
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = AppPrimaryColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(result.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("关闭")
        }

        if (onImportAudio != null) {
            Button(
                onClick = {
                    onImportAudio()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPrimaryColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("导入列表")
            }
        }
    }
}

@Composable
private fun FailedContent(
    error: String,
    onDismiss: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = AppleRed
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "提取失败",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = AppleRed
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = error,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppPrimaryColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("确定")
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

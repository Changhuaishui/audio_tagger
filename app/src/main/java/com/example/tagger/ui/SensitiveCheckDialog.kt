package com.example.tagger.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tagger.core.SensitiveCheckResult

/**
 * 敏感词检测对话框
 * 设计理念：简洁、直观、专注
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveCheckDialog(
    text: String,
    result: SensitiveCheckResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenOnlineTool: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "违禁词检测",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 状态指示器
                AnimatedContent(
                    targetState = when {
                        isLoading -> "loading"
                        result == null -> "idle"
                        result.isClean -> "clean"
                        else -> "found"
                    },
                    label = "status"
                ) { status ->
                    when (status) {
                        "loading" -> LoadingState()
                        "clean" -> CleanState()
                        "found" -> FoundState(result!!)
                        else -> IdleState()
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 检测的文本
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "检测内容",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (result != null && result.foundWords.isNotEmpty()) {
                            HighlightedText(text, result)
                        } else {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenOnlineTool,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("在线检测")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("完成")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在检测...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "准备检测",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CleanState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFF4CAF50)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "未检测到违禁词",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4CAF50)
        )
        Text(
            text = "内容安全，可以放心使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FoundState(result: SensitiveCheckResult) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFFF9800).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFFFF9800)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "检测到 ${result.foundWords.size} 个敏感词",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFFF9800)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 敏感词标签
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            result.foundWords.forEach { word ->
                SuggestionChip(
                    onClick = { },
                    label = { Text(word.word) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFFFF9800).copy(alpha = 0.1f),
                        labelColor = Color(0xFFFF9800)
                    )
                )
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, result: SensitiveCheckResult) {
    val annotatedString = buildAnnotatedString {
        var lastEnd = 0
        val sortedWords = result.foundWords.sortedBy { it.startIndex }

        for (word in sortedWords) {
            // 添加普通文本
            if (word.startIndex > lastEnd) {
                append(text.substring(lastEnd, word.startIndex))
            }
            // 添加高亮文本
            withStyle(
                SpanStyle(
                    background = Color(0xFFFF9800).copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            ) {
                append(text.substring(word.startIndex, word.endIndex))
            }
            lastEnd = word.endIndex
        }
        // 添加剩余文本
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // 简化的 FlowRow 实现
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        content()
    }
}

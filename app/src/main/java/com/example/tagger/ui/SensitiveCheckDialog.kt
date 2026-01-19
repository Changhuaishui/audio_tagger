package com.example.tagger.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.ui.theme.*

/**
 * Apple 风格敏感词检测对话框
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态图标
                AnimatedContent(
                    targetState = when {
                        isLoading -> "loading"
                        result == null -> "idle"
                        result.isClean -> "clean"
                        else -> "found"
                    },
                    label = "status",
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    }
                ) { status ->
                    when (status) {
                        "loading" -> LoadingState()
                        "clean" -> CleanState()
                        "found" -> FoundState(result!!)
                        else -> IdleState()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 检测内容预览
                if (text.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AppleGray6
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "检测内容",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppleGray1
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (result != null && result.foundWords.isNotEmpty()) {
                                HighlightedText(text, result)
                            } else {
                                Text(
                                    text = text.take(200) + if (text.length > 200) "..." else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 在线检测按钮
                    OutlinedButton(
                        onClick = onOpenOnlineTool,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppleBlue
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "在线检测",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    // 完成按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            "完成",
                            style = MaterialTheme.typography.titleSmall
                        )
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
            strokeWidth = 3.dp,
            color = AppleBlue
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在检测...",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleGray1
        )
    }
}

@Composable
private fun IdleState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(AppleGray6),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = AppleGray1
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "准备检测",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
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
                .size(80.dp)
                .clip(CircleShape)
                .background(AppleGreen.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppleGreen
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "内容安全",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppleGreen
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "未检测到违禁词",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleGray1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundState(result: SensitiveCheckResult) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(AppleOrange.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppleOrange
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "发现 ${result.foundWords.size} 个敏感词",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppleOrange
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 敏感词标签 - 使用 FlowRow
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            result.foundWords.take(10).forEach { word ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppleOrange.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleOrange,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            if (result.foundWords.size > 10) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppleGray5
                ) {
                    Text(
                        text = "+${result.foundWords.size - 10}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleGray1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, result: SensitiveCheckResult) {
    val displayText = text.take(200)
    val annotatedString = buildAnnotatedString {
        var lastEnd = 0
        val sortedWords = result.foundWords
            .filter { it.startIndex < displayText.length }
            .sortedBy { it.startIndex }

        for (word in sortedWords) {
            val start = word.startIndex.coerceAtMost(displayText.length)
            val end = word.endIndex.coerceAtMost(displayText.length)

            if (start > lastEnd) {
                append(displayText.substring(lastEnd, start))
            }
            withStyle(
                SpanStyle(
                    background = AppleOrange.copy(alpha = 0.2f),
                    fontWeight = FontWeight.SemiBold,
                    color = AppleOrange
                )
            ) {
                append(displayText.substring(start, end))
            }
            lastEnd = end
        }
        if (lastEnd < displayText.length) {
            append(displayText.substring(lastEnd))
        }
        if (text.length > 200) {
            append("...")
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium
    )
}

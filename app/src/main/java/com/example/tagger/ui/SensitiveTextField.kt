package com.example.tagger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 带敏感词检测的输入框
 * 敏感词会用红色下划线标记
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    sensitiveWords: Set<String>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    val foundWords = remember(value, sensitiveWords) {
        findSensitiveWords(value, sensitiveWords)
    }

    val hasSensitiveWords = foundWords.isNotEmpty()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = hasSensitiveWords,
        supportingText = if (hasSensitiveWords) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    foundWords.forEach { match ->
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = match.word,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFEBEE),
                                labelColor = Color(0xFFD32F2F)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        } else null
    )
}

/**
 * 检测到的敏感词信息
 */
data class SensitiveWordMatch(
    val word: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 在文本中查找敏感词
 */
private fun findSensitiveWords(text: String, sensitiveWords: Set<String>): List<SensitiveWordMatch> {
    if (text.isEmpty() || sensitiveWords.isEmpty()) return emptyList()

    val lowerText = text.lowercase()
    val matches = mutableListOf<SensitiveWordMatch>()

    for (word in sensitiveWords) {
        if (word.isEmpty()) continue
        var startIndex = 0
        while (true) {
            val index = lowerText.indexOf(word.lowercase(), startIndex)
            if (index == -1) break
            matches.add(
                SensitiveWordMatch(
                    word = text.substring(index, index + word.length),
                    startIndex = index,
                    endIndex = index + word.length
                )
            )
            startIndex = index + 1
        }
    }

    // 去重并取前5个，避免显示太多
    return matches.distinctBy { it.word.lowercase() }
        .sortedBy { it.startIndex }
        .take(5)
}

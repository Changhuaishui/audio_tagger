package com.example.tagger.core

import android.content.Context
import com.example.tagger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 敏感词检测结果
 */
data class SensitiveCheckResult(
    val originalText: String,
    val foundWords: List<FoundWord>,
    val isClean: Boolean = foundWords.isEmpty()
) {
    val summary: String
        get() = if (isClean) "未检测到敏感词" else "检测到 ${foundWords.size} 个敏感词"
}

/**
 * 检测到的敏感词
 */
data class FoundWord(
    val word: String,
    val startIndex: Int,
    val endIndex: Int,
    val category: String = "通用"
)

/**
 * 敏感词检测器
 * 支持离线检测 + 在线 API 检测
 */
class SensitiveWordChecker(private val context: Context) {

    private var wordSet: Set<String> = emptySet()
    private var isInitialized = false

    /**
     * 初始化离线词库
     */
    suspend fun initialize() {
        if (isInitialized) return

        withContext(Dispatchers.IO) {
            val words = mutableSetOf<String>()
            try {
                context.resources.openRawResource(R.raw.sensitive_words).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                        reader.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .forEach { words.add(it.lowercase()) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            wordSet = words
            isInitialized = true
        }
    }

    /**
     * 离线检测文本中的敏感词
     */
    suspend fun checkOffline(text: String): SensitiveCheckResult {
        if (!isInitialized) {
            initialize()
        }

        return withContext(Dispatchers.Default) {
            val foundWords = mutableListOf<FoundWord>()
            val lowerText = text.lowercase()

            for (word in wordSet) {
                var startIndex = 0
                while (true) {
                    val index = lowerText.indexOf(word, startIndex)
                    if (index == -1) break

                    foundWords.add(
                        FoundWord(
                            word = text.substring(index, index + word.length),
                            startIndex = index,
                            endIndex = index + word.length
                        )
                    )
                    startIndex = index + 1
                }
            }

            // 按位置排序，去重
            val uniqueWords = foundWords
                .distinctBy { "${it.startIndex}-${it.endIndex}" }
                .sortedBy { it.startIndex }

            SensitiveCheckResult(
                originalText = text,
                foundWords = uniqueWords
            )
        }
    }

    /**
     * 在线检测（调用零克查词 API）
     * 注意：这是一个示例实现，实际使用需要注册获取 API
     */
    suspend fun checkOnline(text: String, platform: Platform = Platform.XIAOHONGSHU): OnlineCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                // 零克查词 API 示例
                // 实际使用需要替换为真实的 API 调用
                // val url = "https://www.lingkechaci.com/api/check"

                // 目前返回模拟结果，提示用户使用在线工具
                OnlineCheckResult(
                    success = false,
                    message = "在线检测需要配置 API。建议访问 lingkechaci.com 手动检测",
                    sensitiveWords = emptyList(),
                    platform = platform
                )
            } catch (e: Exception) {
                OnlineCheckResult(
                    success = false,
                    message = "在线检测失败: ${e.message}",
                    sensitiveWords = emptyList(),
                    platform = platform
                )
            }
        }
    }

    /**
     * 综合检测（离线 + 在线）
     */
    suspend fun checkCombined(text: String, platform: Platform = Platform.XIAOHONGSHU): CombinedCheckResult {
        val offlineResult = checkOffline(text)
        val onlineResult = checkOnline(text, platform)

        return CombinedCheckResult(
            originalText = text,
            offlineResult = offlineResult,
            onlineResult = onlineResult
        )
    }

    /**
     * 高亮显示敏感词
     */
    fun highlightSensitiveWords(text: String, foundWords: List<FoundWord>): String {
        if (foundWords.isEmpty()) return text

        val sb = StringBuilder()
        var lastEnd = 0

        for (word in foundWords.sortedBy { it.startIndex }) {
            if (word.startIndex > lastEnd) {
                sb.append(text.substring(lastEnd, word.startIndex))
            }
            sb.append("【${word.word}】")
            lastEnd = word.endIndex
        }

        if (lastEnd < text.length) {
            sb.append(text.substring(lastEnd))
        }

        return sb.toString()
    }

    /**
     * 获取词库大小
     */
    fun getWordCount(): Int = wordSet.size

    /**
     * 获取敏感词集合（用于实时检测）
     */
    fun getWordSet(): Set<String> = wordSet

    /**
     * 添加自定义敏感词
     */
    fun addCustomWord(word: String) {
        wordSet = wordSet + word.lowercase()
    }

    /**
     * 批量添加自定义敏感词
     */
    fun addCustomWords(words: List<String>) {
        wordSet = wordSet + words.map { it.lowercase() }
    }
}

/**
 * 支持的平台
 */
enum class Platform(val displayName: String) {
    XIAOHONGSHU("小红书"),
    DOUYIN("抖音"),
    BILIBILI("B站"),
    WEIBO("微博"),
    WECHAT("微信"),
    GENERAL("通用")
}

/**
 * 在线检测结果
 */
data class OnlineCheckResult(
    val success: Boolean,
    val message: String,
    val sensitiveWords: List<String>,
    val platform: Platform
)

/**
 * 综合检测结果
 */
data class CombinedCheckResult(
    val originalText: String,
    val offlineResult: SensitiveCheckResult,
    val onlineResult: OnlineCheckResult
) {
    val hasIssues: Boolean
        get() = !offlineResult.isClean || (onlineResult.success && onlineResult.sensitiveWords.isNotEmpty())

    val allFoundWords: List<String>
        get() {
            val words = mutableSetOf<String>()
            words.addAll(offlineResult.foundWords.map { it.word })
            words.addAll(onlineResult.sensitiveWords)
            return words.toList()
        }
}

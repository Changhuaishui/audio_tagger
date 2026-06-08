package com.example.tagger.core.smart

import android.content.Context
import com.example.tagger.core.SensitiveWordChecker

/**
 * 智能替换引擎 — 统合四种替换策略
 *
 * 策略优先级：
 * 1. 文本预处理（零宽字符/全角/拆字还原）
 * 2. 语序替换（固定模板）
 * 3. 意会替换（同义词映射）
 * 4. 拼音替换（拼音绕过）
 * 5. 相近字替换（形近字/拆字）
 */
class SmartReplacer(context: Context) {

    private val semanticReplacer = SemanticReplacer(context)
    private val pinyinReplacer = PinyinReplacer(semanticReplacer)
    private val visualReplacer = VisualReplacer(context)
    private val wordOrderReplacer = WordOrderReplacer(context)

    /**
     * 初始化：从 SensitiveWordChecker 加载违禁词构建拼音映射
     */
    fun initialize(sensitiveChecker: SensitiveWordChecker) {
        val words = sensitiveChecker.getWordSet().toList()
        pinyinReplacer.buildDictionary(words)
    }

    /**
     * 替换结果
     */
    data class ReplaceResult(
        val originalText: String,
        val replacedText: String,
        val isChanged: Boolean,
        val appliedStrategy: Strategy,
        val replacements: List<ReplacementRecord>
    )

    /**
     * 替换记录
     */
    data class ReplacementRecord(
        val original: String,
        val replacement: String,
        val strategy: Strategy
    )

    /**
     * 替换策略类型
     */
    enum class Strategy(val displayName: String) {
        NONE("无需替换"),
        SEMANTIC("意会替换"),
        Pinyin("拼音替换"),
        VISUAL("相近字替换"),
        WORD_ORDER("语序替换"),
        DELETE("删除")
    }

    /**
     * 智能替换主入口
     *
     * @param text 原始文本
     * @return 替换结果
     */
    suspend fun replace(text: String, sensitiveChecker: SensitiveWordChecker): ReplaceResult {
        var currentText = text
        val records = mutableListOf<ReplacementRecord>()
        var strategy = Strategy.NONE

        // Step 1: 文本预处理
        currentText = TextNormalizer.normalize(currentText)

        // Step 2: 拆字还原（先还原再检测）
        val resolvedText = visualReplacer.resolve(currentText)
        if (resolvedText != currentText) {
            currentText = resolvedText
        }

        // Step 3: 语序替换（最高优先级，固定模板）
        val orderResult = wordOrderReplacer.searchAndReplace(currentText)
        if (orderResult != null) {
            currentText = currentText.replace(orderResult.first, orderResult.second)
            records.add(ReplacementRecord(orderResult.first, orderResult.second, Strategy.WORD_ORDER))
            strategy = Strategy.WORD_ORDER
            // 替换后继续检测是否还有违禁词
            return continueReplace(currentText, records, strategy, sensitiveChecker)
        }

        // Step 4: 检测违禁词
        val checkResult = sensitiveChecker.checkOffline(currentText)
        if (checkResult.isClean) {
            return ReplaceResult(text, currentText, false, Strategy.NONE, records)
        }

        // Step 5: 逐个处理违禁词
        var maxIterations = 20
        while (maxIterations-- > 0) {
            val result = sensitiveChecker.checkOffline(currentText)
            if (result.isClean) break

            val foundWord = result.foundWords.firstOrNull() ?: break
            val word = foundWord.word

            // 5.1 尝试意会替换
            val semanticReplacement = semanticReplacer.getReplacement(word)
            if (semanticReplacement != null) {
                currentText = currentText.replace(word, semanticReplacement, ignoreCase = true)
                records.add(ReplacementRecord(word, semanticReplacement, Strategy.SEMANTIC))
                strategy = Strategy.SEMANTIC
                continue
            }

            // 5.2 尝试拼音替换（如果文本包含字母）
            val pinyinResult = pinyinReplacer.searchAndReplace(currentText)
            if (pinyinResult != null) {
                currentText = currentText.replace(pinyinResult.first, pinyinResult.second, ignoreCase = true)
                records.add(ReplacementRecord(pinyinResult.first, pinyinResult.second, Strategy.Pinyin))
                strategy = Strategy.Pinyin
                continue
            }

            // 5.3 无法替换，使用删除
            currentText = currentText.replace(word, "", ignoreCase = true)
            records.add(ReplacementRecord(word, "", Strategy.DELETE))
            strategy = Strategy.DELETE
        }

        return ReplaceResult(text, currentText.trim(), records.isNotEmpty(), strategy, records)
    }

    /**
     * 语序替换后继续处理
     */
    private suspend fun continueReplace(
        text: String,
        records: MutableList<ReplacementRecord>,
        currentStrategy: Strategy,
        sensitiveChecker: SensitiveWordChecker
    ): ReplaceResult {
        var currentText = text
        var strategy = currentStrategy

        var maxIterations = 20
        while (maxIterations-- > 0) {
            val result = sensitiveChecker.checkOffline(currentText)
            if (result.isClean) break

            val foundWord = result.foundWords.firstOrNull() ?: break
            val word = foundWord.word

            val semanticReplacement = semanticReplacer.getReplacement(word)
            if (semanticReplacement != null) {
                currentText = currentText.replace(word, semanticReplacement, ignoreCase = true)
                records.add(ReplacementRecord(word, semanticReplacement, Strategy.SEMANTIC))
                strategy = Strategy.SEMANTIC
                continue
            }

            currentText = currentText.replace(word, "", ignoreCase = true)
            records.add(ReplacementRecord(word, "", Strategy.DELETE))
            strategy = Strategy.DELETE
        }

        return ReplaceResult(text, currentText.trim(), records.isNotEmpty(), strategy, records)
    }

    /**
     * 快速获取替换建议（用于 UI 显示）
     * @return 违禁词 -> 建议替换词 的映射
     */
    suspend fun getSuggestions(text: String, sensitiveChecker: SensitiveWordChecker): Map<String, String> {
        val suggestions = mutableMapOf<String, String>()

        // 预处理
        var processed = TextNormalizer.normalize(text)
        processed = visualReplacer.resolve(processed)

        // 语序替换建议
        val orderResult = wordOrderReplacer.searchAndReplace(processed)
        if (orderResult != null) {
            suggestions[orderResult.first] = "${orderResult.second}（语序替换）"
        }

        // 检测违禁词并给出建议
        val checkResult = sensitiveChecker.checkOffline(processed)
        for (foundWord in checkResult.foundWords) {
            val word = foundWord.word
            if (suggestions.containsKey(word)) continue

            // 意会替换
            val semantic = semanticReplacer.getReplacement(word)
            if (semantic != null) {
                suggestions[word] = "$semantic（意会替换）"
                continue
            }

            // 拼音替换检查
            val pinyinResult = pinyinReplacer.searchAndReplace(processed)
            if (pinyinResult != null && processed.contains(word)) {
                suggestions[pinyinResult.first] = "${pinyinResult.second}（拼音替换）"
                continue
            }

            // 默认建议：删除
            suggestions[word] = "删除"
        }

        return suggestions
    }
}

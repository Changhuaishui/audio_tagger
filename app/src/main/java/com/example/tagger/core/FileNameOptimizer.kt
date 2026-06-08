package com.example.tagger.core

import com.example.tagger.core.smart.SmartReplacer

/**
 * 文件名违禁词优化器
 *
 * 优化策略（按优先级）:
 * 1. 智能替换: 意会/拼音/相近字/语序
 * 2. 字符对调: "敏感" → "感敏"
 * 3. 插入分隔符: "敏感" → "敏_感"
 * 4. 删除违禁词: "敏感" → ""
 */
class FileNameOptimizer(
    private val sensitiveChecker: SensitiveWordChecker,
    private val smartReplacer: SmartReplacer? = null
) {

    /**
     * 优化结果
     */
    data class OptimizeResult(
        val originalName: String,
        val optimizedName: String,
        val appliedStrategies: List<AppliedStrategy>,
        val isChanged: Boolean = originalName != optimizedName
    )

    /**
     * 应用的策略记录
     */
    data class AppliedStrategy(
        val word: String,
        val strategy: Strategy,
        val before: String,
        val after: String
    )

    /**
     * 优化策略
     */
    enum class Strategy(val displayName: String) {
        SWAP("字符对调"),
        SEPARATOR("插入分隔符"),
        SMART_REPLACE("智能替换"),
        // HOMOPHONE("同音替换"),  // TODO: 需要同音词库
        DELETE("删除")
    }

    // 可用的分隔符列表
    private val separators = listOf("_", "·", "-", " ")

    /**
     * 优化文件名中的违禁词
     *
     * @param fileName 原始文件名（不含扩展名）
     * @return 优化结果
     */
    suspend fun optimize(fileName: String): OptimizeResult {
        val appliedStrategies = mutableListOf<AppliedStrategy>()
        var currentName = fileName

        // 循环处理，直到没有违禁词或无法继续优化
        var maxIterations = 10  // 防止无限循环
        while (maxIterations-- > 0) {
            val checkResult = sensitiveChecker.checkOffline(currentName)
            if (checkResult.isClean) break

            // 获取第一个违禁词进行处理
            val foundWord = checkResult.foundWords.firstOrNull() ?: break
            val word = foundWord.word

            // 尝试各种策略
            val (newName, strategy) = tryOptimizeWord(currentName, word)

            if (newName == currentName) {
                // 无法优化这个词，跳过（理论上不应该发生，因为最后会删除）
                break
            }

            appliedStrategies.add(
                AppliedStrategy(
                    word = word,
                    strategy = strategy,
                    before = currentName,
                    after = newName
                )
            )
            currentName = newName
        }

        return OptimizeResult(
            originalName = fileName,
            optimizedName = currentName.trim(),
            appliedStrategies = appliedStrategies
        )
    }

    /**
     * 尝试用各种策略优化单个违禁词
     */
    private suspend fun tryOptimizeWord(text: String, word: String): Pair<String, Strategy> {
        // 策略0: 智能替换（最高优先级）
        smartReplacer?.let { replacer ->
            val result = replacer.replace(text, sensitiveChecker)
            if (result.isChanged && result.replacedText != text) {
                return result.replacedText to Strategy.SMART_REPLACE
            }
        }

        // 策略1: 字符对调（仅对2字及以上的词有效）
        if (word.length >= 2) {
            val swapped = swapCharacters(word)
            val newText = text.replace(word, swapped, ignoreCase = true)
            val checkResult = sensitiveChecker.checkOffline(newText)
            // 检查对调后的词是否仍是违禁词
            val swappedStillSensitive = checkResult.foundWords.any {
                it.word.equals(swapped, ignoreCase = true)
            }
            if (!swappedStillSensitive) {
                return newText to Strategy.SWAP
            }
        }

        // 策略2: 插入分隔符
        for (separator in separators) {
            val separated = insertSeparator(word, separator)
            val newText = text.replace(word, separated, ignoreCase = true)
            val checkResult = sensitiveChecker.checkOffline(newText)
            // 检查分隔后是否仍包含原违禁词
            val stillContainsWord = checkResult.foundWords.any {
                it.word.equals(word, ignoreCase = true)
            }
            if (!stillContainsWord) {
                return newText to Strategy.SEPARATOR
            }
        }

        // TODO: 策略3: 同音词替换
        // 需要同音词库支持，暂时跳过

        // 策略4: 删除违禁词
        val deleted = text.replace(word, "", ignoreCase = true)
        return deleted to Strategy.DELETE
    }

    /**
     * 字符对调
     * "敏感" → "感敏"
     * "绿茶婊" → "婊茶绿"
     */
    private fun swapCharacters(word: String): String {
        return word.reversed()
    }

    /**
     * 插入分隔符
     * "敏感" → "敏_感"
     */
    private fun insertSeparator(word: String, separator: String): String {
        return word.toList().joinToString(separator)
    }

    /**
     * 批量优化多个文件名
     */
    suspend fun optimizeBatch(fileNames: List<String>): List<OptimizeResult> {
        return fileNames.map { optimize(it) }
    }

    /**
     * 纯删除文件名中的违禁词（不做字符对调、不插入分隔符）
     *
     * @param fileName 完整文件名（含扩展名）
     * @return 优化结果
     */
    suspend fun removeSensitiveWords(fileName: String): OptimizeResult {
        val lastDotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
        val extension = if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1)
        } else ""

        var currentName = nameWithoutExt
        val appliedStrategies = mutableListOf<AppliedStrategy>()

        // 循环删除所有命中的违禁词
        var maxIterations = 50  // 防止无限循环
        while (maxIterations-- > 0) {
            val checkResult = sensitiveChecker.checkOffline(currentName)
            if (checkResult.isClean) break

            val foundWord = checkResult.foundWords.firstOrNull() ?: break
            val word = foundWord.word
            val newName = currentName.replace(word, "", ignoreCase = true)

            if (newName == currentName) break

            appliedStrategies.add(
                AppliedStrategy(
                    word = word,
                    strategy = Strategy.DELETE,
                    before = currentName,
                    after = newName
                )
            )
            currentName = newName
        }

        // 清理多余空白和连续分隔符
        var cleanedName = currentName
            .replace(Regex("[ ]{2,}"), " ")       // 连续空格压缩为一个空格
            .replace(Regex("[_]{2,}"), "_")       // 连续下划线压缩
            .replace(Regex("[-]{2,}"), "-")       // 连续短横线压缩
            .replace(Regex("[·]{2,}"), "·")       // 连续中点压缩
            .replace(Regex("[ ]+"), " ")          // 再次清理空格
            .trim()

        // 清理首尾分隔符
        cleanedName = cleanedName.trimStart('_', '-', '·', ' ')
            .trimEnd('_', '-', '·', ' ')

        // 如果删除后主体为空，保持原文件名（返回未改变的结果）
        val finalNameWithoutExt = if (cleanedName.isEmpty()) nameWithoutExt else cleanedName
        val isChanged = finalNameWithoutExt != nameWithoutExt

        val finalFileName = if (extension.isNotEmpty()) {
            "$finalNameWithoutExt.$extension"
        } else {
            finalNameWithoutExt
        }

        return OptimizeResult(
            originalName = fileName,
            optimizedName = finalFileName,
            appliedStrategies = appliedStrategies,
            isChanged = isChanged
        )
    }

    /**
     * 智能替换文件名中的违禁词（意会/拼音/相近字/语序）
     *
     * @param fileName 完整文件名（含扩展名）
     * @return 替换结果
     */
    suspend fun smartReplace(fileName: String): OptimizeResult {
        val replacer = smartReplacer
            ?: return removeSensitiveWords(fileName)

        val lastDotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
        val extension = if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1)
        } else ""

        val result = replacer.replace(nameWithoutExt, sensitiveChecker)

        // 清理多余空白和连续分隔符
        var cleanedName = result.replacedText
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex("[_]{2,}"), "_")
            .replace(Regex("[-]{2,}"), "-")
            .replace(Regex("[·]{2,}"), "·")
            .replace(Regex("[ ]+"), " ")
            .trim()

        cleanedName = cleanedName.trimStart('_', '-', '·', ' ')
            .trimEnd('_', '-', '·', ' ')

        // 如果替换后为空，保持原文件名
        val finalNameWithoutExt = if (cleanedName.isEmpty()) nameWithoutExt else cleanedName
        val isChanged = finalNameWithoutExt != nameWithoutExt || result.isChanged

        val finalFileName = if (extension.isNotEmpty()) {
            "$finalNameWithoutExt.$extension"
        } else {
            finalNameWithoutExt
        }

        val appliedStrategies = result.replacements.map { record ->
            AppliedStrategy(
                word = record.original,
                strategy = when (record.strategy) {
                    SmartReplacer.Strategy.SEMANTIC -> Strategy.SMART_REPLACE
                    SmartReplacer.Strategy.Pinyin -> Strategy.SMART_REPLACE
                    SmartReplacer.Strategy.VISUAL -> Strategy.SMART_REPLACE
                    SmartReplacer.Strategy.WORD_ORDER -> Strategy.SMART_REPLACE
                    else -> Strategy.DELETE
                },
                before = record.original,
                after = record.replacement
            )
        }

        return OptimizeResult(
            originalName = fileName,
            optimizedName = finalFileName,
            appliedStrategies = appliedStrategies,
            isChanged = isChanged
        )
    }
}

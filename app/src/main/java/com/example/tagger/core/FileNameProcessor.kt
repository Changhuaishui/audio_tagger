package com.example.tagger.core

import android.util.Log
import com.example.tagger.data.UserPreferencesRepository
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.ObfuscationMapping
import com.example.tagger.model.ObfuscationMode
import com.example.tagger.model.ReplacementRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val TAG = "FileNameProcessor"

/**
 * 替换处理结果
 */
data class ReplacementResult(
    val originalName: String,
    val processedName: String,
    val appliedRules: List<ReplacementRule>,
    val isChanged: Boolean
)

/**
 * 混淆处理结果
 */
data class ObfuscationResult(
    val originalName: String,
    val obfuscatedName: String,
    val mapping: ObfuscationMapping?
)

/**
 * 文件名处理器 - 处理自定义替换规则和混淆功能
 */
class FileNameProcessor(private val repository: UserPreferencesRepository) {

    /**
     * 应用自定义替换规则到文件名
     *
     * @param fileName 原始文件名（不含扩展名）
     * @param rules 要应用的规则列表，为空则使用存储的规则
     * @return 处理结果
     */
    suspend fun applyReplacementRules(
        fileName: String,
        rules: List<ReplacementRule>? = null
    ): ReplacementResult {
        val rulesToApply = rules ?: repository.getReplacementRules()
        val enabledRules = rulesToApply.filter { it.isEnabled }

        if (enabledRules.isEmpty()) {
            return ReplacementResult(
                originalName = fileName,
                processedName = fileName,
                appliedRules = emptyList(),
                isChanged = false
            )
        }

        var processedName = fileName
        val appliedRules = mutableListOf<ReplacementRule>()

        for (rule in enabledRules) {
            if (processedName.contains(rule.findText)) {
                processedName = processedName.replace(rule.findText, rule.replaceWith)
                appliedRules.add(rule)
                Log.d(TAG, "Applied rule: '${rule.findText}' -> '${rule.replaceWith}'")
            }
        }

        return ReplacementResult(
            originalName = fileName,
            processedName = processedName,
            appliedRules = appliedRules,
            isChanged = processedName != fileName
        )
    }

    /**
     * 生成混淆文件名
     *
     * @param mode 混淆模式
     * @param extension 文件扩展名（不含点号）
     * @param metadata 原始音频元数据（用于创建映射）
     * @param saveMapping 是否保存映射关系
     * @return 混淆结果
     */
    suspend fun generateObfuscatedName(
        mode: ObfuscationMode,
        extension: String,
        metadata: AudioMetadata,
        saveMapping: Boolean = true
    ): ObfuscationResult {
        val baseName = when (mode) {
            ObfuscationMode.NUMBERED -> generateNumberedName()
            ObfuscationMode.RANDOM -> generateRandomName()
            ObfuscationMode.DATE -> generateDateName()
        }

        val obfuscatedName = if (extension.isNotEmpty()) {
            "$baseName.$extension"
        } else {
            baseName
        }

        val mapping = if (saveMapping) {
            ObfuscationMapping(
                obfuscatedName = obfuscatedName,
                originalName = metadata.displayName,
                originalTitle = metadata.title,
                originalArtist = metadata.artist
            ).also {
                repository.addObfuscationMapping(it)
            }
        } else {
            null
        }

        Log.d(TAG, "Generated obfuscated name: ${metadata.displayName} -> $obfuscatedName")

        return ObfuscationResult(
            originalName = metadata.displayName,
            obfuscatedName = obfuscatedName,
            mapping = mapping
        )
    }

    /**
     * 生成编号模式文件名: Track_001
     */
    private suspend fun generateNumberedName(): String {
        val counter = repository.getAndIncrementCounter()
        return "Track_%03d".format(counter)
    }

    /**
     * 生成随机模式文件名: a7f3k9m2 (8个字符的随机字母数字)
     */
    private fun generateRandomName(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * 生成日期模式文件名: audio_20240128_143052
     */
    private fun generateDateName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "audio_${dateFormat.format(Date())}"
    }

    /**
     * 处理单个文件 - 应用替换规则和/或混淆
     *
     * @param metadata 音频文件元数据
     * @param useReplacement 是否应用替换规则
     * @param useObfuscation 是否应用混淆
     * @param obfuscationMode 混淆模式（如果使用混淆）
     * @param saveMapping 是否保存映射
     * @return 新文件名
     */
    suspend fun processFileName(
        metadata: AudioMetadata,
        useReplacement: Boolean,
        useObfuscation: Boolean,
        obfuscationMode: ObfuscationMode?,
        saveMapping: Boolean = true
    ): String {
        val nameWithoutExt = metadata.displayName.substringBeforeLast('.', metadata.displayName)
        val extension = metadata.displayName.substringAfterLast('.', "")

        var processedName = nameWithoutExt

        // 先应用替换规则
        if (useReplacement) {
            val result = applyReplacementRules(processedName)
            processedName = result.processedName
        }

        // 再应用混淆
        if (useObfuscation && obfuscationMode != null) {
            val result = generateObfuscatedName(
                mode = obfuscationMode,
                extension = extension,
                metadata = metadata.copy(
                    displayName = if (extension.isNotEmpty()) "$processedName.$extension" else processedName
                ),
                saveMapping = saveMapping
            )
            return result.obfuscatedName
        }

        // 只有替换，重新组合扩展名
        return if (extension.isNotEmpty()) {
            "$processedName.$extension"
        } else {
            processedName
        }
    }

    /**
     * 重置编号计数器
     */
    suspend fun resetNumberedCounter() {
        repository.resetCounter()
    }

    /**
     * 获取所有替换规则
     */
    suspend fun getReplacementRules(): List<ReplacementRule> {
        return repository.getReplacementRules()
    }
}

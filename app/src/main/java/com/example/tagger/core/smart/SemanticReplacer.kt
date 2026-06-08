package com.example.tagger.core.smart

import android.content.Context
import com.example.tagger.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 意会替换引擎 — 同义词/近义词映射
 *
 * 格式：违禁词=替换词
 */
class SemanticReplacer(context: Context) {

    private val semanticMap: Map<String, String>

    init {
        semanticMap = loadFromRaw(context)
    }

    private fun loadFromRaw(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            context.resources.openRawResource(R.raw.semantic_words).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                map[parts[0].trim().lowercase()] = parts[1].trim()
                            }
                        }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    /**
     * 查找替换词
     * @return 替换词，无映射返回 null
     */
    fun getReplacement(word: String): String? {
        return semanticMap[word.lowercase()]
    }

    /**
     * 检查是否有映射
     */
    fun hasReplacement(word: String): Boolean {
        return semanticMap.containsKey(word.lowercase())
    }
}

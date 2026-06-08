package com.example.tagger.core.smart

import android.content.Context
import com.example.tagger.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 相近字替换引擎
 *
 * 处理拆字绕过和形近字替换：
 * - 拆字：女表 -> 婊
 * - 形近字：艹 -> 草
 */
class VisualReplacer(context: Context) {

    private val visualMap: Map<String, String>

    init {
        visualMap = loadFromRaw(context)
    }

    private fun loadFromRaw(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            context.resources.openRawResource(R.raw.visual_variant).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                map[parts[0].trim()] = parts[1].trim()
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
     * 还原文本中的拆字/形近字
     */
    fun resolve(text: String): String {
        return TextNormalizer.resolveSplitChars(text, visualMap)
    }

    /**
     * 获取拆字映射表（供 TextNormalizer 使用）
     */
    fun getSplitMap(): Map<String, String> = visualMap
}

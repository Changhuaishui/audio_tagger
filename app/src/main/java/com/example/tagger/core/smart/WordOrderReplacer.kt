package com.example.tagger.core.smart

import android.content.Context
import com.example.tagger.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 语序替换引擎
 *
 * 处理固定语序的违禁表达：
 * 杀了你 -> 教训你
 * 打死你 -> 教训你
 */
class WordOrderReplacer(context: Context) {

    private val orderMap: Map<String, String>

    init {
        orderMap = loadFromRaw(context)
    }

    private fun loadFromRaw(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            context.resources.openRawResource(R.raw.word_order).use { inputStream ->
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
     * 在文本中搜索语序模板并替换
     * @return Pair<匹配到的原文, 替换词>，无匹配返回 null
     */
    fun searchAndReplace(text: String): Pair<String, String>? {
        for ((pattern, replacement) in orderMap) {
            if (text.contains(pattern)) {
                return Pair(pattern, replacement)
            }
        }
        return null
    }
}

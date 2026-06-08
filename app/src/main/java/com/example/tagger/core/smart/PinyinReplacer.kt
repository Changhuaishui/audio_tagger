package com.example.tagger.core.smart

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 拼音替换引擎
 *
 * 检测拼音绕过：shabi -> 傻逼 -> 傻瓜
 * 支持全拼和首字母缩写
 */
class PinyinReplacer(private val semanticReplacer: SemanticReplacer) {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    // 缓存：拼音 -> 原始汉字词
    private val pinyinToWord = mutableMapOf<String, String>()
    // 缓存：首字母缩写 -> 原始汉字词
    private val acronymToWord = mutableMapOf<String, String>()

    /**
     * 从敏感词列表构建拼音映射
     */
    fun buildDictionary(forbiddenWords: List<String>) {
        pinyinToWord.clear()
        acronymToWord.clear()

        for (word in forbiddenWords) {
            val pinyin = toPinyin(word)
            if (pinyin.isNotEmpty()) {
                pinyinToWord[pinyin] = word
            }
            val acronym = toAcronym(word)
            if (acronym.length >= 2) {
                acronymToWord[acronym] = word
            }
        }
    }

    /**
     * 在文本中搜索拼音形式的违禁词，返回替换建议
     * @return Pair<匹配到的拼音, 替换词>，无匹配返回 null
     */
    fun searchAndReplace(text: String): Pair<String, String>? {
        // 提取连续字母序列
        val letterPattern = Regex("[a-zA-Z]{2,}")
        for (match in letterPattern.findAll(text)) {
            val letters = match.value.lowercase()

            // 尝试全拼匹配
            val originalWord = pinyinToWord[letters]
            if (originalWord != null) {
                val replacement = semanticReplacer.getReplacement(originalWord)
                if (replacement != null) {
                    return Pair(match.value, replacement)
                }
            }

            // 尝试首字母缩写匹配
            val acronymWord = acronymToWord[letters]
            if (acronymWord != null) {
                val replacement = semanticReplacer.getReplacement(acronymWord)
                if (replacement != null) {
                    return Pair(match.value, replacement)
                }
            }
        }
        return null
    }

    /**
     * 汉字转拼音（无空格）
     */
    private fun toPinyin(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            if (c in '\u4E00'..'\u9FA5') {
                try {
                    val arr = PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat)
                    if (arr != null && arr.isNotEmpty()) {
                        sb.append(arr[0])
                    }
                } catch (_: Exception) {}
            } else if (c.isLetter()) {
                sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * 获取拼音首字母缩写
     */
    private fun toAcronym(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            if (c in '\u4E00'..'\u9FA5') {
                try {
                    val arr = PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat)
                    if (arr != null && arr.isNotEmpty()) {
                        sb.append(arr[0].first())
                    }
                } catch (_: Exception) {}
            } else if (c.isLetter()) {
                sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }
}

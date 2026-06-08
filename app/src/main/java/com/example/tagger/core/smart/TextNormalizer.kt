package com.example.tagger.core.smart

/**
 * 文本预处理标准化器
 *
 * 处理各种绕过手段：
 * - 零宽字符过滤
 * - 全角字符转半角
 * - 重复字符压缩
 * - 拆字还原（基于 visual_variant.txt）
 */
object TextNormalizer {

    // 零宽字符集
    private val ZERO_WIDTH_CHARS = setOf(
        '\u200B', '\u200C', '\u200D', '\uFEFF', '\u2060',
        '\u180E', '\u200E', '\u200F', '\u202A', '\u202B',
        '\u202C', '\u202D', '\u202E'
    )

    // 全角 -> 半角映射
    private fun fullwidthToHalfwidth(c: Char): Char {
        return when (c) {
            in '\uFF01'..'\uFF5E' -> (c.code - 0xFEE0).toChar()
            '\u3000' -> ' '
            '\uFF0E' -> '.'
            '\uFF0F' -> '/'
            in '\uFF10'..'\uFF19' -> (c.code - 0xFEE0).toChar()
            in '\uFF21'..'\uFF3A' -> (c.code - 0xFEE0).toChar()
            in '\uFF41'..'\uFF5A' -> (c.code - 0xFEE0).toChar()
            else -> c
        }
    }

    /**
     * 主预处理入口
     */
    fun normalize(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            if (c in ZERO_WIDTH_CHARS) continue
            sb.append(fullwidthToHalfwidth(c))
        }
        return sb.toString()
    }

    /**
     * 拆字还原
     * 基于预置的拆字映射表，滑动窗口匹配还原
     */
    fun resolveSplitChars(input: String, splitMap: Map<String, String>): String {
        if (input.length < 4) return input

        val result = StringBuilder()
        var i = 0
        val maxWindow = 4

        while (i < input.length) {
            var matched = false
            val maxW = minOf(maxWindow, input.length - i)
            for (windowSize in maxW downTo 2) {
                val substring = input.substring(i, i + windowSize)
                val resolved = splitMap[substring]
                if (resolved != null) {
                    result.append(resolved)
                    i += windowSize
                    matched = true
                    break
                }
            }
            if (!matched) {
                result.append(input[i])
                i++
            }
        }
        return result.toString()
    }
}

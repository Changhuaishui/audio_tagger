package com.example.tagger.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlin.math.max

/**
 * 从封面 Bitmap 提取动态主题色
 * 参考 Material You 取色逻辑，自研简化版
 */
object CoverArtPaletteExtractor {

    /**
     * 提取后的主题色包
     */
    data class PlayerColors(
        val backgroundColor: Color,
        val onBackgroundColor: Color,
        val accentColor: Color,
        val mutedColor: Color
    )

    /**
     * 从 Bitmap 提取主题色
     */
    fun extract(bitmap: Bitmap?): PlayerColors {
        if (bitmap == null) {
            return defaultDarkColors()
        }

        val palette = Palette.from(bitmap).generate()

        // 背景色：取 dominant，偏暗
        val background = palette.dominantSwatch?.rgb?.let { darken(it, 0.6f) }
            ?: android.graphics.Color.DKGRAY

        // 强调色：优先 vibrant，其次 lightVibrant / muted
        val accent = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: android.graphics.Color.WHITE

        // 次要色
        val muted = palette.mutedSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: android.graphics.Color.GRAY

        // 判断背景亮度决定文字色
        val bgColor = Color(background)
        val onBgColor = if (isLight(background)) Color.Black else Color.White

        return PlayerColors(
            backgroundColor = bgColor,
            onBackgroundColor = onBgColor,
            accentColor = Color(accent),
            mutedColor = Color(muted)
        )
    }

    /**
     * 默认暗色主题（无封面时）
     */
    fun defaultDarkColors(): PlayerColors = PlayerColors(
        backgroundColor = Color(0xFF121212),
        onBackgroundColor = Color.White,
        accentColor = Color(0xFFFC3C44), // Apple Music 红 fallback
        mutedColor = Color.Gray
    )

    private fun darken(color: Int, factor: Float): Int {
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * factor).toInt()
        val g = (android.graphics.Color.green(color) * factor).toInt()
        val b = (android.graphics.Color.blue(color) * factor).toInt()
        return android.graphics.Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun isLight(color: Int): Boolean {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return luminance > 0.5
    }
}

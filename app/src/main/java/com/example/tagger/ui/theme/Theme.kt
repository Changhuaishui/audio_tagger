package com.example.tagger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// Apple Music 风格主题配置
// 禁用动态颜色，保持统一红色风格
// ============================================

private val LightColorScheme = lightColorScheme(
    // 主色调 - Apple Music 红
    primary = AppPrimaryColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E5),
    onPrimaryContainer = AppPrimaryColor,

    // 次要色调
    secondary = AppleGray1,
    onSecondary = Color.White,
    secondaryContainer = AppleGray6,
    onSecondaryContainer = AppleGray1,

    // 第三色调
    tertiary = AppleTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F7FA),
    onTertiaryContainer = AppleTeal,

    // 错误色
    error = AppleRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = AppleRed,

    // 背景
    background = BackgroundLight,
    onBackground = TextPrimaryLight,

    // 表面
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = AppleGray6,
    onSurfaceVariant = AppleGray1,

    // 轮廓
    outline = AppleGray4,
    outlineVariant = AppleGray5,

    // 其他
    inverseSurface = AppleGrayDark6,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = AppPrimaryColorDark,
    surfaceTint = AppPrimaryColor
)

private val DarkColorScheme = darkColorScheme(
    // 主色调 - Apple Music 红 (深色模式)
    primary = AppPrimaryColorDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C0011),
    onPrimaryContainer = Color(0xFFFFDAD6),

    // 次要色调
    secondary = AppleGrayDark1,
    onSecondary = Color.White,
    secondaryContainer = AppleGrayDark5,
    onSecondaryContainer = AppleGrayDark1,

    // 第三色调
    tertiary = AppleTeal,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF004D5B),
    onTertiaryContainer = Color(0xFFB4EFF4),

    // 错误色
    error = AppleRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // 背景
    background = BackgroundDark,
    onBackground = TextPrimaryDark,

    // 表面
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = AppleGrayDark5,
    onSurfaceVariant = AppleGrayDark1,

    // 轮廓
    outline = AppleGrayDark3,
    outlineVariant = AppleGrayDark4,

    // 其他
    inverseSurface = AppleGray6,
    inverseOnSurface = TextPrimaryLight,
    inversePrimary = AppPrimaryColor,
    surfaceTint = AppPrimaryColorDark
)

@Composable
fun AudioTaggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 设置状态栏样式
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

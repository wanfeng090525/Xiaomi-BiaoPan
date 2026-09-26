package com.watchface.idtool.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchface.idtool.R

// ====================================================================
// 保时捷风格字体（Porsche-style Typeface · 全语言覆盖）
//
// 直接使用字体原生的字重渲染，不做任何描边/轮廓加厚：
//   · Latin/数字 = Barlow（德系 DIN 工程字体，保时捷仪表同源）
//   · 中文 = 几何黑体常规字重（GB2312 全字库内嵌）
// 全局排版仅使用 Normal / Medium 两档，层级靠字号与字距区分，
// 还原保时捷仪表「轻薄锐利」的原生字形。
// ====================================================================

val AppFonts = FontFamily(
    Font(R.font.barlow_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_medium, weight = FontWeight.Medium),
    // 这里字体改过
    Font(R.font.barlow_medium, weight = FontWeight.SemiBold),
    Font(R.font.barlow_regular, weight = FontWeight.Bold)
)

/** 数字/ID 专用：窄长机械数字（表盘 ID、版本号、计数器），常规字重直接渲染 */
val NumericFonts = FontFamily(
    Font(R.font.barlow_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_medium, weight = FontWeight.Medium)
)

// ====================================================================
// 深色玻璃配色（单色系 · 玻璃容器 + 实心图标，无彩色按钮规格）
// ====================================================================

private val LiquidDarkColors = darkColorScheme(
    primary = Color(0xFFE9EBF4),
    onPrimary = Color(0xFF161926),
    primaryContainer = Color(0xFF2A2E3E),
    onPrimaryContainer = Color(0xFFE9EBF4),
    secondary = Color(0xFFD9DEEB),
    onSecondary = Color(0xFF161926),
    secondaryContainer = Color(0xFF262A38),
    onSecondaryContainer = Color(0xFFD9DEEB),
    tertiary = Color(0xFFE9EBF4),
    onTertiary = Color(0xFF161926),
    tertiaryContainer = Color(0xFF222633),
    onTertiaryContainer = Color(0xFFE9EBF4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF3A0A08),
    errorContainer = Color(0xFF521B16),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF0D0F16),
    onBackground = Color(0xFFF2F3F8),
    surface = Color(0xFF171A24),
    onSurface = Color(0xFFEDEFF5),
    surfaceVariant = Color(0xFF232734),
    onSurfaceVariant = Color(0xFF9CA2B5),
    outline = Color(0xFF565C72),
    outlineVariant = Color(0xFF333849)
)

/**
 * 玻璃材质调色板 —— 深色基底（ColorOS 控制中心规格）：
 *   - 玻璃填充 8~15% 白，半透明磨砂
 *   - 顶部菲涅尔亮缘 + 非对称左上/右下描边
 *   - 无投影阴影，靠边缘高光分离层次
 *   - 激活态提亮填充（30%+ 白）
 */
object GlassPalette {

    /** 玻璃面板底色（iOS 27 液态玻璃：更高透亮、更富体积感） */
    val glassTintTop = Color.White.copy(alpha = 0.16f)
    val glassTintBottom = Color.White.copy(alpha = 0.07f)

    /** 激活态玻璃 */
    val glassActiveTintTop = Color.White.copy(alpha = 0.38f)
    val glassActiveTintBottom = Color.White.copy(alpha = 0.20f)

    /** 顶部液态光泽 */
    val highlight = Color.White.copy(alpha = 0.36f)

    /** 菲涅尔边缘：左上亮缘 / 右下暗缘 */
    val rimBright = Color.White.copy(alpha = 0.46f)
    val rimDim = Color.Black.copy(alpha = 0.30f)

    /** 兼容旧 API */
    val glassTintLightTop = glassTintTop
    val glassTintLightBottom = glassTintBottom
    val glassTintDarkTop = glassTintTop
    val glassTintDarkBottom = glassTintBottom
    val highlightLight = highlight
    val highlightDark = highlight
    val rimLight = rimBright
    val rimDark = rimBright

    /** 屏幕基底：中性深灰黑渐变（无色相，纯黑玻璃底衬） */
    val bgGradient = listOf(Color(0xFF08090C), Color(0xFF101216), Color(0xFF16181D))
    val bgGradientLight = bgGradient
    val bgGradientDark = bgGradient
}

// ====================================================================
// 全局排版（Barlow 原生字重 · 无加厚）
// 仅 Normal / Medium 两档：层级由字号与字距表达，字形保持轻薄锐利。
// ====================================================================

private fun liquidTextStyle(
    weight: FontWeight,
    size: Int,
    line: Int,
    spacing: Float = 0f
) = TextStyle(
    fontFamily = AppFonts,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

private val LiquidTypography = Typography(
    displayLarge = liquidTextStyle(FontWeight.Medium, 34, 40, 0.2f),
    displayMedium = liquidTextStyle(FontWeight.Medium, 30, 36, 0.2f),
    displaySmall = liquidTextStyle(FontWeight.Medium, 26, 32, 0.2f),
    headlineLarge = liquidTextStyle(FontWeight.Medium, 24, 30),
    headlineMedium = liquidTextStyle(FontWeight.Medium, 21, 27),
    headlineSmall = liquidTextStyle(FontWeight.Medium, 18, 24),
    titleLarge = liquidTextStyle(FontWeight.Medium, 17, 23),
    titleMedium = liquidTextStyle(FontWeight.Medium, 15, 21),
    titleSmall = liquidTextStyle(FontWeight.Normal, 13, 18),
    bodyLarge = liquidTextStyle(FontWeight.Normal, 15, 22),
    bodyMedium = liquidTextStyle(FontWeight.Normal, 13, 19),
    bodySmall = liquidTextStyle(FontWeight.Normal, 11, 16),
    labelLarge = liquidTextStyle(FontWeight.Medium, 14, 20, 0.3f),
    labelMedium = liquidTextStyle(FontWeight.Normal, 12, 16, 0.3f),
    labelSmall = liquidTextStyle(FontWeight.Normal, 10, 14, 0.4f)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/**
 * 深色玻璃主题（参照 ColorOS 控制中心，固定深色基底）。
 */
@Composable
fun WatchFaceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiquidDarkColors,
        typography = LiquidTypography,
        shapes = AppShapes,
        content = content
    )
}

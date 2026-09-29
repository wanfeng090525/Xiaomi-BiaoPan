package com.watchface.idtool.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
// 字体（保留原 Barlow / 几何黑体，全语言覆盖）
// ====================================================================

val AppFonts = FontFamily(
    Font(R.font.barlow_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_medium, weight = FontWeight.Medium),
    Font(R.font.barlow_medium, weight = FontWeight.SemiBold),
    Font(R.font.barlow_regular, weight = FontWeight.Bold)
)

/** 数字/ID 专用：窄长机械数字（表盘 ID、版本号、计数器） */
val NumericFonts = FontFamily(
    Font(R.font.barlow_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_medium, weight = FontWeight.Medium)
)

// ====================================================================
// iOS 设计令牌（Design Tokens）
//
// 层级模型（从下到上）：
//   groupedBackground  #F2F2F7  页面底色
//   card               #FFFFFF  InsetGrouped 卡片
//   separator          #E5E5EA  1px 项间分割线
//   label              #000000  主文本
//   secondaryLabel     #8E8E93  次级文本 / 数值
//   tint               #007AFF  高亮（链接、开关、选中）
//   capsuleSelected    #E5F0FF  TabBar 选中胶囊底
// ====================================================================

object IOSPalette {

    // ---- 层级底色 ----
    /** 页面底色（iOS grouped 背景） */
    val groupedBackground = Color(0xFFF2F2F7)

    /** 卡片底色 */
    val card = Color(0xFFFFFFFF)

    /** 次级卡片 / 嵌插槽位底（如输入框、代码块） */
    val cardSecondary = Color(0xFFF9F9FB)

    /** TabBar 胶囊底 */
    val tabBar = Color(0xFFFFFFFF)

    /** TabBar 选中胶囊底（浅蓝） */
    val capsuleSelected = Color(0xFFE5F0FF)

    // ---- 分割线 ----
    /** 项间 1px 分割线 */
    val separator = Color(0xFFE5E5EA)

    /** 卡片内缩进后的分割线起点留白后的视觉变体 */
    val separatorSoft = Color(0xFFEFEFF4)

    // ---- 文本 ----
    val label = Color(0xFF000000)
    val secondaryLabel = Color(0xFF8E8E93)
    val tertiaryLabel = Color(0xFFC7C7CC)

    // ---- 高亮 ----
    val tint = Color(0xFF007AFF)
    val destructive = Color(0xFFFF3B30)
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9500)

    // ---- 彩色图标瓦片底（红橙黄绿蓝紫）----
    val tileRed = Color(0xFFFF3B30)
    val tileOrange = Color(0xFFFF9500)
    val tileYellow = Color(0xFFFFCC00)
    val tileGreen = Color(0xFF34C759)
    val tileBlue = Color(0xFF007AFF)
    val tilePurple = Color(0xFFAF52DE)
    val tileTeal = Color(0xFF30B0C7)
    val tileIndigo = Color(0xFF5856D6)
    val tileGray = Color(0xFF8E8E93)
    val purple = Color(0xFFAF52DE)

    /** 瓦片内图标统一白色 */
    val tileGlyph = Color(0xFFFFFFFF)

    // ---- 交互 ----
    /** 按下时整体透明度（iOS Active state） */
    val pressedOpacity = 0.55f
    val pressedOpacityStrong = 0.4f

    // ---- 圆角 ----
    /** InsetGrouped 卡片圆角 */
    val cardRadius = 16.dp
    /** ListItem 高亮块圆角 */
    val rowRadius = 8.dp
    /** 彩色图标瓦片圆角 */
    val tileRadius = 7.dp
    /** TabBar 胶囊圆角 */
    val tabBarRadius = 28.dp
    /** 圆形搜索按钮半径（直径 52dp → 26dp） */
    val fabRadius = 26.dp
}

// ====================================================================
// Material3 颜色方案（映射到 iOS 令牌）
// ====================================================================

private val IOSLightColors = lightColorScheme(
    primary = IOSPalette.tint,
    onPrimary = Color.White,
    primaryContainer = IOSPalette.capsuleSelected,
    onPrimaryContainer = IOSPalette.tint,
    secondary = IOSPalette.secondaryLabel,
    onSecondary = Color.White,
    secondaryContainer = IOSPalette.cardSecondary,
    onSecondaryContainer = IOSPalette.label,
    tertiary = IOSPalette.purple,
    onTertiary = Color.White,
    tertiaryContainer = IOSPalette.cardSecondary,
    onTertiaryContainer = IOSPalette.label,
    error = IOSPalette.destructive,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBE9),
    onErrorContainer = IOSPalette.destructive,
    background = IOSPalette.groupedBackground,
    onBackground = IOSPalette.label,
    surface = IOSPalette.card,
    onSurface = IOSPalette.label,
    surfaceVariant = IOSPalette.cardSecondary,
    onSurfaceVariant = IOSPalette.secondaryLabel,
    outline = IOSPalette.separator,
    outlineVariant = IOSPalette.separator
)

// ====================================================================
// 排版（iOS 规格：17pt 大标题 / 17pt 正文 / 13pt 次级 / 11pt 脚注）
// ====================================================================

private fun iosTextStyle(
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

private val IOSTypography = Typography(
    displayLarge = iosTextStyle(FontWeight.Bold, 32, 38, -0.4f),
    displayMedium = iosTextStyle(FontWeight.Bold, 28, 34, -0.3f),
    displaySmall = iosTextStyle(FontWeight.Bold, 24, 30, -0.2f),
    headlineLarge = iosTextStyle(FontWeight.SemiBold, 22, 28, -0.2f),
    headlineMedium = iosTextStyle(FontWeight.SemiBold, 20, 26, -0.2f),
    headlineSmall = iosTextStyle(FontWeight.SemiBold, 17, 22),
    titleLarge = iosTextStyle(FontWeight.Medium, 17, 22, -0.2f),
    titleMedium = iosTextStyle(FontWeight.Medium, 16, 21, -0.1f),
    titleSmall = iosTextStyle(FontWeight.Medium, 15, 20, -0.1f),
    bodyLarge = iosTextStyle(FontWeight.Normal, 17, 22, -0.2f),
    bodyMedium = iosTextStyle(FontWeight.Normal, 15, 20, -0.1f),
    bodySmall = iosTextStyle(FontWeight.Normal, 13, 18),
    labelLarge = iosTextStyle(FontWeight.Medium, 15, 20, -0.1f),
    labelMedium = iosTextStyle(FontWeight.Normal, 13, 18, -0.05f),
    labelSmall = iosTextStyle(FontWeight.Normal, 11, 14, 0.06f)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * iOS 浅色主题（固定浅色，参照系统「设置」App）。
 */
@Composable
fun WatchFaceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IOSLightColors,
        typography = IOSTypography,
        shapes = AppShapes,
        content = content
    )
}

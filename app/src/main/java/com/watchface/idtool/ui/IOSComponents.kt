package com.watchface.idtool.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ====================================================================
// iOS 组件库（参照系统「设置」App）
//
//   InsetGroup        分组卡片：一张大圆角白卡
//   InsetListItem     卡内列表项：左侧彩色瓦片 / 标题 / 副标题 / 右值 / 箭头 / 开关
//   ColoredIconTile   彩色圆角图标瓦片
// ====================================================================


/**
 * 分组卡片：一张大圆角白卡，内部纵向排列 ListItem。
 * 项之间的 1px 分割线由 [InsetListItem] 自行绘制（自动跳过最后一项）。
 */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 卡片与其他 GlassCard 分区同宽（页级 20dp 边距），
            // 行内容统一由 [InsetListItem]/行组件自带 16dp 内边距对齐
            .clip(RoundedCornerShape(IOSPalette.cardRadius))
            .background(IOSPalette.card),
        content = content
    )
}

/** 彩色圆角图标瓦片（红橙黄绿蓝紫） */
@Composable
fun ColoredIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 29
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(IOSPalette.tileRadius))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IOSPalette.tileGlyph,
            modifier = Modifier.size((size * 0.6f).dp)
        )
    }
}

/**
 * 卡内列表项。
 *
 * @param tileIcon  左侧瓦片图标（null 则不显示瓦片）
 * @param tileTint  瓦片底色
 * @param dotColor  左侧彩色圆点（iOS 设置「详情分组」风格；优先级低于瓦片）
 * @param title     主文本
 * @param subtitle  副文本（次级灰）
 * @param value     右侧数值（次级灰）
 * @param showArrow 右侧 chevron 箭头
 * @param onClick   点击回调（null 则不可点击）
 * @param destructive 主文本染红
 * @param toggle / onToggle 开关态
 * @param isLast    是否为组内最后一项（不画底部分割线）
 */
@Composable
fun InsetListItem(
    title: String,
    modifier: Modifier = Modifier,
    tileIcon: ImageVector? = null,
    tileTint: Color = IOSPalette.tileBlue,
    dotColor: Color? = null,
    subtitle: String? = null,
    value: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    toggle: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    isLast: Boolean = false
) {
    val interaction = remember0()
    val pressed by interaction.collectIsPressedAsState()
    // iOS Active state：按下时整行轻微变淡
    val contentAlpha by animateFloatAsState(
        targetValue = if (pressed && onClick != null) IOSPalette.pressedOpacity else 1f,
        animationSpec = tween(90),
        label = "iosRowPress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 60.dp else 50.dp)
            .then(
                if (onClick != null || onToggle != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null
                    ) {
                        when {
                            onToggle != null && toggle != null -> onToggle(!toggle)
                            onClick != null -> onClick()
                        }
                    }
                } else Modifier
            )
            .graphicsLayer { alpha = contentAlpha }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tileIcon != null) {
            ColoredIconTile(icon = tileIcon, tint = tileTint)
            Spacer(Modifier.width(12.dp))
        } else if (dotColor != null) {
            // iOS「详情分组」彩色圆点：10dp 实心圆
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // AppLocale.t：标题/副标题过翻译词表，英文模式下自动切换
                text = AppLocale.t(title),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = if (destructive) IOSPalette.destructive else IOSPalette.label,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = AppLocale.t(subtitle),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = IOSPalette.secondaryLabel,
                    maxLines = 2
                )
            }
        }

        if (value != null) {
            Text(
                text = AppLocale.t(value),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = IOSPalette.secondaryLabel,
                maxLines = 1,
                // 等宽表格数字：数值变化时不跳宽（tnum）
                style = androidx.compose.material3.LocalTextStyle.current
                    .copy(fontFeatureSettings = "tnum")
            )
            if (showArrow) Spacer(Modifier.width(6.dp))
        }

        if (toggle != null) {
            Switch(
                checked = toggle,
                onCheckedChange = { v -> onToggle?.invoke(v) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = IOSPalette.tint,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = IOSPalette.separator,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IOSPalette.tertiaryLabel,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // 0.5dp 分割线（内缩对齐正文，圆点行/瓦片行各自对齐；颜色更柔和）
    if (!isLast) {
        val dividerStart = when {
            tileIcon != null -> 57.dp
            dotColor != null -> 40.dp
            else -> 16.dp
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = dividerStart)
                .height(0.5.dp)
                .background(IOSPalette.separatorSoft)
        )
    }
}


/** 便捷：remember 一个 MutableInteractionSource（避免重复 remember 导入） */
@Composable
private fun remember0(): MutableInteractionSource =
    androidx.compose.runtime.remember { MutableInteractionSource() }

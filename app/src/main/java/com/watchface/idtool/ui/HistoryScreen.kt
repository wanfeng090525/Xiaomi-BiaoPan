package com.watchface.idtool.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchface.idtool.MainViewModel
import com.watchface.idtool.RecordStore
import com.watchface.idtool.UiState
import com.watchface.idtool.WatchfaceRecord
import kotlin.math.sin

/**
 * 记录页：液态玻璃重构版
 *
 * - 悬浮标题栏（记录数徽章 + 导出 + 清空）
 * - 玻璃记录卡：ID 迁移动画（旧 ID 划线 → 箭头脉冲 → 新 ID 高亮）
 * - 今天徽章带呼吸绿点
 * - 空状态液态动画
 */
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    state: UiState
) {
    val context = LocalContext.current
    val records = state.records
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ============ 标题栏 ============
            item {
                StaggeredItem(index = 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(
                            icon = Icons.Default.History,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "修改记录",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (records.isEmpty()) "暂无记录"
                                else AppLocale.tf("共 {0} 条记录", records.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (records.isNotEmpty()) {
                            // 导出文本
                            GlassIconButton(
                                icon = Icons.Default.IosShare,
                                contentDescription = "导出记录",
                                tint = MaterialTheme.colorScheme.primary,
                                size = 34.dp,
                                onClick = {
                                    val text = RecordStore.exportText(context)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "导出修改记录")
                                    )
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            // 清空
                            GlassIconButton(
                                icon = Icons.Default.DeleteSweep,
                                contentDescription = "清空记录",
                                tint = AppColors.dangerAdaptive(),
                                size = 34.dp,
                                tintTop = AppColors.danger.copy(alpha = 0.12f),
                                tintBottom = AppColors.danger.copy(alpha = 0.06f),
                                onClick = {
                                    showClearAllDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // ============ 空状态 ============
            if (records.isEmpty()) {
                item {
                    StaggeredItem(index = 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyHistoryState()
                        }
                    }
                }
            } else {
                // ============ 记录卡片（交错入场） ============
                itemsIndexed(records, key = { _, r -> "${r.time}|${r.oldId}|${r.newId}" }) { index, record ->
                    StaggeredItem(index = index + 1) {
                        HistoryRecordCard(
                            record = record,
                            onOpen = {
                                val intent = viewModel.openFile(index)
                                if (intent != null) {
                                    context.startActivity(intent)
                                }
                            },
                            onShare = {
                                val intent = viewModel.shareFile(index)
                                if (intent != null) {
                                    context.startActivity(
                                        Intent.createChooser(intent, "分享文件")
                                    )
                                }
                            },
                            onDelete = {
                                showDeleteDialog = index
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    // ============ 删除确认弹窗 ============
    showDeleteDialog?.let { idx ->
        ConfirmDialog(
            title = "删除记录",
            message = "确定要删除这条修改记录吗？",
            onConfirm = {
                viewModel.deleteRecord(idx, false)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    // ============ 清空确认弹窗 ============
    if (showClearAllDialog) {
        ConfirmDialog(
            title = "清空全部记录",
            message = AppLocale.tf("将删除全部 {0} 条记录，该操作不可恢复。", records.size),
            onConfirm = {
                viewModel.clearAllRecords(false)
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

// ====================================================================
// 记录卡片
// ====================================================================

@Composable
private fun HistoryRecordCard(
    record: WatchfaceRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val today = record.isToday()
    val successColor = if (today) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    GlassCard(shape = RoundedCornerShape(20.dp), contentPadding = 14.dp) {
        // ---- 顶部：时间徽章 + 操作按钮 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间胶囊（今天 = 绿色 + 呼吸点）
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (today) {
                    GlowDot(color = Color.White, dotSize = 6.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = record.displayDate(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = NumericFonts,
                    color = successColor
                )
                if (today) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "今天",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassIconButton(
                    icon = Icons.Default.OpenInNew,
                    contentDescription = AppLocale.t("打开文件"),
                    tint = MaterialTheme.colorScheme.primary,
                    size = 30.dp,
                    onClick = onOpen
                )
                GlassIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = "分享文件",
                    tint = AppColors.successAdaptive(),
                    size = 30.dp,
                    onClick = onShare
                )
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "删除记录",
                    tint = AppColors.dangerAdaptive(),
                    size = 30.dp,
                    tintTop = AppColors.danger.copy(alpha = 0.12f),
                    tintBottom = AppColors.danger.copy(alpha = 0.06f),
                    onClick = onDelete
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- ID 迁移：旧 → 新 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "原 ID",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    record.oldId,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = NumericFonts,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PulsingArrow()

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "新 ID",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    record.newId,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = NumericFonts,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---- 名称变更（存在时展示） ----
        AnimatedVisibility(
            visible = record.nameChanged && record.oldName.isNotEmpty() && record.newName != record.oldName,
            enter = fadeIn(tween(280)),
            exit = fadeOut(tween(180))
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFE9EBF4),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        record.oldName,
                        fontSize = 11.sp,
                        fontFamily = NumericFonts,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        record.newName.ifEmpty { "(空)" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = NumericFonts,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 底部：表盘信息 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .glass(
                            RoundedCornerShape(7.dp),
                            rememberGlassColors()
                        )
                )
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = Color(0xFFE9EBF4),
                    modifier = Modifier.size(11.dp)
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = record.newName.ifEmpty { record.oldName.ifEmpty { AppLocale.t("(未命名)") } },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = RecordStore.formatBytes(record.fileSize),
                fontSize = 10.5.sp,
                fontFamily = NumericFonts,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ====================================================================
// 脉冲箭头（左右轻微往返 + 透明度呼吸）
// ====================================================================

@Composable
private fun PulsingArrow() {
    val transition = rememberInfiniteTransition(label = "arrow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowT"
    )
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = Color(0xFFE9EBF4).copy(alpha = 0.35f + 0.65f * t),
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer { translationX = 4f * sin(t * Math.PI.toFloat() * 2f) }
    )
}

// ====================================================================
// 空状态（浮动图标 + 液态呼吸）
// ====================================================================

@Composable
private fun EmptyHistoryState() {
    val transition = rememberInfiniteTransition(label = "empty")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyT"
    )
    val offsetY by animateFloatAsState(
        targetValue = 8f * sin(t * Math.PI.toFloat()),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "emptyY"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { translationY = offsetY }
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // 外圈呼吸光环
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .alpha(0.25f + 0.2f * t)
                    .glass(CircleShape, rememberGlassColors())
            )
            // 内圈玻璃
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .glass(CircleShape, rememberGlassColors()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFFE9EBF4),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "暂无修改记录",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "修改表盘 ID 后记录将显示在此",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

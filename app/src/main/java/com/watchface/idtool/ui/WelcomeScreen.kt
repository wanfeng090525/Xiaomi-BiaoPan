package com.watchface.idtool.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
 import com.watchface.idtool.AppSettings
 import com.watchface.idtool.BuildConfig
 import com.watchface.idtool.ClickSound
 import com.watchface.idtool.HitokotoApi
import com.watchface.idtool.ImportedFile
import com.watchface.idtool.LogKeyExtractor
import com.watchface.idtool.MainViewModel
import com.watchface.idtool.PermissionStatus
import com.watchface.idtool.RecordStore
import com.watchface.idtool.UiState

/**
 * 首页（控制中心式布局）
 *
 * 结构（自上而下）：
 *   1. 品牌栏      Logo + 标题/版本 + 设置
 *   2. 权限胶囊卡  状态图标 + 提示 + 刷新（参考图 WLAN/蓝牙开关形态）
 *   3. 快捷操作    2×2 玻璃瓷砖网格（参考图圆形开关阵列）
 *   4. 已导入文件  列表
 */
@Composable
fun WelcomeScreen(
    viewModel: MainViewModel,
    state: UiState,
    onNavigateToModify: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current

    // 首页直接选择文件 → 加载并跳转修改页
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } catch (_: Exception) {
                null
            } ?: it.toString().substringAfterLast("/").let { name ->
                java.net.URLDecoder.decode(name, "UTF-8")
            }
            viewModel.loadFile(it, fileName)
            onNavigateToModify()
        }
    }

    // 密钥提取ZIP文件选择器 — 选择后自动提取
    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = try {
                val tempFile = File(context.cacheDir, "extract_${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(it)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile.absolutePath
            } catch (_: Exception) {
                null
            }
            if (path != null) {
                viewModel.setExtractZipPath(path)
                // 选择完成后自动触发提取，无需再手动点击
                viewModel.extractLogKey()
            }
        }
    }

    // 所有文件访问权限授权页返回后：刷新状态 + 自动扫描
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 先刷新权限胶囊（会识别 FILE 状态）
        viewModel.checkPermissionStatus()
        if (viewModel.hasManageExternalStorage()) {
            viewModel.showToast("文件权限已授予，正在扫描…")
            viewModel.quickImportSmart()
        } else {
            viewModel.showToast("未授予「所有文件访问权限」，可改用 Root/Shizuku")
            viewModel.showNoPermissionDialog()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ============ 品牌栏 ============
        StaggeredItem(index = 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoBadge(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "表盘 ID 工具",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "WATCHFACE ID TOOL · v${BuildConfig.VERSION_NAME}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 设置入口移至底部导航分离式圆钮
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 一言引用（Hitokoto API） ============
        StaggeredItem(index = 1) {
            HitokotoBar()
        }

        Spacer(Modifier.height(18.dp))

        // ============ 权限状态胶囊卡 ============
        StaggeredItem(index = 2) {
            PermissionStatusCard(
                status = state.permissionStatus,
                onRefresh = { viewModel.checkPermissionStatus() }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ============ 快捷操作 2×2 瓷砖网格 + 密钥提取入口 ============
        StaggeredItem(index = 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickTile(
                    icon = Icons.Default.FolderOpen,
                    title = "一键导入",
                    subtitle = "表盘目录批量导入",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // 智能导入：有 Root/Shizuku 直接扫；有文件管理权限用零宽空格静默扫；
                        // 都没有则跳转「所有文件访问权限」授权页（使用系统设置页）
                        val needRequestPermission = viewModel.quickImportSmart()
                        if (needRequestPermission) {
                            viewModel.showToast("请点击「允许」授予所有文件管理权限，以扫描表盘文件")
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                manageStorageLauncher.launch(intent)
                            } catch (e: Exception) {
                                // 降级方案：跳转到通用文件访问设置页
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                manageStorageLauncher.launch(intent)
                            }
                        }
                    }
                )
                QuickTile(
                    icon = Icons.Default.Watch,
                    title = "选择文件",
                    subtitle = "手动选取 .bin 文件",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!viewModel.requireLogin()) return@QuickTile
                        filePicker.launch(arrayOf("*/*"))
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        StaggeredItem(index = 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickTile(
                    icon = Icons.Default.Build,
                    title = "修改表盘",
                    subtitle = "ID 与名称修改",
                    tint = AppColors.successAdaptive(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!viewModel.requireLogin()) return@QuickTile
                        onNavigateToModify()
                    }
                )
                QuickTile(
                    icon = Icons.Default.History,
                    title = "修改记录",
                    subtitle = if (state.records.isEmpty()) "暂无记录"
                    else AppLocale.tf("{0} 条记录", state.records.size),
                    tint = AppColors.warning,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHistory
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 密钥提取快捷瓷砖（独立一行）
        StaggeredItem(index = 5) {
            KeyExtractionTile(
                state = state,
                zipPicker = zipPicker,
                onExtract = { viewModel.extractLogKey() },
                onClear = { viewModel.clearExtractResult() }
            )
        }

        // ============ 已导入的表盘文件 ============
        AnimatedVisibility(
            visible = state.importedFiles.isNotEmpty(),
            enter = fadeIn(tween(280)) + expandVertically(tween(320)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(240))
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppLocale.tf("已导入的表盘 · {0}", state.importedFiles.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    GlassIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = AppLocale.t("清除"),
                        tint = AppColors.dangerAdaptive(),
                        size = 30.dp,
                        tintTop = AppColors.danger.copy(alpha = 0.14f),
                        tintBottom = AppColors.danger.copy(alpha = 0.06f),
                        onClick = { viewModel.clearImportedFiles() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                state.importedFiles.forEachIndexed { index, file ->
                    StaggeredItem(index = index + 5) {
                        ImportedFileCard(
                            file = file,
                            onClick = {
                                viewModel.loadImportedFile(file)
                                onNavigateToModify()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ============ 密钥提取结果展示 ============
        if (state.isExtracting || state.extractResult != null) {
            Spacer(Modifier.height(20.dp))
            StaggeredItem(index = 100) {
                KeyExtractionResultCard(
                    result = state.extractResult,
                    isExtracting = state.isExtracting,
                    onClear = { viewModel.clearExtractResult() }
                )
            }
            if (state.isExtracting) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = 1f
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    // ============ 弹窗 ============
    if (state.showNoPermissionDialog) {
        NoPermissionDialog(
            onDismiss = { viewModel.dismissNoPermissionDialog() },
            onAuthorizeShizuku = {
                viewModel.dismissNoPermissionDialog()
                viewModel.requestShizukuPermission()
            }
        )
    }

    if (state.showAnnouncementDialog) {
        val config = state.cloudConfig
        if (config != null) {
            AnnouncementDialog(
                announcement = config.announcement,
                onDismiss = { viewModel.dismissAnnouncementDialog() }
            )
        }
    }

    // 版本更新弹窗：与公告完全分离，仅在公告关闭后展示
    if (state.showUpdateDialog && !state.showAnnouncementDialog) {
        val config = state.cloudConfig
        if (config != null) {
            UpdateDialog(
                latestVersion = config.latestVersion,
                onUpdate = { viewModel.startDownloadUpdate() },
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
    }

    if (state.showDownloadProgress) {
        DownloadProgressDialog(
            progress = state.downloadProgress,
            downloadedBytes = state.downloadDownloadedBytes,
            totalBytes = state.downloadTotalBytes,
            speedBytesPerSec = state.downloadSpeed,
            isDownloading = state.isDownloading,
            error = state.downloadError,
            onDismiss = { viewModel.dismissDownloadProgress() },
            onRetry = { viewModel.startDownloadUpdate() },
            onOpenBrowser = { viewModel.openDownloadInBrowser() },
            onCancel = { viewModel.cancelDownloadUpdate() }
        )
    }
}

/** 应用徽标：玻璃相框 + 弹簧入场 */
@Composable
private fun LogoBadge(size: androidx.compose.ui.unit.Dp = 44.dp) {
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "logoScale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassShadow(6.dp, RoundedCornerShape(size * 0.32f))
            .glass(
                RoundedCornerShape(size * 0.32f),
                rememberGlassColors()
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.26f))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    RoundedCornerShape(size * 0.26f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.watchface.idtool.R.drawable.ic_ximi_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ====================================================================
// 快捷操作瓷砖（参考图圆形开关的方形版：图标 + 标题 + 副标题）
// ====================================================================

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tileContext = androidx.compose.ui.platform.LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1.55f)
            .glassShadow(8.dp, RoundedCornerShape(22.dp))
            .pressScale(interaction, pressedScale = 0.94f)
            .glass(RoundedCornerShape(22.dp), rememberGlassColors())
            .pressRipple(interaction, clipShape = RoundedCornerShape(22.dp), color = tint, intensity = 1.2f)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                if (AppSettings.soundEnabled) {
                    ClickSound.play(tileContext)
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .glass(
                        RoundedCornerShape(12.dp),
                        rememberGlassColors()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFFE9EBF4),
                    modifier = Modifier.size(17.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ====================================================================
// 权限状态胶囊卡（WLAN 开关同款形态）
// ====================================================================

private data class PermissionVisual(
    val icon: ImageVector,
    val tintTop: Color?,
    val tintBottom: Color?,
    val iconTint: Color,
    val title: String,
    val subtitle: String,
    val active: Boolean
)

// ====================================================================
// 时钟 · 一言条（Hitokoto API）
//
//   · 位置：品牌栏下方，权限卡上方（外间距比普通卡片更大）
//   · 结构（同一玻璃组件，上下两段）：
//       上段  大号时钟 HH:mm:ss（数字翻动动画）+ 右侧日期小字
//       中间  渐隐细玻璃分隔线
//       下段  竖向光棒 + 引文（打字机渐显 + 光标）+ 出处 + 刷新
//   · 时钟：精确到秒；冒号随秒呼吸闪烁；日期跟随界面语言
//   · 一言：每次打开 App 自动拉取刷新；文字逐字渐显，
//     显示完毕后出处淡入上浮；点按可手动换一句（重新渐显）
//   · 容错：断网/接口异常自动回退内置句子，永不空白
// ====================================================================

@Composable
private fun HitokotoBar() {
    var quote by remember { mutableStateOf<HitokotoApi.Hitokoto?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var revealCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 每次进入组合（即每次打开 App / 回到主页）自动拉取新句
    LaunchedEffect(Unit) {
        quote = HitokotoApi.fetch()
    }

    // 打字机渐显：新句到达后逐字展开
    LaunchedEffect(quote) {
        revealCount = 0
        val text = quote?.text.orEmpty()
        text.forEachIndexed { i, _ ->
            delay(26)
            revealCount = i + 1
        }
    }

    // 时钟：对齐秒边界刷新，精确到秒
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L + 8L)
            nowSec = System.currentTimeMillis() / 1000L
        }
    }
    val cal = remember(nowSec) {
        Calendar.getInstance().apply { timeInMillis = nowSec * 1000L }
    }
    val hh = String.format(Locale.ROOT, "%02d", cal.get(Calendar.HOUR_OF_DAY))
    val mm = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE))
    val ss = String.format(Locale.ROOT, "%02d", cal.get(Calendar.SECOND))
    // 日期仅在跨天时重算
    val dateText = remember(cal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.YEAR)) {
        formatDateLabel(cal)
    }

    val refresh: () -> Unit = {
        if (!refreshing && quote != null) {
            refreshing = true
            scope.launch {
                quote = HitokotoApi.fetch()
                refreshing = false
            }
        }
    }

    // 刷新图标旋转
    val spin by animateFloatAsState(
        targetValue = if (refreshing) 1f else 0f,
        animationSpec = tween(400),
        label = "hitokotoSpin"
    )
    val interaction = remember { MutableInteractionSource() }
    val hitokotoContext = androidx.compose.ui.platform.LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val timeColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(
                RoundedCornerShape(20.dp),
                rememberGlassColors(
                    tintTop = Color.White.copy(alpha = 0.08f),
                    tintBottom = Color.White.copy(alpha = 0.03f)
                )
            )
            .clickable(interactionSource = interaction, indication = null) {
                if (AppSettings.soundEnabled) {
                    ClickSound.play(hitokotoContext)
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
                refresh()
            }
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        // ---- 上段：大号时钟 + 右侧日期 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedTimeSeg(hh, timeColor)
                AnimatedTimeColon(timeColor, nowSec)
                AnimatedTimeSeg(mm, timeColor)
                AnimatedTimeColon(timeColor, nowSec)
                AnimatedTimeSeg(ss, timeColor.copy(alpha = 0.62f))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = dateText,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(Modifier.height(13.dp))

        // ---- 中间：渐隐细玻璃分隔线 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(1.dp)
                )
        )

        Spacer(Modifier.height(13.dp))

        // ---- 下段：光棒 + 引文（打字机渐显）+ 刷新 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 竖向光棒（引用视觉锚点）
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.0f),
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.0f)
                            )
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(14.dp))

            // 引文 + 出处（高度自适应动画）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(tween(320))
            ) {
                val q = quote
                if (q == null) {
                    Text(
                        text = "…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                } else {
                    val revealing = revealCount < q.text.length
                    // 渐显完成后文字由半亮平滑过渡到全亮
                    val textAlpha by animateFloatAsState(
                        targetValue = if (revealing) 0.65f else 0.85f,
                        animationSpec = tween(400),
                        label = "quoteAlpha"
                    )
                    val text = if (revealing) q.text.take(revealCount) + "▎" else q.text
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 出处：文字展开完毕后淡入上浮
                    AnimatedVisibility(
                        visible = !revealing && q.from.isNotBlank(),
                        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 2 },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "—— ${q.from}",
                            fontSize = 11.sp,
                            letterSpacing = 0.4.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 刷新指示（加载中呼吸闪烁）
            val iconAlpha by animateFloatAsState(
                targetValue = if (refreshing) 0.25f else 0.45f,
                animationSpec = tween(300),
                label = "hitokotoIconAlpha"
            )
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha),
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin * 360f }
            )
        }
    }
}

/** 日期标签：跟随界面语言（中文「8月15日 · 周五」，其它语言本地化短格式） */
private fun formatDateLabel(cal: Calendar): String {
    val lang = AppLocale.savedLang
    val loc = if (lang == "system") Locale.getDefault()
    else Locale.forLanguageTag(lang.replace('_', '-'))
    return try {
        if (loc.language == "zh") {
            java.text.SimpleDateFormat("M月d日 · E", loc).format(cal.time)
        } else {
            java.text.SimpleDateFormat("MMM d · E", loc).format(cal.time)
        }
    } catch (_: Exception) {
        ""
    }
}

/** 时钟数字段：变化时旧数字上滑淡出、新数字自下滑入（翻牌质感） */
@Composable
private fun AnimatedTimeSeg(seg: String, color: Color) {
    AnimatedContent(
        targetState = seg,
        transitionSpec = {
            (slideInVertically(tween(340, easing = EaseOutCubic)) { it / 2 } +
                    fadeIn(tween(340)))
                .togetherWith(
                    slideOutVertically(tween(340, easing = EaseOutCubic)) { -it / 2 } +
                            fadeOut(tween(200))
                )
        },
        label = "timeSeg"
    ) { s ->
        Text(
            text = s,
            style = TextStyle(
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
                color = color,
                letterSpacing = 0.5.sp
            )
        )
    }
}

/** 时钟冒号：随秒呼吸闪烁，强化「走时」生命感 */
@Composable
private fun AnimatedTimeColon(color: Color, nowSec: Long) {
    val alpha by animateFloatAsState(
        targetValue = if (nowSec % 2 == 0L) 0.85f else 0.35f,
        animationSpec = tween(500),
        label = "colonBlink"
    )
    Text(
        text = ":",
        style = TextStyle(
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            color = color.copy(alpha = alpha)
        ),
        modifier = Modifier.padding(horizontal = 1.dp)
    )
}

@Composable
private fun PermissionStatusCard(
    status: PermissionStatus,
    onRefresh: () -> Unit
) {
    val visual = when (status) {
        PermissionStatus.ROOT -> PermissionVisual(
            Icons.Default.Shield,
            null, null,
            AppColors.successAdaptive(), "Root 权限已激活", "可通过 su 命令提权操作", true
        )
        PermissionStatus.SHELL -> PermissionVisual(
            Icons.Outlined.ShieldMoon,
            null, null,
            AppColors.infoAdaptive(), "Shell 权限已激活", "Shizuku / ADB Shell 已就绪", true
        )
        PermissionStatus.FILE -> PermissionVisual(
            Icons.Default.FolderOpen,
            null, null,
            AppColors.successAdaptive(), "文件权限已激活", "已授予所有文件访问，可无 Root 导入", true
        )
        PermissionStatus.NONE -> PermissionVisual(
            Icons.Outlined.WarningAmber,
            null, null,
            AppColors.warning, "未激活权限", "可授权「所有文件访问」或使用 Root/Shizuku", false
        )
        PermissionStatus.CHECKING -> PermissionVisual(
            Icons.Default.Shield,
            null, null,
            MaterialTheme.colorScheme.onSurfaceVariant, "正在检测权限…", "请稍候", false
        )
    }

    val baseGlass = rememberGlassColors()
    val tintTop by animateColorAsState(
        visual.tintTop ?: baseGlass.tintTop,
        tween(450), label = "permBgTop"
    )
    val tintBottom by animateColorAsState(
        visual.tintBottom ?: baseGlass.tintBottom,
        tween(450), label = "permBgBottom"
    )

    GlassCard(
        shape = RoundedCornerShape(24.dp),
        tintTop = tintTop,
        tintBottom = tintBottom,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标徽章：激活时白色呼吸光晕，未激活时静态
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .glow(
                        if (visual.active) Color.White.copy(alpha = 0.18f)
                        else Color.White.copy(alpha = 0.08f),
                        radiusFraction = 1.5f
                    )
                    .glass(CircleShape, rememberGlassColors()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    visual.icon,
                    contentDescription = null,
                    tint = if (visual.active) Color.White
                    else Color(0xFFB9C0D4),
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        visual.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(7.dp))
                    // 状态点：激活 = 白色呼吸；未激活 = 暗灰缓慢待机呼吸
                    GlowDot(
                        color = if (visual.active) Color.White
                        else Color(0xFF6B7186),
                        dotSize = 7.dp
                    )
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    visual.subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (status != PermissionStatus.CHECKING) {
                GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = AppLocale.t("刷新"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRefresh,
                    size = 32.dp
                )
            }
        }
    }
}

// ====================================================================
// 已导入文件卡
// ====================================================================

@Composable
private fun ImportedFileCard(
    file: ImportedFile,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .glass(
                        RoundedCornerShape(15.dp),
                        rememberGlassColors()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = Color(0xFFE9EBF4),
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ID ${file.id}",
                        fontSize = 11.sp,
                        fontFamily = NumericFonts,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (file.name.isNotEmpty()) {
                        Text(
                            file.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    RecordStore.formatBytes(file.fileSize),
                    fontSize = 10.sp,
                    fontFamily = NumericFonts,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ====================================================================
// 无权限弹窗
// ====================================================================

@Composable
private fun NoPermissionDialog(
    onDismiss: () -> Unit,
    onAuthorizeShizuku: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .glow(AppColors.warning, radiusFraction = 1.6f)
                            .glass(CircleShape, rememberGlassColors()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "权限不可用",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "一键导入需要 Root 或 Shell 权限。\n" +
                                "请通过 Shizuku 授权或确保设备已 Root。\n\n" +
                                "Shizuku: 安装并启动 Shizuku 服务后\n点击下方按钮授权",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassButton(
                        text = "授权 Shizuku",
                        icon = Icons.Outlined.ShieldMoon,
                        onClick = onAuthorizeShizuku,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = "知道了",
                        onClick = onDismiss,
                        style = GlassButtonStyle.Glass,
                        shimmer = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ====================================================================
// 公告弹窗（纯公告内容，与版本更新完全分离）
// ====================================================================

@Composable
internal fun AnnouncementDialog(
    announcement: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .glow(Color(0xFFE9EBF4).copy(alpha = 0.30f), radiusFraction = 1.6f)
                            .glass(CircleShape, rememberGlassColors()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "公告",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = announcement,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(Modifier.height(18.dp))
                    GlassButton(
                        text = "知道了",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ====================================================================
// 版本更新弹窗（独立于公告：版本对比 + 下载）
// ====================================================================

@Composable
internal fun UpdateDialog(
    latestVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .glow(Color(0xFFD9DEEB).copy(alpha = 0.30f), radiusFraction = 1.6f)
                            .glass(CircleShape, rememberGlassColors()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "发现新版本",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassCard(
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = 12.dp
                    ) {
                        Column {
                            Text(
                                text = AppLocale.tf("当前版本  v{0}", BuildConfig.VERSION_NAME),
                                fontSize = 12.sp,
                                fontFamily = NumericFonts,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = AppLocale.tf("最新版本  v{0}", latestVersion),
                                fontSize = 12.sp,
                                fontFamily = NumericFonts,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        text = "下载更新",
                        icon = Icons.Default.Download,
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = "稍后再说",
                        onClick = onDismiss,
                        style = GlassButtonStyle.Glass,
                        shimmer = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ====================================================================
// 下载进度弹窗
// ====================================================================

@Composable
internal fun DownloadProgressDialog(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    speedBytesPerSec: Long,
    isDownloading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCancel: () -> Unit
) {
    val hasError = error != null

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() else onCancel() }) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasError) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .glow(AppColors.danger, radiusFraction = 1.6f)
                                .glass(CircleShape, rememberGlassColors()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "下载失败",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        GlassButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            text = "使用浏览器下载",
                            onClick = onOpenBrowser,
                            style = GlassButtonStyle.Glass,
                            shimmer = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            text = "取消",
                            onClick = onDismiss,
                            style = GlassButtonStyle.Glass,
                            shimmer = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .glow(AppColors.infoAdaptive(), radiusFraction = 1.6f)
                                .glass(CircleShape, rememberGlassColors()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDownloading) {
                                LiquidDotsLoader(dotSize = 8.dp)
                            } else {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (isDownloading) "正在下载更新" else "下载完成",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isDownloading) {
                            Spacer(Modifier.height(14.dp))
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress.coerceIn(0, 100) / 100f,
                                animationSpec = tween(280),
                                label = "dlProgress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.12f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatBytes(downloadedBytes),
                                    fontSize = 11.sp,
                                    fontFamily = NumericFonts,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (speedBytesPerSec > 0) {
                                    Text(
                                        text = "${formatBytes(speedBytesPerSec)}/s",
                                        fontSize = 11.sp,
                                        fontFamily = NumericFonts,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = if (totalBytes > 0) "$progress%" else formatBytes(downloadedBytes),
                                    fontSize = 11.sp,
                                    fontFamily = NumericFonts,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (totalBytes > 0) formatBytes(totalBytes) else "未知大小",
                                    fontSize = 11.sp,
                                    fontFamily = NumericFonts,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (speedBytesPerSec > 0 && totalBytes > 0 && progress < 100) {
                                val remainSec = (totalBytes - downloadedBytes) / speedBytesPerSec
                                if (remainSec > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = AppLocale.tf("预计剩余 {0}", formatDuration(remainSec)),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                text = "取消下载",
                                onClick = onCancel,
                                style = GlassButtonStyle.Glass,
                                shimmer = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "正在调起安装界面…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 弹窗入场动画包装 */
@Composable
internal fun DialogEntranceWrapper(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(180)) + androidx.compose.animation.scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f)
        ),
        exit = fadeOut(tween(150))
    ) {
        content()
    }
}

// ====================================================================
// 密钥提取瓷砖 — 首页快捷入口
//
//   · 点击选 ZIP → 自动解压提取密钥
//   · 提取中显示进度脉冲
//   · 有结果时右上角显示清除按钮
// ====================================================================

@Composable
private fun KeyExtractionTile(
    state: UiState,
    zipPicker: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    onExtract: () -> Unit,
    onClear: () -> Unit
) {
    val clickContext = androidx.compose.ui.platform.LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isExtracting = state.isExtracting

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassShadow(8.dp, RoundedCornerShape(22.dp))
            .pressScale(interaction, pressedScale = 0.96f)
            .glass(RoundedCornerShape(22.dp), rememberGlassColors())
            .pressRipple(interaction, clipShape = RoundedCornerShape(22.dp), color = Color.White, intensity = 1f)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                if (AppSettings.soundEnabled) {
                    ClickSound.play(clickContext)
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
                if (!isExtracting) {
                    if (state.extractResult != null) {
                        onClear()
                    }
                    zipPicker.launch("*/*")
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .glass(
                        RoundedCornerShape(13.dp),
                        rememberGlassColors()
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isExtracting) {
                    // 提取中：旋转刷新图标
                    val rotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)
                        ),
                        label = "extractSpin"
                    )
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFE9EBF4),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                } else {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFE9EBF4),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // 中间文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isExtracting) "正在提取 Token…" else "提取 Token",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        isExtracting -> "解压并扫描日志文件中"
                        state.extractResult?.success == true -> {
                            val tokenCount = state.extractResult.tokens.size.let {
                                if (it > 0) it else state.extractResult.authKeys["token"]?.size ?: 0
                            }
                            if (tokenCount > 0) "已提取 $tokenCount 个 Token · 点击重新选择"
                            else "未找到 Token · 点击重新选择"
                        }
                        state.extractResult?.success == false -> "提取失败 · 点击重试"
                        else -> "从日志 ZIP 中提取 Token + 设备名"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧清除按钮（有结果时）
            if (state.extractResult != null && !isExtracting) {
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = AppLocale.t("清除"),
                    tint = AppColors.dangerAdaptive(),
                    size = 28.dp,
                    tintTop = AppColors.danger.copy(alpha = 0.14f),
                    tintBottom = AppColors.danger.copy(alpha = 0.06f),
                    onClick = { onClear() }
                )
            } else if (!isExtracting) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ====================================================================
// 密钥提取结果卡片 — 仅展示 Token + 设备名称
//
//   · 从日志 ZIP 中提取 Token，并显示关联设备名
//   · 每个值可单独点击一键复制
// ====================================================================

@Composable
private fun KeyExtractionResultCard(
    result: LogKeyExtractor.ExtractResult?,
    isExtracting: Boolean,
    onClear: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedKey by remember { mutableStateOf<String?>(null) }

    // 优先使用带设备信息的 tokens；兼容旧结构
    val tokenInfos: List<LogKeyExtractor.TokenInfo> = when {
        result == null -> emptyList()
        result.tokens.isNotEmpty() -> result.tokens
        else -> (result.authKeys["token"] ?: emptyList()).map {
            LogKeyExtractor.TokenInfo(token = it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassShadow(8.dp, RoundedCornerShape(22.dp))
            .glass(RoundedCornerShape(22.dp), rememberGlassColors())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFE9EBF4),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Token 提取结果",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (result != null) {
                    GlassIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "清除",
                        tint = AppColors.dangerAdaptive(),
                        size = 28.dp,
                        onClick = { onClear(); copiedKey = null }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(12.dp))

            if (isExtracting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(
                                800,
                                easing = androidx.compose.animation.core.LinearEasing
                            )
                        ),
                        label = "resultSpin"
                    )
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在扫描日志文件…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (result == null) {
                Text(
                    text = "暂无结果",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!result.success) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = AppColors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.errorMessage ?: "提取失败",
                        fontSize = 12.sp,
                        color = AppColors.warning
                    )
                }
            } else {
                Text(
                    text = AppLocale.tf(
                        "扫描 {0} 个文件（共 {1} 个）",
                        result.scannedFilesCount,
                        result.rawFilesCount
                    ),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (tokenInfos.isNotEmpty()) {
                    // 只展示优先级最高的当前 Token（主日志命中优先），历史 bak 中的旧值不展示
                    val primary = tokenInfos.first()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFE9EBF4), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Token",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    KeyRow(
                        value = primary.token,
                        deviceName = primary.deviceName,
                        model = primary.model,
                        isCopied = copiedKey == primary.token,
                         onCopy = {
                             clipboardManager.setText(
                                 androidx.compose.ui.text.AnnotatedString(primary.token)
                             )
                             copiedKey = primary.token
                         }
                    )
                } else {
                    Text(
                        text = "未在日志中找到 Token",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 单个 Token 行：设备名 + 等宽 Token + 复制按钮 */
@Composable
private fun KeyRow(
    value: String,
    deviceName: String = "",
    model: String = "",
    isCopied: Boolean,
    onCopy: () -> Unit
) {
    val clickContext = androidx.compose.ui.platform.LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    // 只展示设备名称，不拼接型号
    val subtitle = when {
        deviceName.isNotEmpty() -> deviceName.trim()
        model.isNotEmpty() -> model.trim()
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .glass(
                RoundedCornerShape(10.dp),
                rememberGlassColors()
            )
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                if (AppSettings.soundEnabled) {
                    ClickSound.play(clickContext)
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
                onCopy()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 设备名称（提取结果处重点展示）
            if (subtitle.isNotEmpty()) {
                Text(
                    text = "设备：$subtitle",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7CFBA7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            } else {
                Text(
                    text = "设备：未知",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (isCopied) "已复制" else "复制",
            tint = if (isCopied) Color(0xFF7CFBA7) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}

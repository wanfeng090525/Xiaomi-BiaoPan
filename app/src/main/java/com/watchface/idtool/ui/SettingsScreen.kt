package com.watchface.idtool.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.watchface.idtool.AppSettings
import com.watchface.idtool.BgMode
import com.watchface.idtool.BuildConfig
import com.watchface.idtool.MainViewModel
import com.watchface.idtool.PermissionStatus
import com.watchface.idtool.SagAuthManager
import com.watchface.idtool.UiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 设置页（深色玻璃 · ColorOS 控制中心风格）
 *
 * 结构：
 *   1. 权限管理    当前状态卡 + 重新检测 / Shizuku 授权
 *   2. 应用与更新  检查更新 + 公告
 *   3. 关于        版本信息
 *
 * 所有图标均置于透明玻璃容器中（玻璃容器 + 实心图标规格）。
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    state: UiState
) {
    var showLangDialog by remember { mutableStateOf(false) }
    var showBgDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 相册选图启动器（GetContent 走 SAF，无需任何存储权限）
    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !AppSettings.setGalleryBackground(context, uri)) {
            android.widget.Toast.makeText(
                context,
                AppLocale.t("图片读取失败，请换一张试试"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ============ 卡密登录状态 ============
        StaggeredItem(index = 1) { SectionLabel("卡密登录") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 2) {
            LoginStatusSection()
        }

        Spacer(Modifier.height(22.dp))

        // ============ 权限管理 ============
        StaggeredItem(index = 3) { SectionLabel("权限管理") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 4) {
            PermissionSection(
                status = state.permissionStatus,
                onRefresh = { viewModel.checkPermissionStatus() },
                onAuthorize = { viewModel.requestShizukuPermission() }
            )
        }

        Spacer(Modifier.height(22.dp))

        // ============ 界面与语言 ============
        StaggeredItem(index = 5) { SectionLabel("界面与语言") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 4) {
            val savedLang = AppLocale.savedLang
            val langNative = AppLocale.LOCALES.firstOrNull { it.code == savedLang }?.native ?: "简体中文"
            InsetGroup {
                SettingsRow(
                    icon = Icons.Default.Language,
                    iconTint = IOSPalette.tileBlue,
                    title = "语言",
                    subtitle = langNative,
                    onClick = { showLangDialog = true }
                )
                SettingsDivider()
                // 显示密度拉条：80% ~ 110%，实时百分比
                DensitySliderRow()
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 背景与外观 ============
        StaggeredItem(index = 5) { SectionLabel("背景与外观") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 6) {
            val bgCfg = AppSettings.bgConfig

            InsetGroup {
                SettingsRow(
                    icon = Icons.Default.Wallpaper,
                    iconTint = IOSPalette.tilePurple,
                    title = "背景样式",
                    subtitle = when (bgCfg.mode) {
                        BgMode.GALLERY -> "自定义图片"
                        BgMode.COLOR -> "纯色背景"
                        BgMode.LIQUID -> "液态动态"
                        else -> "液态动态"   // 原默认壁纸已移除，统一按液态动态展示
                    },
                    onClick = { showBgDialog = true }
                )
                if (bgCfg.mode == BgMode.COLOR) {
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        iconTint = IOSPalette.tileIndigo,
                        title = "背景颜色",
                        subtitle = "自定义纯色（保持界面可读的深色调）",
                        onClick = { showColorDialog = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 应用与更新 ============
        StaggeredItem(index = 7) { SectionLabel("应用与更新") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 8) {
            InsetGroup {
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    iconTint = IOSPalette.tileTeal,
                    title = "检查更新",
                    subtitle = AppLocale.tf("当前版本 v{0}", BuildConfig.VERSION_NAME),
                    onClick = { viewModel.checkCloudConfig("update") }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconTint = IOSPalette.tileOrange,
                    title = "查看公告",
                    subtitle = if (state.cloudConfig != null) "有新公告" else "暂无公告",
                    onClick = { viewModel.checkCloudConfig("announce") }
                )
                SettingsDivider()
                // 公告自动弹出开关：开 = 启动时弹公告；关 = 仅手动查看
                SettingsSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = "启动时显示公告",
                    subtitle = "启动 App 时自动弹出新公告",
                    checked = AppSettings.announceAutoShow,
                    onCheckedChange = { AppSettings.setAnnounceAutoShow(context, it) }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 关于 ============
        StaggeredItem(index = 9) { SectionLabel("关于") }
        Spacer(Modifier.height(10.dp))
        StaggeredItem(index = 10) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 表盘 ID 工具：与 Github 按钮等宽并行，比例协调
                GlassCard(modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconBadge(Icons.Default.VerifiedUser, MaterialTheme.colorScheme.primary, size = 42.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "表盘 ID 工具",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "WATCHFACE ID TOOL · v${BuildConfig.VERSION_NAME}",
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Github 按钮：点击跳转开源仓库
                GlassCard(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wanfeng090525/XiaoMi-Clock-dial"))
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = com.watchface.idtool.R.drawable.ic_github),
                            contentDescription = "Github 仓库",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Github 仓库",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "查看开源仓库",
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    // ============ 弹窗 ============
    val dialogContext = LocalContext.current
    if (showLangDialog) {
        LanguageDialog(
            current = AppLocale.savedLang,
            onSelect = { code ->
                AppSettings.setLanguage(dialogContext, code)
                showLangDialog = false
            },
            onDismiss = { showLangDialog = false }
        )
    }

    // 背景样式弹窗（相册图片 / 纯色 / 液态动态；默认壁纸已移除）
    if (showBgDialog) {
        BgStyleDialog(
            current = AppSettings.bgConfig.mode,
            onPickGallery = {
                showBgDialog = false
                bgPicker.launch("image/*")
            },
            onPickColor = {
                AppSettings.setBackground(dialogContext, BgMode.COLOR)
                showBgDialog = false
                showColorDialog = true
            },
            onPickLiquid = {
                AppSettings.setBackground(dialogContext, BgMode.LIQUID)
                showBgDialog = false
            },
            onDismiss = { showBgDialog = false }
        )
    }

    // 纯色背景调色弹窗（色相 / 饱和度 / 明度 + 快捷预设）
    if (showColorDialog) {
        ColorPickerDialog(
            initial = AppSettings.bgConfig.color,
            onApply = { argb ->
                AppSettings.setBackground(dialogContext, BgMode.COLOR, argb)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }

    if (state.isCheckingCloud) {
        // 检查中弹窗：点击「检查更新 / 查看公告」后出现，8 秒超时自动关闭，可随时手动取消
        Dialog(onDismissRequest = { viewModel.cancelCloudCheck() }) {
            GlassCard(contentPadding = RowInset) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowDot(color = Color.White, dotSize = 10.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "正在检查更新…",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请稍候",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    GlassButton(
                        text = "取消",
                        onClick = { viewModel.cancelCloudCheck() },
                        style = GlassButtonStyle.Glass,
                        modifier = Modifier.fillMaxWidth(),
                        shimmer = false
                    )
                }
            }
        }
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
                onDismiss = { viewModel.dismissAnnouncementDialog() }
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

// ====================================================================
// 子组件
// ====================================================================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = IOSPalette.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 7.dp)
    )
}

/** iOS 彩色圆角瓦片图标容器 */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(IOSPalette.tileRadius))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    InsetListItem(
        title = title,
        subtitle = subtitle,
        tileIcon = icon,
        tileTint = iconTint,
        showArrow = true,
        onClick = onClick
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = RowInset + 28.dp + 12.dp)
            .height(0.5.dp)
            .background(IOSPalette.separator)
    )
}

/** 开关行：iOS InsetListItem + 系统蓝开关 */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    InsetListItem(
        title = title,
        subtitle = subtitle,
        tileIcon = icon,
        tileTint = IOSPalette.tileGray,
        toggle = checked,
        onToggle = onCheckedChange
    )
}

/** 卡密登录状态：未登录点击卡片输入卡密登录；已登录可取消解锁 */
@Composable
private fun LoginStatusSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(SagAuthManager.isLoggedIn) }
    var endTime by remember { mutableStateOf(SagAuthManager.endTime) }
    var kamiMask by remember { mutableStateOf(SagAuthManager.currentKamiMasked) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var kamiInput by remember { mutableStateOf(SagAuthManager.loadSavedKami(context)) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            loggedIn = SagAuthManager.isLoggedIn
            endTime = SagAuthManager.endTime
            kamiMask = SagAuthManager.currentKamiMasked
        }
    }

    val visual = if (loggedIn) {
        SettingsPermVisual(
            Icons.Default.VerifiedUser,
            AppColors.successAdaptive(),
            "已登录",
            buildString {
                if (kamiMask.isNotEmpty()) append("卡密：$kamiMask  ")
                if (endTime.isNotEmpty()) append("到期：$endTime")
                else append("可使用全部功能")
            }
        )
    } else {
        SettingsPermVisual(
            Icons.Default.Lock,
            AppColors.warning,
            "未登录",
            "点击此处输入卡密登录"
        )
    }

    GlassCard(
        onClick = if (!loggedIn) {{ showLoginDialog = true }} else null,
        contentPadding = RowInset
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(visual.icon, visual.tint, size = 44.dp)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = visual.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (loggedIn) {
                        Spacer(Modifier.width(8.dp))
                        GlowDot(color = Color.White, dotSize = 7.dp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = visual.subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (tip.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = tip,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (loggedIn) {
            Spacer(Modifier.height(14.dp))
            GlassButton(
                text = if (busy) "处理中…" else "取消解锁",
                onClick = {
                    if (busy) return@GlassButton
                    busy = true
                    tip = ""
                    scope.launch {
                        val r = SagAuthManager.unbindKami(context)
                        busy = false
                        loggedIn = SagAuthManager.isLoggedIn
                        endTime = SagAuthManager.endTime
                        kamiMask = SagAuthManager.currentKamiMasked
                        tip = r.message
                    }
                },
                style = GlassButtonStyle.Danger,
                modifier = Modifier.fillMaxWidth(),
                shimmer = false
            )
        }
    }

    if (showLoginDialog && !loggedIn) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) showLoginDialog = false }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "卡密登录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "输入授权卡密以解锁全部功能",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = kamiInput,
                        onValueChange = { kamiInput = it; tip = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("卡密") },
                        placeholder = { Text("请输入卡密") },
                        enabled = !busy
                    )
                    if (tip.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = tip,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        text = if (busy) "登录中…" else "登录",
                        onClick = {
                            if (busy) return@GlassButton
                            if (kamiInput.isBlank()) {
                                tip = "请输入卡密"
                                return@GlassButton
                            }
                            busy = true
                            tip = ""
                            scope.launch {
                                val r = SagAuthManager.login(context, kamiInput)
                                busy = false
                                tip = r.message
                                if (r.success) {
                                    loggedIn = true
                                    endTime = SagAuthManager.endTime
                                    kamiMask = SagAuthManager.currentKamiMasked
                                    showLoginDialog = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** 权限管理区块：状态卡 + 操作按钮 */
@Composable
private fun PermissionSection(
    status: PermissionStatus,
    onRefresh: () -> Unit,
    onAuthorize: () -> Unit
) {
    val visual = when (status) {
        PermissionStatus.ROOT -> SettingsPermVisual(
            Icons.Default.VerifiedUser, AppColors.successAdaptive(),
            "Root 权限可用", "可批量导入 / 直接写入系统目录"
        )
        PermissionStatus.SHELL -> SettingsPermVisual(
            Icons.Default.AdminPanelSettings, AppColors.infoAdaptive(),
            "Shell 权限可用", "通过 Shizuku / ADB 授权"
        )
        PermissionStatus.FILE -> SettingsPermVisual(
            Icons.Default.FolderOpen, AppColors.successAdaptive(),
            "文件权限可用", "已授予所有文件访问，可无 Root 导入"
        )
        PermissionStatus.NONE -> SettingsPermVisual(
            Icons.Default.Shield, AppColors.warning,
            "权限不可用", "可授权「所有文件访问」或 Root / Shizuku"
        )
        PermissionStatus.CHECKING -> SettingsPermVisual(
            Icons.Default.Security, MaterialTheme.colorScheme.onSurfaceVariant,
            "正在检测权限…", "请稍候"
        )
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(visual.icon, visual.tint, size = 44.dp)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = visual.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (status == PermissionStatus.ROOT ||
                        status == PermissionStatus.SHELL ||
                        status == PermissionStatus.FILE
                    ) {
                        Spacer(Modifier.width(8.dp))
                        GlowDot(color = Color.White, dotSize = 7.dp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = visual.subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = "重新检测",
                onClick = onRefresh,
                style = GlassButtonStyle.Glass,
                modifier = Modifier.weight(1f),
                shimmer = false
            )
            if (status == PermissionStatus.NONE) {
                GlassButton(
                    text = "Shizuku 授权",
                    onClick = onAuthorize,
                    style = GlassButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                    shimmer = false
                )
            }
        }
    }
}

private data class SettingsPermVisual(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String
)

/** 设置页所有行内元素的统一水平内边距（与 GlassCard 默认 contentPadding 对齐） */
private val RowInset = 16.dp

/** 密度拉条行：80% ~ 110%，松手保存并重建界面 */
@Composable
private fun DensitySliderRow() {
    val context = LocalContext.current
    var sliderValue by remember(AppSettings.densityFactor) {
        mutableFloatStateOf(AppSettings.densityFactor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Default.AspectRatio, IOSPalette.tileBlue)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "显示密度",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = IOSPalette.label
                )
                Spacer(Modifier.weight(1f))
                // 实时百分比（保时捷工程数字）
                Text(
                    text = "${(sliderValue * 100).roundToInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = NumericFonts,
                    color = IOSPalette.secondaryLabel
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "80% ~ 110%，松手后界面重新加载",
                fontSize = 13.sp,
                color = IOSPalette.secondaryLabel
            )
            Spacer(Modifier.height(4.dp))
            GlassSlider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    AppSettings.setDensityFactor(context, sliderValue)
                    // 密度在 attachBaseContext 生效，需重建 Activity
                    (context as? Activity)?.recreate()
                },
                valueRange = AppSettings.DENSITY_MIN..AppSettings.DENSITY_MAX,
                steps = 29   // 每 1% 一档
            )
        }
    }
}

// ====================================================================
// 语言选择弹窗
// ====================================================================

@Composable
private fun LanguageDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 18.dp) {
                Text(
                    text = "语言",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "LANGUAGE",
                    fontSize = 9.sp,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                // 语言选项：原生名 + 当前语言说明，纵向滚动选择
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppLocale.LOCALES.forEachIndexed { index, locale ->
                        val selected = current == locale.code
                        GlassCard(
                            onClick = { onSelect(locale.code) },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = 13.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = locale.native,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) Color.White
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (locale.zhDesc != locale.native) {
                                        Spacer(Modifier.height(1.dp))
                                        Text(
                                            text = locale.zhDesc,
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (index < AppLocale.LOCALES.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// 背景样式弹窗（相册图片 / 纯色 / 液态动态；默认壁纸已移除）
// ====================================================================

@Composable
private fun BgStyleDialog(
    current: String,
    onPickGallery: () -> Unit,
    onPickColor: () -> Unit,
    onPickLiquid: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 18.dp) {
                Text(
                    text = "背景样式",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))

                data class BgOption(
                    val mode: String,
                    val icon: ImageVector,
                    val title: String,
                    val subtitle: String,
                    val action: () -> Unit
                )

                listOf(
                    BgOption(BgMode.LIQUID, Icons.Default.Gradient, "液态动态", "渐变光斑动态背景（默认）", onPickLiquid),
                    BgOption(BgMode.GALLERY, Icons.Default.PhotoLibrary, "从相册选择", "自定义图片，自动适配屏幕比例", onPickGallery),
                    BgOption(BgMode.COLOR, Icons.Default.Palette, "纯色背景", "自定义颜色（深色调）", onPickColor)
                ).forEach { opt ->
                    val selected = current == opt.mode
                    GlassCard(
                        onClick = opt.action,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = 13.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(opt.icon, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opt.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selected) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = opt.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ====================================================================
// 纯色背景调色弹窗（HSV 三通道 + 深色预设）
// 明度限制在 6% ~ 46%：保证玻璃卡片与白色文字的可读性
// ====================================================================

/** 快捷预设（深色调，均保证界面可读） */
private val BG_COLOR_PRESETS = listOf(
    0xFF0E1116,  // 石墨黑
    0xFF12172B,  // 午夜蓝
    0xFF101F1A,  // 松林绿
    0xFF1F1216,  // 酒红
    0xFF1A1226,  // 暗紫
    0xFF0E1E22,  // 深青
    0xFF16181F,  // 炭灰
    0xFF241A10   // 深咖
)

@Composable
private fun ColorPickerDialog(
    initial: Long,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // 初始值 → HSV
    val initHsv = FloatArray(3).apply {
        android.graphics.Color.colorToHSV((initial and 0xFFFFFFFFL).toInt(), this)
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var value by remember { mutableFloatStateOf(initHsv[2].coerceIn(0.06f, 0.46f)) }

    fun currentArgb(): Int {
        val hsv = floatArrayOf(hue, sat, value)
        return android.graphics.Color.HSVToColor(hsv)
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogEntranceWrapper {
            GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 20.dp) {
                Text(
                    text = "背景颜色",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))

                // 实时预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(
                            color = Color(currentArgb().toLong() and 0xFFFFFFFFL),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa 预览文字 Preview",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 快捷预设
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BG_COLOR_PRESETS.forEach { preset ->
                        val argb = (preset and 0xFFFFFFFFL).toInt()
                        val selected = run {
                            val h = FloatArray(3).apply { android.graphics.Color.colorToHSV(argb, this) }
                            kotlin.math.abs(h[0] - hue) < 4f && kotlin.math.abs(h[1] - sat) < 0.06f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.20f),
                                    shape = CircleShape
                                )
                                .background(color = Color(preset), shape = CircleShape)
                                .clickable {
                                    val h = FloatArray(3).apply { android.graphics.Color.colorToHSV(argb, this) }
                                    hue = h[0]; sat = h[1]; value = h[2]
                                }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // HSV 拉条（白色系规格与密度拉条一致）
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.9f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                )
                Text(AppLocale.tf("色调  {0}°", hue.roundToInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = sliderColors
                )
                Text(AppLocale.tf("饱和度  {0}%", (sat * 100).roundToInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = sat,
                    onValueChange = { sat = it },
                    valueRange = 0f..1f,
                    colors = sliderColors
                )
                Text(AppLocale.tf("明度  {0}%（深色调保证可读）", (value * 100).roundToInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.06f..0.46f,
                    colors = sliderColors
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        text = "取消",
                        onClick = onDismiss,
                        style = GlassButtonStyle.Glass,
                        shimmer = false,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "应用",
                        onClick = { onApply(currentArgb().toLong() and 0xFFFFFFFFL) },
                        style = GlassButtonStyle.Primary,
                        shimmer = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ====================================================================
// 显示密度选择弹窗（已由内联拉条 DensitySliderRow 取代）
// ====================================================================

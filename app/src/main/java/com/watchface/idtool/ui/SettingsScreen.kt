package com.watchface.idtool.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val context = LocalContext.current

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
                            tint = MaterialTheme.colorScheme.onSurface,
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


    if (state.isCheckingCloud) {
        // 检查中弹窗：点击「检查更新 / 查看公告」后出现，8 秒超时自动关闭，可随时手动取消
        Dialog(onDismissRequest = { viewModel.cancelCloudCheck() }) {
            GlassCard(contentPadding = RowInset) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowDot(color = IOSPalette.tint, dotSize = 10.dp)
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
            .padding(start = RowInset + 28.dp + 13.dp)  // 与 InsetListItem 自绘分割线 57dp 严格一致
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
                        GlowDot(color = IOSPalette.success, dotSize = 7.dp)
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
                        GlowDot(color = IOSPalette.success, dotSize = 7.dp)
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
                                        color = if (selected) IOSPalette.tint
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
                                        tint = IOSPalette.tint,
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


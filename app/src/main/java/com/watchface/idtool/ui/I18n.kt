package com.watchface.idtool.ui

import android.os.LocaleList
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * 轻量 i18n 层（中文 + 英文回退）
 *
 * 原理：界面层所有 Text 渲染前先经过 AppLocale.t() 翻译，
 * 语言状态为 Compose 状态，切换后全局立即重组，无需重启。
 *
 * · t(source)      普通词条：中文原文为 key
 * · tf(pattern..)  带参词条：{0} {1} 占位符
 * · 回退链         当前语言 → English → 中文原文
 * · "system"       跟随系统语言（支持列表内语言，否则回退英文）
 */
object AppLocale {

    /** 语言选项（原生名 + 中文说明） */
    data class LocaleInfo(val code: String, val native: String, val zhDesc: String)

    val LOCALES: List<LocaleInfo> = listOf(
        LocaleInfo("system", "System default", "系统默认设置"),
        LocaleInfo("zh", "简体中文", "简体中文"),
        LocaleInfo("en", "English", "英语")
    )

    private val SUPPORTED = LOCALES.map { it.code }.toSet()

    /** 原始保存值（"system" 或具体语言码），用于设置页回显 */
    var savedLang: String = "zh"
        private set

    var lang by mutableStateOf("zh")
        private set

    fun apply(code: String) {
        // 旧版本遗留的繁体语言码归一为简体
        val normalized = if (code == "zh-TW" || code == "zh-HK") "zh" else code
        savedLang = normalized
        lang = if (normalized == "system") systemLang() else normalized
    }

    private fun systemLang(): String = try {
        val sys = LocaleList.getDefault()[0].language
        if (sys in SUPPORTED) sys else "en"
    } catch (_: Exception) {
        "en"
    }

    /** 翻译入口：中文原文为 key，回退 English → 原文 */
    fun t(source: String): String {
        val l = lang ?: "zh"
        if (l == "zh") return source
        return EN[source] ?: source
    }

    /** 带参翻译：{0} {1} 占位符替换 */
    fun tf(pattern: String, vararg args: Any?): String {
        var out = t(pattern)
        args.forEachIndexed { i, v -> out = out.replace("{${i}}", v?.toString() ?: "") }
        return out
    }

    // ================================================================
    // English（完整词库 · 最终回退语言）
    // ================================================================
    private val EN: Map<String, String> = mapOf(
        // ===== 通用 =====
        "设置" to "Settings", "主页" to "Home", "修改" to "Edit", "记录" to "History",
        "取消" to "Cancel", "确定" to "OK", "确认" to "Confirm", "删除" to "Delete",
        "分享" to "Share", "打开" to "Open", "保存" to "Save", "完成" to "Done",
        "我知道了" to "Got it", "再按一次退出" to "Press back again to exit",
        "复制" to "Copy", "已复制" to "Copied", "暂无" to "None", "应用" to "Apply",

        // ===== 首页 =====
        "表盘 ID 工具" to "Watchface ID Tool",
        "一键导入" to "Quick Import", "表盘目录批量导入" to "Batch import from watch dir",
        "选择文件" to "Pick File", "手动选取 .bin 文件" to "Select a .bin manually",
        "修改表盘" to "Edit Watchface", "ID 与名称修改" to "Change ID & name",
        "修改记录" to "History", "查看历史" to "View history", "暂无记录" to "No records yet",
        "权限不可用" to "Permission unavailable", "未激活权限" to "Permission inactive",
        "未检测到 Root 或 Shell 权限" to "No Root or Shell permission detected",
        "Root 权限已激活" to "Root active", "Shell 权限已激活" to "Shell active",
        "可通过 su 命令提权操作" to "Elevated via su commands",
        "Shizuku / ADB Shell 已就绪" to "Shizuku / ADB shell ready",
        "正在检测权限…" to "Checking permission…", "请稍候" to "Please wait",
        "一键导入需要 Root 或 Shell 权限。" to "Quick import requires Root or Shell permission.",
        "请通过 Shizuku 授权或确保设备已 Root。" to "Authorize via Shizuku or root your device.",
        "已导入的表盘" to "Imported watchfaces",

        // ===== 设置 =====
        "权限管理" to "Permissions", "数据管理" to "Data",
        "应用与更新" to "App & Updates", "关于" to "About",
        "检查更新" to "Check Update", "查看公告" to "Announcements",
        "有新公告" to "New announcement", "暂无公告" to "No announcement",
        "清除已导入文件" to "Clear imported files", "清空修改记录" to "Clear history",
        "语言" to "Language", "显示密度" to "Display Density",
        "背景与外观" to "Background & Appearance", "背景样式" to "Background Style",
        "背景颜色" to "Background Color", "默认壁纸" to "Default Wallpaper",
        "从相册选择" to "Pick from Gallery", "纯色背景" to "Solid Color",
        "液态动态" to "Liquid Dynamic", "自定义图片" to "Custom Image",
        "应用内置默认背景" to "Built-in default background",
        "自定义图片，自动适配屏幕比例" to "Custom image, auto fit screen",
        "自定义颜色（深色调）" to "Custom color (dark tones)",
        "渐变光斑动态背景" to "Gradient glow animated",
        "自定义纯色（保持界面可读的深色调）" to "Solid color (readable dark tones)",
        "色调" to "Hue", "饱和度" to "Saturation", "明度" to "Brightness",
        "（深色调保证可读）" to " (dark for readability)",
        "图片读取失败，请换一张试试" to "Failed to read image, try another",
        "界面与语言" to "Interface & Language",
        "系统默认设置" to "System default",
        "启动时显示公告" to "Show announcement on launch",
        "启动 App 时自动弹出新公告" to "Auto-popup new announcements at launch",
        "已是最新版本" to "Already up to date",
        "检查超时，请稍后重试" to "Timed out, try again later",
        "网络不可用或配置获取失败" to "Network unavailable or fetch failed",
        "正在检查更新…" to "Checking for updates…",
        "当前 {0} 个表盘文件" to "Current {0} watchface files",
        "当前 {0} 条记录" to "Current {0} records",
        "当前版本 v{0}" to "Version {0}",
        "共 {0} 条记录" to "{0} records",
        "80% ~ 110%，松手后界面重新加载" to "80% – 110%, UI reloads on release",
        "紧凑" to "Compact", "标准" to "Standard", "大" to "Large", "特大" to "Extra Large",
        "调整后界面将重新加载" to "UI will reload after changing",
        "Root 权限可用" to "Root available", "可批量导入 / 直接写入系统目录" to "Batch import / write system dirs",
        "Shell 权限可用" to "Shell available", "通过 Shizuku / ADB 授权" to "Via Shizuku / ADB",
        "需要 Root 或 Shell (Shizuku) 权限" to "Root or Shell (Shizuku) required",
        "重新检测" to "Re-check", "Shizuku 授权" to "Authorize Shizuku",
        "液态玻璃界面 · Barlow 字体" to "Liquid glass UI · Barlow typeface",

        // ===== 修改页 =====
        "文件信息" to "File Info", "表盘 ID" to "Watchface ID", "表盘名称" to "Watchface Name",
        "文件大小" to "Size", "文件名" to "File Name", "(空)" to "(empty)",
        "设置新 ID" to "Set New ID", "输入 9 或 12 位纯数字" to "Enter 9 or 12 digits",
        "随机 9/12" to "Random 9/12", "9 位" to "9 digits", "12 位" to "12 digits",
        "生成随机 ID" to "Generate Random ID", "保持原名" to "Keep name",
        "自定义名称" to "Custom name", "输入新的表盘名称" to "Enter new watchface name",
        "名称将写入表盘文件，留空则清除名称" to "Written into the file; empty clears it",
        "保存修改" to "Save Changes", "复制原 ID" to "Copy original ID", "复制新 ID" to "Copy new ID",
        "原 ID 已复制" to "Original ID copied", "新 ID 已复制" to "New ID copied",
        "尚未生成" to "Not generated", "暂无新 ID，请先生成" to "No new ID yet",
        "重新选择" to "Reselect", "清除当前状态" to "Clear current state",
        "已加载 · 点击重新选择" to "Loaded · Tap to reselect",
        "点击选择表盘文件" to "Tap to pick a watchface file",
        "支持 .bin 表盘文件" to ".bin watchface files only",
        "仅支持 9 位或 12 位纯数字" to "Only 9 or 12 pure digits",
        "有效的" to "Valid", "位 ID" to "-digit ID", "ID 无效" to "Invalid ID", "位" to " digits",
        "请先选择表盘文件" to "Pick a watchface file first",
        "正在处理…" to "Processing…", "正在读取文件…" to "Reading file…",
        "正在修改…" to "Modifying…", "正在扫描表盘文件…" to "Scanning watchfaces…",
        "文件加载成功" to "File loaded", "加载失败" to "Load failed", "保存失败" to "Save failed",
        "操作成功" to "Success", "操作失败" to "Failed", "已重置" to "Reset",

        // ===== 记录页 =====
        "导出记录" to "Export records", "导出修改记录" to "Export history",
        "清空记录" to "Clear all", "分享文件" to "Share file", "删除记录" to "Delete record",
        "确定要删除这条修改记录吗？" to "Delete this record?",
        "同时删除本地文件" to "Also delete local file",
        "记录和本地文件已删除" to "Record and file deleted",
        "记录已删除（本地文件不存在）" to "Record deleted (file missing)",
        "记录已删除" to "Record deleted", "记录已清空" to "History cleared",
        "今天" to "Today", "原 ID" to "Old ID", "新 ID" to "New ID",
        "原名称" to "Old name", "新名称" to "New name",

        // ===== 弹窗/公告/更新 =====
        "公 告" to "Announcement", "发现新版本" to "New version available",
        "最新版本: " to "Latest: ", "立即更新" to "Update now", "稍后再说" to "Later",
        "下载更新" to "Download Update", "下载中…" to "Downloading…", "重试" to "Retry",
        "浏览器下载" to "Open in browser", "取消下载" to "Cancel",
        "下载已取消" to "Download cancelled", "更新链接为空" to "Update URL is empty",

        // ===== 残留补全（v2：插值模板与长尾词条） =====
        // 首页/欢迎页
        "{0} 条记录" to "{0} records",
        "已导入的表盘 · {0}" to "Imported · {0}",
        "清除" to "Clear", "刷新" to "Refresh", "授权 Shizuku" to "Authorize Shizuku",
        "知道了" to "Got it", "公告" to "Announcement",
        "当前版本  v{0}" to "Current version  v{0}",
        "最新版本  v{0}" to "Latest version  v{0}",
        "下载失败" to "Download failed", "使用浏览器下载" to "Download in browser",
        "正在下载更新" to "Downloading update", "下载完成" to "Download complete",
        "未知大小" to "Unknown size", "预计剩余 {0}" to "{0} remaining",
        "正在调起安装界面…" to "Opening installer…",
        "打开浏览器失败" to "Failed to open browser",
        "重定向次数过多" to "Too many redirects",
        "创建安装 Intent 失败" to "Failed to create install intent",
        // 记录页
        "清空全部记录" to "Clear all records",
        "将删除全部 {0} 条记录，该操作不可恢复。" to
                "This will delete all {0} records. This cannot be undone.",
        "记录已清空，已删除 {0} 个本地文件" to "History cleared, {0} local files deleted",
        "记录已清空（本地文件不存在）" to "History cleared (no local files found)",
        "打开文件" to "Open file", "(未命名)" to "(unnamed)",
        "暂无修改记录" to "No records yet",
        "修改表盘 ID 后记录将显示在此" to "Records will appear here after editing IDs",
        // 修改页
        "修改 ID 与名称，导出到 Download" to "Edit ID & name, export to Download",
        "有效的 {0} 位 ID" to "Valid {0}-digit ID", "{0} 位" to "{0} digits",
        // 设置页
        "将移除全部 {0} 个已导入的表盘文件，不影响修改记录。" to
                "This will remove all {0} imported files. Records are kept.",
        "将删除全部 {0} 条修改记录，此操作不可恢复。" to
                "This will delete all {0} records. This cannot be undone.",
        "Aa 预览文字 Preview" to "Aa Preview Text",
        "色调  {0}°" to "Hue  {0}°", "饱和度  {0}%" to "Saturation  {0}%",
        "明度  {0}%（深色调保证可读）" to "Brightness  {0}% (dark for readability)",
        // 解析/权限/下载错误
        "文件格式错误: Magic 不匹配 (0x{0})" to "Bad file format: magic mismatch (0x{0})",
        "文件过小，无法解析" to "File too small to parse",
        "ID 不能为空" to "ID cannot be empty",
        "ID 必须为纯数字" to "ID must be digits only",
        "ID 位数必须为 9 或 12 位，当前 {0} 位" to "ID must be 9 or 12 digits, got {0}",
        "Shizuku 授权成功" to "Shizuku authorized",
        "Shizuku 授权被拒绝" to "Shizuku authorization denied",
        "Shizuku 未运行，请先启动 Shizuku 服务" to "Shizuku not running, start it first",
        "扫描失败: {0}" to "Scan failed: {0}",
        "请求授权失败: {0}" to "Auth request failed: {0}",
        "(解析失败)" to "(parse failed)", "未找到 .bin 文件" to "No .bin files found",
        "已导入 {0} 个表盘文件" to "Imported {0} watchface files",
        "文件过大（{0}），超过 64 MB 限制" to "File too large ({0}), 64 MB limit",
        "无法打开文件" to "Cannot open file",
        "文件不存在: Download/{0}" to "File missing: Download/{0}",
        // 结果详情标签
        "原 ID:" to "Old ID:", "新 ID:" to "New ID:",
        "原名称:" to "Old name:", "新名称:" to "New name:",
        "导出时间:" to "Exported:", "表盘名称:" to "Name:",
        "表盘 ID 修改记录" to "Watchface ID History",
        "总记录数: {0}" to "Total records: {0}",
        // 时长
        "0 秒" to "0s", "{0} 秒" to "{0}s", "{0} 分钟" to "{0}m", "{0} 分 {1} 秒" to "{0}m {1}s",
        // 密钥提取
        "密钥提取" to "Key Extraction", "日志密钥提取" to "Log Key Extraction",
        "提取AuthKey" to "Extract AuthKey",
        "请先选择日志ZIP文件" to "Please select a log ZIP file first",
        "已提取 {0} 个 Token" to "Extracted {0} Token(s)", "未找到 Token" to "No Token found",
        "提取失败" to "Extraction failed",
        "提取失败: {0}" to "Extraction failed: {0}", "提取中…" to "Extracting…",
        "从日志 ZIP 中提取 Token" to "Extract Token from log ZIP",
        "提取 Token" to "Extract Token", "正在提取 Token…" to "Extracting Token…",
        "从日志 ZIP 中提取 Token + 设备名" to "Extract Token + device name from log ZIP",
        "AuthKey 提取结果" to "AuthKey Extraction Result",
        "未在日志中找到 Token" to "No Token found in logs",
        "认证密钥" to "Auth Key", "Token令牌" to "Token", "加密密钥" to "Encrypt Key",
        "信标密钥" to "Beacon Key", "IRQ密钥" to "IRQ Key", "设备ID" to "Device ID",
        "华米认证密钥" to "Huami Auth Key", "原始文件数" to "Total files",
        "扫描日志文件数" to "Scanned log files", "密钥类型" to "Key Type",
        "复制所有密钥" to "Copy All Keys", "清空结果" to "Clear Results",
        // ---- 卡密登录（设置页 + 验证 SDK）----
        "卡密登录" to "License Login", "卡密" to "License Key", "登录" to "Log In",
        "登录中…" to "Logging in…", "已登录" to "Logged In", "未登录" to "Not Logged In",
        "点击此处输入卡密登录" to "Tap here to enter your license key",
        "输入授权卡密以解锁全部功能" to "Enter your license key to unlock all features",
        "请输入卡密" to "Please enter the license key",
        "取消解锁" to "Cancel Unlock", "处理中…" to "Processing…",
        "可使用全部功能" to "All features available",
        "卡密：{0}" to "Key: {0}", "到期：{0}" to "Expires: {0}", "，{0}" to ", {0}",
        // 授权 SDK / 服务端返回的常见错误消息
        "卡密不存在" to "License key not found",
        "卡密已过期" to "License key expired",
        "卡密已被封禁" to "License key banned",
        "卡密已被禁用" to "License key disabled",
        "登录成功{0}{1}" to "Login successful{0}{1}", "（{0}）" to " ({0})",
        "登录失败" to "Login failed", "登录异常" to "Login error",
        "心跳失败" to "Heartbeat failed", "过期" to "Expired",
        "已退出登录" to "Logged out",
        "已退出登录（{0}）" to "Logged out ({0})",
        "已退出登录（设备已解绑）{0}" to "Logged out (device unbound){0}",
        "（剩余解绑次数：{0}）" to " (unbindings left: {0})",
        "次数卡({0})" to "Count card ({0})",
        "免费卡" to "Free card", "时卡" to "Hourly card", "天卡" to "Daily card",
        "周卡" to "Weekly card", "月卡" to "Monthly card", "季卡" to "Quarterly card",
        "年卡" to "Yearly card", "永久卡" to "Lifetime card", "次数卡" to "Count card",
        "解绑失败" to "Unbind failed", "解绑异常" to "Unbind error",
        "SDK 初始化失败" to "SDK initialization failed",
        "验证未初始化：缺少 Context" to "Verification not initialized: missing Context",
        "验证已关闭（调试）" to "Verification disabled (debug)",
        "微验初始化失败: {0}" to "Verification init failed: {0}",
        "正在验证会话…" to "Verifying session…",
        "请先在设置中登录卡密后再使用此功能" to "Please log in with your license key in Settings first",
        // ---- 权限说明（设置页 + 首页）----
        "文件权限可用" to "File permission available",
        "文件权限已激活" to "File permission granted",
        "已授予所有文件访问，可无 Root 导入" to "All-files access granted; import works without Root",
        "可授权「所有文件访问」或 Root / Shizuku" to "Grant \"All files access\", or use Root / Shizuku",
        "可授权「所有文件访问」或使用 Root/Shizuku" to "Grant \"All files access\", or use Root/Shizuku",
        "通过 Shizuku / ADB 授权" to "Authorize via Shizuku / ADB",
        "Shizuku: 安装并启动 Shizuku 服务后\n点击下方按钮授权" to
                "Shizuku: install & start the Shizuku service,\nthen tap the button below to authorize",
        "一键导入需要 Root 或 Shell 权限。\n" to "One-tap import requires Root or Shell permission.\n",
        "请通过 Shizuku 授权或确保设备已 Root。\n\n" to
                "Authorize via Shizuku or make sure the device is rooted.\n\n",
        // ---- 更新弹窗（海洋风）----
        "发现软件新版本" to "New version available",
        "XiaomBP {0} 带来更稳定、更流畅的使用体验。" to
                "XiaomBP {0} brings a more stable and smoother experience.",
        "下载并安装" to "Download & Install", "稍后安装" to "Later",
        "查看详情" to "View Details", " 查看详情" to " View Details",
        "设备：{0}" to "Device: {0}", "设备：未知" to "Device: Unknown",
        "发现新版本 {0}（暂无下载地址）" to "New version {0} found (no download URL yet)",
        "\n文件已保存到 Download 目录" to "\nFile saved to the Download folder",
        // ---- 设置页其他 ----
        "Github 仓库" to "GitHub Repository", "查看开源仓库" to "View Open-Source Repo",
        "正在获取公告…" to "Loading announcement…",
        // ---- 密钥提取补充 ----
        "已提取 {0} 个 Token（{1}）" to "Extracted {0} Token(s) ({1})",
        "未找到 .bin 文件，可尝试授权 Root/Shizuku 后重试" to
                "No .bin files found; try granting Root/Shizuku and retry",
        "文件不存在: {0}" to "File not found: {0}",
        "请提供 ZIP 日志包或 .log 文件" to "Please provide a ZIP log package or .log file",
        "手环" to "Band", "手表" to "Watch", "秤" to "Scale", "黑鲨" to "Black Shark",
        // ---- 每日一言（纤句兜底语录）----
        "凡是过往，皆为序章。" to "What's past is prologue.",
        "我们的征途，是星辰大海。" to "Our journey is the sea of stars.",
        "万物皆有裂痕，那是光照进来的地方。" to "There is a crack in everything, that's how the light gets in.",
        "慢慢来，比较快。" to "Slow down and you'll get there faster.",
        "保持热爱，奔赴山海。" to "Stay passionate, chase mountains and seas.",
        "精益求精，止于至善。" to "Strive for excellence, pursue perfection.",
        "山高路远，看世界，也找自己。" to "Over high hills and long roads — see the world, find yourself.",
        "每个不曾起舞的日子，都是对生命的辜负。" to "Every day not danced is a day betrayed to life.",
        "莎士比亚" to "Shakespeare", "尼采" to "Nietzsche", "莱昂纳德·科恩" to "Leonard Cohen",
        "银河英雄传说" to "Legend of the Galactic Heroes"
    )
}


/**
 * 包内 Text 包装器：渲染前自动走 AppLocale.t()。
 * 各界面文件不再 import material3.Text，直接使用本包装器。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    M3Text(
        text = AppLocale.t(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

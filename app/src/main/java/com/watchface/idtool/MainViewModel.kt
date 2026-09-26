package com.watchface.idtool

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.watchface.idtool.ui.AppLocale

enum class PermissionStatus {
    ROOT,       // 已激活 Root 权限
    SHELL,      // 已激活 Shell / Shizuku 权限
    FILE,       // 已授予「所有文件访问权限」（可走零宽空格无 Root 扫描）
    NONE,       // 未激活任何可用权限
    CHECKING    // 检测中
}

data class ImportedFile(
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val id: String,
    val name: String,
    val isUri: Boolean = false
)

data class UiState(
    val isLoading: Boolean = false,
    val loadingText: String = "正在处理…",
    val fileInfo: WatchfaceParser.WatchfaceInfo? = null,
    val originalFileName: String = "",
    val newId: String = "",
    val selectedMode: String? = null,
    val nameMode: String = "keep",
    val customName: String = "",
    val records: List<WatchfaceRecord> = emptyList(),
    val importedFiles: List<ImportedFile> = emptyList(),
    val resultMessage: String? = null,
    val resultSuccess: Boolean = false,
    val toastMessage: String? = null,
    val permissionStatus: PermissionStatus = PermissionStatus.CHECKING,
    val showNoPermissionDialog: Boolean = false,
    // 云公告/云更新
    val cloudConfig: CloudConfigManager.CloudConfig? = null,
    val showAnnouncementDialog: Boolean = false,
    val showUpdateDialog: Boolean = false,
    /** 云配置检查进行中（检查更新可取消） */
    val isCheckingCloud: Boolean = false,
    // APK 下载
    val downloadProgress: Int = 0,
    val downloadTotalBytes: Long = 0L,
    val downloadDownloadedBytes: Long = 0L,
    val downloadSpeed: Long = 0L,
    val isDownloading: Boolean = false,
    val showDownloadProgress: Boolean = false,
    val downloadError: String? = null,
    val downloadUrl: String = "",
    // 日志密钥提取
    val isExtracting: Boolean = false,
    val extractResult: LogKeyExtractor.ExtractResult? = null,
    val extractZipPath: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 缓存的权限状态，避免重复检测 */
    @Volatile
    private var cachedPermission: PermissionStatus = PermissionStatus.CHECKING

    /** 上次权限检测时间（ON_RESUME 刷新时做节流，避免状态栏反复闪 CHECKING） */
    @Volatile
    private var lastPermissionCheckAt: Long = 0L

    /** 下载状态收集器（单例 Job，避免重复订阅造成泄漏） */
    private var downloadCollectorJob: kotlinx.coroutines.Job? = null

    /** 当前下载协程 */
    private var downloadJob: kotlinx.coroutines.Job? = null

    /** 单个表盘文件大小上限（超过则拒绝加载，防止 OOM） */
    private companion object {
        private const val WATCHFACE_DIR = "/storage/emulated/0/Android/data/com.mi.health/files/WatchFace"
        // 零宽空格路径（U+200B），用于在无 Root/Shizuku 时绕过部分系统对 Android/data 的路径校验
        private const val WATCHFACE_DIR_ZWSP = "/storage/emulated/\u200B0/Android/data/com.mi.health/files/WatchFace"
        const val MAX_WATCHFACE_BYTES = 64L * 1024 * 1024 // 64 MB
        const val PERMISSION_RECHECK_INTERVAL_MS = 10_000L
    }

    init {
        loadRecords()
        checkPermissionStatus()
        checkCloudConfig()
        // 全局唯一的下载状态收集器
        downloadCollectorJob = viewModelScope.launch {
            ApkDownloader.downloadState.collect { state ->
                onDownloadStateChanged(state)
            }
        }
    }

    /** 下载状态统一分发 */
    private fun onDownloadStateChanged(state: ApkDownloader.DownloadState) {
        when (state) {
            is ApkDownloader.DownloadState.Idle -> {}
            is ApkDownloader.DownloadState.Downloading -> {
                _uiState.value = _uiState.value.copy(
                    downloadProgress = state.progress,
                    downloadTotalBytes = state.totalBytes,
                    downloadDownloadedBytes = state.downloadedBytes,
                    downloadSpeed = state.speedBytesPerSec,
                    isDownloading = true
                )
            }
            is ApkDownloader.DownloadState.Cancelled -> {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    showDownloadProgress = false,
                    toastMessage = "下载已取消"
                )
                ApkDownloader.reset()
            }
            is ApkDownloader.DownloadState.Success -> {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgress = 100
                )
                // 自动调起安装
                val intent = ApkDownloader.installApk(getApplication(), state.file)
                if (intent != null) {
                    getApplication<Application>().startActivity(intent)
                }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    _uiState.value = _uiState.value.copy(showDownloadProgress = false)
                    ApkDownloader.reset()
                }
            }
            is ApkDownloader.DownloadState.Error -> {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadError = state.message
                )
            }
        }
    }

    // ==================== 权限检测 ====================

    fun checkPermissionStatus() {
        lastPermissionCheckAt = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(permissionStatus = PermissionStatus.CHECKING)
            val status = withContext(Dispatchers.IO) {
                detectPermission()
            }
            cachedPermission = status
            // 仅当仍是本次检测结果最新时才更新（避免旧结果覆盖新结果）
            if (System.currentTimeMillis() - lastPermissionCheckAt < 15_000L) {
                _uiState.value = _uiState.value.copy(permissionStatus = status)
            }
        }
    }

    /** ON_RESUME 时的节流刷新：距上次检测超过阈值才重新检测，避免状态闪烁 */
    fun refreshPermissionOnResume() {
        val elapsed = System.currentTimeMillis() - lastPermissionCheckAt
        if (elapsed > PERMISSION_RECHECK_INTERVAL_MS) {
            checkPermissionStatus()
        }
    }

    /** 实际权限探测（IO 线程执行） */
    private fun detectPermission(): PermissionStatus {
        // 1. 检测 Root
        val hasRoot = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(5, TimeUnit.SECONDS)
            val result = output.contains("uid=0")
            process.destroy()
            result
        } catch (_: Exception) {
            false
        }
        if (hasRoot) return PermissionStatus.ROOT

        // 2. 检测 Shizuku
        if (ShizukuExecutor.isShizukuRunning() && ShizukuExecutor.hasPermission()) {
            return PermissionStatus.SHELL
        }

        // 3. 当前进程 uid 是否为 2000 (shell 用户)
        if (android.os.Process.myUid() == 2000) {
            return PermissionStatus.SHELL
        }

        // 4. 「所有文件访问权限」—— 可用于无 Root 一键导入（零宽空格路径）
        if (hasManageExternalStorage()) {
            return PermissionStatus.FILE
        }

        return PermissionStatus.NONE
    }

    fun requestShizukuPermission() {
        try {
            if (ShizukuExecutor.isShizukuRunning()) {
                if (!ShizukuExecutor.hasPermission()) {
                    val listener = object : rikka.shizuku.Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(this)
                            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                cachedPermission = PermissionStatus.SHELL
                                _uiState.value = _uiState.value.copy(
                                    permissionStatus = PermissionStatus.SHELL,
                                    toastMessage = AppLocale.t("Shizuku 授权成功")
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    toastMessage = AppLocale.t("Shizuku 授权被拒绝")
                                )
                            }
                        }
                    }
                    rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
                    rikka.shizuku.Shizuku.requestPermission(0)
                } else {
                    cachedPermission = PermissionStatus.SHELL
                    _uiState.value = _uiState.value.copy(permissionStatus = PermissionStatus.SHELL)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    toastMessage = AppLocale.t("Shizuku 未运行，请先启动 Shizuku 服务")
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                toastMessage = AppLocale.tf("请求授权失败: {0}", e.message)
            )
        }
    }

    fun dismissNoPermissionDialog() {
        _uiState.value = _uiState.value.copy(showNoPermissionDialog = false)
    }

    fun showNoPermissionDialog() {
        _uiState.value = _uiState.value.copy(showNoPermissionDialog = true)
    }

    // ==================== 云公告 / 云更新 ====================

    /** 云配置检查协程（可被用户取消） */
    private var cloudCheckJob: kotlinx.coroutines.Job? = null

    /**
     * 检查云配置：公告和更新（两个独立弹窗，互不拼接）
     *
     * @param mode 展示模式：
     *   "announce" 手动查看公告（仅公告弹窗，无公告则 Toast）
     *   "update"   手动检查更新（仅更新弹窗，已是最新则 Toast）
     *   "auto"     启动自动流程：
     *              · 更新弹窗【永不】自动弹出，只能手动点击「检查更新」触发
     *              · 公告仅在「启动时显示公告」开关开启时弹出
     *
     * 手动模式（announce/update）检查期间 isCheckingCloud = true（检查中弹窗，可取消）；
     * auto 模式全程静默（无检查中弹窗）；
     * 网络请求 8 秒超时，超时/失败给 Toast 反馈。
     */
    fun checkCloudConfig(mode: String = "auto") {
        cloudCheckJob?.cancel()
        cloudCheckJob = viewModelScope.launch {
            // 仅手动检查显示「检查中」弹窗；启动自动检查完全静默
            if (mode != "auto") {
                _uiState.value = _uiState.value.copy(isCheckingCloud = true)
            }
            try {
                val config = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    CloudConfigManager.fetchConfig(getApplication())
                }
                if (config == null) {
                    // 超时或网络失败：手动模式下提示，自动模式静默
                    if (mode != "auto") {
                        _uiState.value = _uiState.value.copy(
                            isCheckingCloud = false,
                            toastMessage = "检查超时，请稍后重试"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isCheckingCloud = false)
                    }
                    return@launch
                }

                _uiState.value = _uiState.value.copy(cloudConfig = config)

                val hasAnnouncement = config.enabled && config.announcement.isNotEmpty()
                val hasUpdate = config.hasUpdate
                val hasUpdateUrl = config.updateUrl.isNotEmpty()

                when (mode) {
                    "announce" -> {
                        _uiState.value = if (hasAnnouncement) {
                            _uiState.value.copy(
                                isCheckingCloud = false,
                                showAnnouncementDialog = true,
                                showUpdateDialog = false
                            )
                        } else {
                            _uiState.value.copy(
                                isCheckingCloud = false,
                                toastMessage = "暂无公告"
                            )
                        }
                    }

                    "update" -> {
                        _uiState.value = when {
                            hasUpdate && hasUpdateUrl -> _uiState.value.copy(
                                isCheckingCloud = false,
                                showAnnouncementDialog = false,
                                showUpdateDialog = true
                            )
                            hasUpdate -> _uiState.value.copy(
                                isCheckingCloud = false,
                                toastMessage = "发现新版本 ${config.latestVersion}（暂无下载地址）"
                            )
                            else -> _uiState.value.copy(
                                isCheckingCloud = false,
                                toastMessage = "已是最新版本"
                            )
                        }
                    }

                    else -> {
                        // 启动自动流程：只弹公告（受开关控制），更新绝不自动弹
                        _uiState.value = _uiState.value.copy(
                            isCheckingCloud = false,
                            showAnnouncementDialog = hasAnnouncement &&
                                    AppSettings.announceAutoShow,
                            showUpdateDialog = false
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户取消：静默复位
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingCloud = false,
                    toastMessage = if (mode == "auto") null else "网络不可用或配置获取失败"
                )
            }
        }
    }

    /** 取消进行中的云配置检查（检查更新弹窗的「取消」按钮） */
    fun cancelCloudCheck() {
        cloudCheckJob?.cancel()
        cloudCheckJob = null
        _uiState.value = _uiState.value.copy(isCheckingCloud = false)
    }

    /** 关闭公告弹窗 */
    fun dismissAnnouncementDialog() {
        _uiState.value = _uiState.value.copy(
            showAnnouncementDialog = false,
            showUpdateDialog = false
        )
    }

    /** 关闭更新弹窗 */
    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            showAnnouncementDialog = false,
            showUpdateDialog = false
        )
    }

    /**
     * 开始下载更新 APK
     * 优化：下载状态由 init 中的唯一收集器分发，这里只启动下载协程，
     * 重复调用时先取消上一次下载，杜绝状态收集器泄漏。
     */
    fun startDownloadUpdate() {
        val config = _uiState.value.cloudConfig ?: return
        val url = config.updateUrl
        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(downloadError = "更新链接为空")
            return
        }

        // 取消可能存在的旧下载
        downloadJob?.cancel()
        ApkDownloader.reset()

        // 关闭公告弹窗，显示下载进度
        _uiState.value = _uiState.value.copy(
            showAnnouncementDialog = false,
            showUpdateDialog = false,
            showDownloadProgress = true,
            isDownloading = true,
            downloadProgress = 0,
            downloadError = null,
            downloadUrl = url
        )

        downloadJob = viewModelScope.launch {
            ApkDownloader.download(getApplication(), url)
        }
    }

    /** 取消当前下载 */
    fun cancelDownloadUpdate() {
        ApkDownloader.cancel()
    }

    /**
     * 下载失败时，使用系统浏览器打开下载链接（兜底方案）
     */
    fun openDownloadInBrowser() {
        val url = _uiState.value.downloadUrl.ifEmpty {
            _uiState.value.cloudConfig?.updateUrl ?: return
        }
        ApkDownloader.cancel() // 停止应用内下载
        ApkDownloader.openInBrowser(getApplication(), url)
        _uiState.value = _uiState.value.copy(
            showDownloadProgress = false,
            downloadError = null
        )
    }

    fun dismissDownloadProgress() {
        // 若仍在下载中，先取消
        if (_uiState.value.isDownloading) ApkDownloader.cancel()
        _uiState.value = _uiState.value.copy(
            showDownloadProgress = false,
            downloadError = null
        )
        ApkDownloader.reset()
    }

    // ==================== 日志密钥提取 ====================

    fun setExtractZipPath(path: String) {
        _uiState.value = _uiState.value.copy(extractZipPath = path)
    }

    fun clearExtractResult() {
        _uiState.value = _uiState.value.copy(
            extractResult = null,
            extractZipPath = ""
        )
    }

    fun extractLogKey() {
        if (!requireLogin()) return
        val zipPath = _uiState.value.extractZipPath
        if (zipPath.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                toastMessage = AppLocale.t("请先选择日志ZIP文件")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true, extractResult = null)
            try {
                val zipFile = File(zipPath)
                val extractor = LogKeyExtractor(zipFile)
                val result = withContext(Dispatchers.IO) {
                    extractor.extract()
                }
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    extractResult = result
                )
                if (result.success) {
                    val tokenCount = result.tokens.size.let { n ->
                        if (n > 0) n else result.authKeys["token"]?.size ?: 0
                    }
                    val primaryName = result.tokens.firstOrNull()?.deviceName.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        toastMessage = when {
                            tokenCount <= 0 -> AppLocale.t("未找到 Token")
                            primaryName.isNotEmpty() ->
                                AppLocale.tf("已提取 {0} 个 Token（{1}）", tokenCount, primaryName)
                            else -> AppLocale.tf("已提取 {0} 个 Token", tokenCount)
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        toastMessage = result.errorMessage ?: AppLocale.t("提取失败")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    toastMessage = AppLocale.tf("提取失败: {0}", e.message)
                )
            }
        }
    }

    // ==================== 命令执行辅助 ====================

    /** 通过当前可用权限读取文件全部内容 */
    private fun readFileWithPrivilege(path: String): ByteArray {
        // 优先 Shizuku
        try {
            if (cachedPermission == PermissionStatus.SHELL && ShizukuExecutor.hasPermission()) {
                val bytes = ShizukuExecutor.readFile(path)
                if (bytes.isNotEmpty()) return bytes
            }
        } catch (_: Exception) { }
        // Root
        if (cachedPermission == PermissionStatus.ROOT) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat \"$path\""))
                val bytes = process.inputStream.use { it.readBytes() }
                // 消费错误流防止阻塞
                process.errorStream.use { it.readBytes() }
                process.waitFor(15, TimeUnit.SECONDS)
                process.destroy()
                if (bytes.isNotEmpty()) return bytes
            } catch (_: Exception) { }
        }
        // Shell（应用自身 uid）
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "cat \"$path\""))
            val bytes = process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(15, TimeUnit.SECONDS)
            process.destroy()
            if (bytes.isNotEmpty()) return bytes
        } catch (_: Exception) { }
        // File API 兜底
        return File(path).readBytes()
    }

    /** 只读取文件头部指定字节数，大幅减少 IO */
    private fun readPartialFileWithPrivilege(path: String, byteCount: Int): ByteArray {
        // 优先 Shizuku
        try {
            if (cachedPermission == PermissionStatus.SHELL && ShizukuExecutor.hasPermission()) {
                val bytes = ShizukuExecutor.readPartialFile(path, byteCount)
                if (bytes.isNotEmpty()) return bytes
            }
        } catch (_: Exception) { }
        // Root
        if (cachedPermission == PermissionStatus.ROOT) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "dd if=\"$path\" bs=$byteCount count=1 2>/dev/null")
                )
                val bytes = process.inputStream.use { it.readBytes() }
                process.errorStream.use { it.readBytes() }
                process.waitFor(10, TimeUnit.SECONDS)
                process.destroy()
                if (bytes.isNotEmpty()) return bytes
            } catch (_: Exception) { }
        }
        // Shell
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c", "dd if=\"$path\" bs=$byteCount count=1 2>/dev/null")
            )
            val bytes = process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(10, TimeUnit.SECONDS)
            process.destroy()
            if (bytes.isNotEmpty()) return bytes
        } catch (_: Exception) { }
        // File API 兜底
        val file = File(path)
        if (file.exists()) {
            return file.inputStream().use { stream ->
                val buffer = ByteArray(minOf(byteCount, file.length().toInt()))
                var read = 0
                while (read < buffer.size) {
                    val n = stream.read(buffer, read, buffer.size - read)
                    if (n == -1) break
                    read += n
                }
                if (read < buffer.size) buffer.copyOf(read) else buffer
            }
        }
        return ByteArray(0)
    }

    // ==================== 一键导入 ====================

    /**
     * 检查是否已有「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）
     * Android 11+ 需要此权限；更低版本直接返回 true
     */
    fun hasManageExternalStorage(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 智能一键导入入口：
     * 1. 有 Root / Shizuku → 走高权限扫描
     * 2. 无高权限但已有 MANAGE_EXTERNAL_STORAGE → 使用零宽空格路径静默扫描（不弹对话框）
     * 3. 无高权限且无文件管理权限 → 返回 true，由 UI 跳转授权页
     * 4. 授权回来后再次调用本方法即可自动扫描
     *
     * @return true 表示需要 UI 跳转「所有文件访问权限」设置页；false 表示已开始扫描
     */
    fun quickImportSmart(): Boolean {
        if (!requireLogin()) return false
        // 已有 Root 或 Shizuku，直接高权限扫描
        if (cachedPermission == PermissionStatus.ROOT || cachedPermission == PermissionStatus.SHELL) {
            doQuickImportWithPrivilege()
            return false
        }

        // 已有文件管理权限（含刚授权后），用零宽空格路径扫描
        if (cachedPermission == PermissionStatus.FILE || hasManageExternalStorage()) {
            // 同步刷新缓存状态，避免 UI 仍显示「未激活」
            if (cachedPermission != PermissionStatus.FILE && hasManageExternalStorage()) {
                cachedPermission = PermissionStatus.FILE
                _uiState.value = _uiState.value.copy(permissionStatus = PermissionStatus.FILE)
            }
            doQuickImportWithoutPrivilege()
            return false
        }

        // 需要 UI 跳转「所有文件访问权限」设置页
        return true
    }

    /**
     * 兼容旧调用：直接走智能逻辑。
     * 如果需要授权，则显示原来的 NoPermissionDialog 作为兜底提示。
     */
    fun quickImportWithRoot() {
        val needAuth = quickImportSmart()
        if (needAuth) {
            // 没有文件管理权限也没有 Root/Shizuku → 弹原来的对话框（可引导用户去开 Shizuku）
            _uiState.value = _uiState.value.copy(showNoPermissionDialog = true)
        }
    }

    /** 高权限扫描（Root / Shizuku） */
    private fun doQuickImportWithPrivilege() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingText = "正在扫描表盘文件…")
            try {
                val files = withContext(Dispatchers.IO) {
                    val findCmd = "find \"$WATCHFACE_DIR\" -name \"*.bin\" -type f -exec stat -c '%n|%s|%Y' {} \\;"

                    val finalLines: List<String> = when (cachedPermission) {
                        PermissionStatus.SHELL -> {
                            val shizukuOutput = try {
                                if (ShizukuExecutor.hasPermission()) {
                                    ShizukuExecutor.execCommand(findCmd)
                                        .lines()
                                        .filter { it.isNotBlank() && it.contains("|") }
                                } else emptyList()
                            } catch (_: Exception) { emptyList() }

                            if (shizukuOutput.isNotEmpty()) shizukuOutput else {
                                execShellCommand(findCmd)
                            }
                        }
                        PermissionStatus.ROOT -> {
                            execRootCommand(findCmd)
                        }
                        else -> emptyList()
                    }

                    parseImportedLines(finalLines)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importedFiles = files,
                    toastMessage = if (files.isEmpty()) AppLocale.t("未找到 .bin 文件") else AppLocale.tf("已导入 {0} 个表盘文件", files.size)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = AppLocale.tf("扫描失败: {0}", e.message)
                )
            }
        }
    }

    /**
     * 无 Root / 无 Shizuku 的扫描逻辑：
     * 优先零宽空格路径 → 普通路径 → 直接 File API
     * 配合已授予的 MANAGE_EXTERNAL_STORAGE 使用，多数情况下可静默成功且不弹对话框
     */
    private fun doQuickImportWithoutPrivilege() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingText = "正在扫描表盘文件…")
            try {
                val files = withContext(Dispatchers.IO) {
                    // 1. 优先零宽空格路径
                    var lines = findBinFilesViaAppShell(WATCHFACE_DIR_ZWSP)
                    // 2. 回退普通路径
                    if (lines.isEmpty()) {
                        lines = findBinFilesViaAppShell(WATCHFACE_DIR)
                    }
                    // 3. 最后尝试直接 File 递归
                    if (lines.isEmpty()) {
                        lines = findBinFilesDirect(WATCHFACE_DIR_ZWSP)
                        if (lines.isEmpty()) {
                            lines = findBinFilesDirect(WATCHFACE_DIR)
                        }
                    }
                    parseImportedLines(lines)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importedFiles = files,
                    toastMessage = if (files.isEmpty()) {
                        AppLocale.t("未找到 .bin 文件，可尝试授权 Root/Shizuku 后重试")
                    } else {
                        AppLocale.tf("已导入 {0} 个表盘文件", files.size)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = AppLocale.tf("扫描失败: {0}", e.message)
                )
            }
        }
    }

    /** 把 find/stat 输出的行解析成 ImportedFile 列表（并发限制 4） */
    private suspend fun parseImportedLines(finalLines: List<String>): List<ImportedFile> = coroutineScope {
        val semaphore = java.util.concurrent.Semaphore(4)
        finalLines.map { line ->
            async {
                semaphore.acquire()
                try {
                    val parts = line.split("|")
                    val path = parts[0]
                    val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    val fileName = path.substringAfterLast("/")

                    try {
                        val header = readPartialFileWithPrivilege(path, WatchfaceParser.HEADER_SIZE)
                        val info = WatchfaceParser.parseHeader(header, size)
                        ImportedFile(
                            path = path,
                            fileName = fileName,
                            fileSize = size,
                            id = info.id,
                            name = info.name,
                            isUri = false
                        )
                    } catch (_: Exception) {
                        ImportedFile(
                            path = path,
                            fileName = fileName,
                            fileSize = size,
                            id = AppLocale.t("(解析失败)"),
                            name = "",
                            isUri = false
                        )
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().sortedByDescending { it.fileSize }
    }

    /**
     * 通过应用自身的 sh 执行 find（UID 仍是应用 UID，不依赖 Root/Shizuku）
     * 配合零宽空格路径可在部分机型上绕过 Android/data 限制
     */
    private fun findBinFilesViaAppShell(dirPath: String): List<String> {
        return try {
            val cmd = "find \"$dirPath\" -type f -name \"*.bin\" -exec stat -c '%n|%s|%Y' {} \\; 2>/dev/null"
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(15, TimeUnit.SECONDS)
            process.destroy()
            output.lines().filter { it.isNotBlank() && it.contains("|") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 直接使用 File API 递归收集 .bin 文件（最后兜底） */
    private fun findBinFilesDirect(dirPath: String): List<String> {
        val result = mutableListOf<String>()
        fun collect(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    collect(f)
                } else if (f.name.lowercase().endsWith(".bin")) {
                    // 格式与 find -exec stat 保持一致：path|size|mtime
                    result.add("${f.absolutePath}|${f.length()}|${f.lastModified() / 1000}")
                }
            }
        }
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
            collect(dir)
        }
        return result
    }

    /** 通过 Root 执行命令，确保进程清理 */
    private fun execRootCommand(cmd: String): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            // 同时消费错误流，防止进程阻塞
            process.errorStream.use { it.readBytes() }
            process.waitFor(15, TimeUnit.SECONDS)
            process.destroy()
            output.lines().filter { it.isNotBlank() && it.contains("|") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 通过 Shell 执行命令，确保进程清理 */
    private fun execShellCommand(cmd: String): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(15, TimeUnit.SECONDS)
            process.destroy()
            output.lines().filter { it.isNotBlank() && it.contains("|") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ==================== 文件加载 ====================

    /** 从 Uri 加载文件（系统文件选择器），带大小防护防止 OOM */
    fun loadFile(uri: Uri, fileName: String) {
        if (!requireLogin()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingText = "正在读取文件…")
            try {
                val data = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { stream ->
                            // 先查可用大小
                            val available = stream.available()
                            if (available > MAX_WATCHFACE_BYTES) {
                                throw Exception(AppLocale.tf("文件过大（{0}），超过 64 MB 限制", RecordStore.formatBytes(available.toLong())))
                            }
                            stream.readBytes()
                        }
                        ?: throw Exception(AppLocale.t("无法打开文件"))
                }
                if (data.size > MAX_WATCHFACE_BYTES) {
                    throw Exception(AppLocale.tf("文件过大（{0}），超过 64 MB 限制", RecordStore.formatBytes(data.size.toLong())))
                }
                val info = WatchfaceParser.parse(data)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fileInfo = info,
                    originalFileName = fileName,
                    newId = "",
                    selectedMode = null,
                    customName = "",
                    nameMode = "keep",
                    toastMessage = "文件加载成功"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultMessage = e.message ?: "加载失败",
                    resultSuccess = false
                )
            }
        }
    }

    fun loadImportedFile(file: ImportedFile) {
        if (!requireLogin()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingText = "正在读取文件…")
            try {
                val data = withContext(Dispatchers.IO) {
                    if (file.isUri) {
                        getApplication<Application>().contentResolver
                            .openInputStream(Uri.parse(file.path))!!.use { it.readBytes() }
                    } else {
                        if (file.fileSize > MAX_WATCHFACE_BYTES) {
                            throw Exception(AppLocale.tf("文件过大（{0}），超过 64 MB 限制", RecordStore.formatBytes(file.fileSize)))
                        }
                        readFileWithPrivilege(file.path)
                    }
                }
                if (data.size > MAX_WATCHFACE_BYTES) {
                    throw Exception(AppLocale.tf("文件过大（{0}），超过 64 MB 限制", RecordStore.formatBytes(data.size.toLong())))
                }
                val info = WatchfaceParser.parse(data)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fileInfo = info,
                    originalFileName = file.fileName,
                    newId = "",
                    selectedMode = null,
                    customName = "",
                    nameMode = "keep",
                    toastMessage = "文件加载成功"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultMessage = e.message ?: "加载失败",
                    resultSuccess = false
                )
            }
        }
    }

    fun clearImportedFiles() {
        _uiState.value = _uiState.value.copy(importedFiles = emptyList())
    }

    // ==================== 修改/保存 ====================

    fun selectMode(mode: String) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun selectNameMode(mode: String) {
        _uiState.value = _uiState.value.copy(nameMode = mode)
    }

    fun setNewId(id: String) {
        _uiState.value = _uiState.value.copy(newId = id.filter { it.isDigit() })
    }

    fun setCustomName(name: String) {
        _uiState.value = _uiState.value.copy(customName = name)
    }

    fun generateRandomId() {
        var mode = _uiState.value.selectedMode
        if (mode == null) {
            mode = "random"
            _uiState.value = _uiState.value.copy(selectedMode = "random")
        }
        val id = WatchfaceParser.generateRandomId(mode)
        _uiState.value = _uiState.value.copy(newId = id)
    }

    fun resetAll() {
        _uiState.value = _uiState.value.copy(
            fileInfo = null,
            originalFileName = "",
            newId = "",
            selectedMode = null,
            customName = "",
            nameMode = "keep",
            toastMessage = "已重置"
        )
    }

    private fun buildFileName(name: String, id: String): String {
        val safeName = name.ifEmpty { "watchface" }
        return "$safeName-$id.bin"
    }

    fun saveFile() {
        if (!requireLogin()) return
        val state = _uiState.value
        val info = state.fileInfo
        if (info == null) {
            _uiState.value = _uiState.value.copy(toastMessage = "请先选择表盘文件")
            return
        }
        val newId = state.newId.trim()
        val error = WatchfaceParser.validateId(newId)
        if (error != null) {
            _uiState.value = _uiState.value.copy(toastMessage = error)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingText = "正在修改…")
            try {
                val nameChanged = state.nameMode == "custom"
                val newName = if (nameChanged) state.customName else info.name
                val modified = withContext(Dispatchers.IO) {
                    WatchfaceParser.modifyId(info.data, newId, newName, nameChanged)
                }
                val fileName = buildFileName(newName, newId)
                withContext(Dispatchers.IO) {
                    WatchfaceParser.saveToDownloads(fileName, modified)
                }
                val record = WatchfaceRecord(
                    time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                    oldId = info.id,
                    newId = newId,
                    oldName = info.name,
                    newName = newName,
                    nameChanged = nameChanged,
                    fileName = state.originalFileName,
                    fileSize = info.size
                )
                RecordStore.addRecord(getApplication(), record)
                loadRecords()

                val msg = buildString {
                    append(AppLocale.t("原 ID:")).append(" ").append(info.id).append("\n")
                    append(AppLocale.t("新 ID:")).append(" ").append(newId).append("\n")
                    if (nameChanged) {
                        append(AppLocale.t("原名称:")).append(" ").append(info.name.ifEmpty { AppLocale.t("(空)") }).append("\n")
                        append(AppLocale.t("新名称:")).append(" ").append(newName.ifEmpty { AppLocale.t("(空)") }).append("\n")
                    }
                    append("\n文件已保存到 Download 目录")
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultSuccess = true,
                    resultMessage = msg
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultSuccess = false,
                    resultMessage = e.message ?: "保存失败"
                )
            }
        }
    }

    // ==================== 记录管理 ====================

    fun loadRecords() {
        val records = RecordStore.getRecords(getApplication())
        _uiState.value = _uiState.value.copy(records = records)
    }

    fun deleteRecord(index: Int, deleteFile: Boolean) {
        val records = RecordStore.getRecords(getApplication())
        if (index !in records.indices) return
        val r = records[index]
        val savedName = if (r.nameChanged) r.newName.ifEmpty { r.oldName } else r.oldName
        val fileName = buildFileName(savedName, r.newId)
        RecordStore.deleteRecord(getApplication(), index)
        loadRecords()
        if (deleteFile) {
            viewModelScope.launch {
                val deleted = withContext(Dispatchers.IO) {
                    WatchfaceParser.deleteFromDownloads(fileName)
                }
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (deleted) "记录和本地文件已删除"
                    else "记录已删除（本地文件不存在）"
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(toastMessage = "记录已删除")
        }
    }

    fun openFile(index: Int): Intent? {
        val records = RecordStore.getRecords(getApplication())
        if (index !in records.indices) return null
        val r = records[index]
        val savedName = if (r.nameChanged) r.newName.ifEmpty { r.oldName } else r.oldName
        val fileName = buildFileName(savedName, r.newId)
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(toastMessage = AppLocale.tf("文件不存在: Download/{0}", fileName))
            return null
        }
        val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareFile(index: Int): Intent? {
        val records = RecordStore.getRecords(getApplication())
        if (index !in records.indices) return null
        val r = records[index]
        val savedName = if (r.nameChanged) r.newName.ifEmpty { r.oldName } else r.oldName
        val fileName = buildFileName(savedName, r.newId)
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(toastMessage = AppLocale.tf("文件不存在: Download/{0}", fileName))
            return null
        }
        val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 清空全部修改记录
     *
     * @param deleteFile 是否同时删除 Download 目录中记录对应的本地文件
     *                   （文件名按记录的 保存名称-新ID.bin 规则重建，去重后逐个删除）
     */
    fun clearAllRecords(deleteFile: Boolean = false) {
        val records = RecordStore.getRecords(getApplication())
        RecordStore.clearAll(getApplication())
        loadRecords()
        if (deleteFile && records.isNotEmpty()) {
            viewModelScope.launch {
                var deletedCount = 0
                withContext(Dispatchers.IO) {
                    records.map { r ->
                        val savedName = if (r.nameChanged) r.newName.ifEmpty { r.oldName } else r.oldName
                        buildFileName(savedName, r.newId)
                    }.distinct().forEach { fileName ->
                        try {
                            if (WatchfaceParser.deleteFromDownloads(fileName)) deletedCount++
                        } catch (_: Exception) { }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (deletedCount > 0)
                        AppLocale.tf("记录已清空，已删除 {0} 个本地文件", deletedCount)
                    else
                        AppLocale.t("记录已清空（本地文件不存在）")
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(toastMessage = AppLocale.t("记录已清空"))
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(resultMessage = null)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    /** 显示与主题一致的应用内 Toast（不要用系统 Toast.makeText） */
    fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
    }

    /**
     * 功能门禁：未登录时提示并返回 false（界面可进入，但核心功能不可用）
     */
    fun requireLogin(): Boolean {
        if (!SagConfig.ENABLED) return true
        if (SagAuthManager.isLoggedIn) return true
        showToast("请先在设置中登录卡密后再使用此功能")
        return false
    }

    override fun onCleared() {
        // 页面销毁时停止下载并释放收集器
        ApkDownloader.cancel()
        downloadCollectorJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }
}
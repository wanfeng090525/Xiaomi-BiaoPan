package com.watchface.idtool

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * APK 下载管理器（深度优化版）
 *
 * 支持进度跟踪、重定向（含 HTML 实体修复）、Cookie 管理、文件保存、安装调起。
 *
 * 本版优化点：
 * 1. 支持任意时刻取消下载（cancel()），下载循环内快速响应
 * 2. 网络抖动自动重试（IOException 时退避重试一次，支持断点续传 Range）
 * 3. 实时速度计算与预估剩余时间（由 UI 层展示）
 * 4. 原子写入：先写 .tmp 再重命名，避免半成品 APK 被调起安装
 *
 * 关键修复：QQ urlshare.cn 重定向返回的 Location 头中 URL 参数使用 &amp; 而非 &，
 * 导致 CDN 无法识别参数返回 403。此处手动修复该编码问题。
 */
object ApkDownloader {

    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 60000
    private const val MAX_REDIRECTS = 8
    private const val BUFFER_SIZE = 16384
    private const val APK_FILE_NAME = "watchface_id_tool_update.apk"
    private const val TMP_SUFFIX = ".tmp"

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(
            val progress: Int,          // -1 表示未知总大小（不确定进度）
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBytesPerSec: Long = 0
        ) : DownloadState()
        data class Cancelled(val downloadedBytes: Long) : DownloadState()
        data class Success(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /** 取消标记：置位后下载循环在下一个读取块后停止 */
    private val cancelledFlag = AtomicBoolean(false)

    init {
        // 启用全局 Cookie 管理，跨重定向保持 Cookie
        if (CookieHandler.getDefault() == null) {
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            CookieHandler.setDefault(cookieManager)
        }
    }

    /** 重置下载状态（同时清除取消标记） */
    fun reset() {
        cancelledFlag.set(false)
        _downloadState.value = DownloadState.Idle
    }

    /** 取消正在进行的下载 */
    fun cancel() {
        cancelledFlag.set(true)
    }

    /** 是否已请求取消 */
    fun isCancelled(): Boolean = cancelledFlag.get()

    /**
     * 修复 URL 中的 HTML 实体编码
     * QQ urlshare.cn 重定向返回的 Location 中 &amp; 应为 &
     */
    private fun fixHtmlEntities(url: String): String {
        return url
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    /**
     * 下载 APK 文件，带进度跟踪 / 取消 / 自动重试
     */
    suspend fun download(context: Context, urlStr: String) {
        cancelledFlag.set(false)
        _downloadState.value = DownloadState.Downloading(0, 0L, 0L)

        // 入口即修复 HTML 实体（T3 后台/部分 CDN 返回的 upurl 常带 &amp;）
        val fixedUrl = fixHtmlEntities(urlStr.trim())

        val outputFile = File(context.cacheDir, APK_FILE_NAME)
        val tmpFile = File(context.cacheDir, APK_FILE_NAME + TMP_SUFFIX)
        val maxRetries = 2

        try {
            var attempt = 0
            while (true) {
                try {
                    val resumeFrom = if (attempt > 0) tmpFile.length() else 0L
                    val complete = withContext(Dispatchers.IO) {
                        downloadOnce(fixedUrl, outputFile, tmpFile, resumeFrom)
                    }
                    if (complete) {
                        _downloadState.value = DownloadState.Success(outputFile)
                        return
                    }
                    // complete == false 且未抛异常 → 用户取消
                    _downloadState.value = DownloadState.Cancelled(tmpFile.length())
                    return
                } catch (e: Exception) {
                    attempt++
                    if (cancelledFlag.get()) {
                        _downloadState.value = DownloadState.Cancelled(tmpFile.length())
                        return
                    }
                    if (attempt > maxRetries) throw e
                    val backoffMs = 1500L * attempt
                    kotlinx.coroutines.delay(backoffMs)
                }
            }
        } catch (e: Throwable) {
            tmpFile.delete()
            _downloadState.value = DownloadState.Error(e.message ?: "下载失败")
        }
    }

    /**
     * 单次下载尝试（含重定向处理）。
     * @return true 下载完成；false 用户取消（抛出异常则为失败，由上层重试）
     */
    private fun downloadOnce(
        urlStr: String,
        outputFile: File,
        tmpFile: File,
        resumeFrom: Long
    ): Boolean {
        var currentUrl = urlStr
        var referer = urlStr
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = false

                // 完整的浏览器请求头
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9," +
                            "application/vnd.android.package-archive,application/octet-stream,*/*;q=0.8"
                )
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Referer", referer)
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Upgrade-Insecure-Requests", "1")
                // 断点续传
                if (resumeFrom > 0) {
                    setRequestProperty("Range", "bytes=$resumeFrom-")
                }
            }

            try {
                conn.connect()
                val responseCode = conn.responseCode

                // 处理重定向
                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrEmpty()) throw Exception("重定向地址为空")

                    // 关键修复：QQ urlshare.cn 返回的 Location 中 &amp; 应为 &
                    val fixedLocation = fixHtmlEntities(location)
                    val newUrl = URL(url, fixedLocation).toString()

                    referer = currentUrl
                    currentUrl = newUrl
                    redirectCount++
                    continue
                }

                // 206 = 断点续传成功；200 = 全新下载（服务器不支持 Range 时忽略本地半成品）
                if (responseCode == 200 && resumeFrom > 0) {
                    tmpFile.delete() // 服务器不支持续传，重新开始
                }
                if (responseCode != 200 && responseCode != 206) {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) {
                        null
                    }
                    conn.disconnect()
                    throw Exception("服务器返回 $responseCode${if (!errorBody.isNullOrEmpty()) ": ${errorBody.take(200)}" else ""}")
                }

                val contentLength = conn.contentLengthLong
                val alreadyDownloaded = if (responseCode == 206) resumeFrom else 0L
                val totalBytes = if (contentLength > 0) contentLength + alreadyDownloaded else -1L

                var downloadedBytes = alreadyDownloaded
                var lastProgress = if (totalBytes > 0)
                    ((downloadedBytes * 100) / totalBytes).toInt() else -1
                var lastSpeedSample = System.currentTimeMillis()
                var lastSpeedBytes = downloadedBytes
                var speed = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile, responseCode == 206).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (cancelledFlag.get()) {
                                conn.disconnect()
                                return false
                            }
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            // 每秒采样一次速度
                            val now = System.currentTimeMillis()
                            if (now - lastSpeedSample >= 500) {
                                val dt = (now - lastSpeedSample) / 1000.0
                                speed = ((downloadedBytes - lastSpeedBytes) / dt).toLong()
                                lastSpeedSample = now
                                lastSpeedBytes = downloadedBytes
                            }

                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else {
                                -1
                            }

                            // 进度变化或每 500ms（速度刷新）时发射状态
                            if (progress != lastProgress || now - lastSpeedSample < 10) {
                                lastProgress = progress
                                _downloadState.value = DownloadState.Downloading(
                                    progress = progress.coerceIn(0, 100),
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed
                                )
                            }
                        }
                    }
                }
                conn.disconnect()

                // 原子替换：tmp → 正式文件
                if (outputFile.exists()) outputFile.delete()
                if (!tmpFile.renameTo(outputFile)) {
                    // 重命名失败（跨文件系统等场景），退回复制
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                }

                return true

            } catch (e: Throwable) {
                conn.disconnect()
                throw e
            }
        }

        throw Exception("重定向次数过多")
    }

    /**
     * 使用系统浏览器打开下载链接（兜底方案）
     */
    fun openInBrowser(context: Context, url: String) {
        try {
            val fixed = fixHtmlEntities(url.trim())
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
        }
    }

    /**
     * 调起系统安装界面
     */
    fun installApk(context: Context, file: File): Intent? {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Throwable) {
            null
        }
    }
}

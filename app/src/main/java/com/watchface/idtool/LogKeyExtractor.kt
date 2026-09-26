package com.watchface.idtool

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * 从小米健康 / 手表日志 ZIP 中提取 Token，并尽量识别对应设备名称。
 *
 * 优化点：
 * 1. 仅提取 token（不扫 authKey / encryptKey 等）
 * 2. 修复 .log.bak.* 等备份日志无法匹配的问题
 * 3. 不整包解压到磁盘，ZipFile 流式按行扫描
 * 4. 主日志权重高于 bak，优先当前有效 token
 * 5. 通过滑动窗口关联同一 JSON 段中的设备 name / model
 * 6. 兼容直接传入单个 .log 文件
 */
class LogKeyExtractor(private val inputFile: File) {

    /** 单个 Token 及其关联设备信息 */
    data class TokenInfo(
        val token: String,
        /** 设备显示名，如 "REDMI Watch 6"；未知时为空 */
        val deviceName: String = "",
        /** 型号，如 "miwear.watch.p65"；未知时为空 */
        val model: String = "",
        /** 加权出现次数（主日志权重更高） */
        val score: Int = 0
    )

    data class ExtractResult(
        val success: Boolean,
        val errorMessage: String? = null,
        /**
         * 兼容旧 UI：key 固定为 "token"，value 为按优先级降序的 token 字符串列表
         */
        val authKeys: Map<String, List<String>> = emptyMap(),
        /** 带设备信息的完整列表（按优先级降序） */
        val tokens: List<TokenInfo> = emptyList(),
        val rawFilesCount: Int = 0,
        val scannedFilesCount: Int = 0
    )

    private companion object {
        private val TOKEN_PATTERN: Pattern = Pattern.compile(
            "\"token\"\\s*:\\s*\"([a-fA-F0-9]{32})\""
        )
        private val NAME_PATTERN: Pattern = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+)\""
        )
        /** productName 常出现在 token 之前的产品信息块中，作为设备名的可靠来源 */
        private val PRODUCT_NAME_PATTERN: Pattern = Pattern.compile(
            "\"productName\"\\s*:\\s*\"([^\"]+)\""
        )
        private val MODEL_PATTERN: Pattern = Pattern.compile(
            "\"model\"\\s*:\\s*\"([^\"]+)\""
        )

        /** 历史滑动窗口行数 */
        private const val WINDOW_SIZE = 48
        /** 命中 token 后向前再读的行数（name/model 常在 token 之后） */
        private const val LOOKAHEAD = 20

        /** 主日志命中权重；bak 命中权重 */
        private const val WEIGHT_MAIN = 5
        private const val WEIGHT_BAK = 1

        private fun isLogCandidate(name: String): Boolean {
            val n = name.lowercase()
            return n.contains(".log") || (n.contains("log") && n.endsWith(".txt"))
        }

        private fun isBackupLog(name: String): Boolean {
            val n = name.lowercase()
            return n.contains(".bak") || Regex(""".*\.log\.\d+$""").matches(n)
        }

        /**
         * 判断 name 是否像真实设备名（过滤掉「联系朋友」「勤喝水」等无关字符串）
         */
        private fun looksLikeDeviceName(name: String): Boolean {
            val n = name.trim()
            if (n.isEmpty() || n.equals("null", true)) return false
            if (n.length < 3 || n.length > 64) return false
            // 常见设备关键词
            val keywords = listOf(
                "watch", "band", "bracelet", "scale", "秤", "手环", "手表",
                "redmi", "xiaomi", "mi ", "miui", "haylou", "amazfit", "huami",
                "poco", "black shark", "黑鲨"
            )
            val lower = n.lowercase()
            if (keywords.any { lower.contains(it) }) return true
            // 型号风格：含点的 model 不当作 name，这里只处理 name 字段
            // 若包含中英文数字组合且不太像普通句子
            return n.any { it.isDigit() } && n.any { it.isLetter() }
        }

        private fun looksLikeModel(model: String): Boolean {
            val m = model.trim()
            if (m.isEmpty() || m.equals("null", true)) return false
            // 小米健康常见型号：miwear.watch.xxx / zhizao.watch.xxx 等
            return m.contains('.') || m.lowercase().contains("watch") || m.lowercase().contains("band")
        }

        /** 常见小米/红米手表型号 → 显示名（日志缺 name 时的兜底） */
        private val KNOWN_MODEL_NAMES = mapOf(
            "miwear.watch.p65" to "REDMI Watch 6",
            "miwear.watch.m67" to "Xiaomi Watch S4",
            "miwear.watch.n67" to "Xiaomi Watch S4 Sport",
            "zhizao.watch.n67" to "Xiaomi Watch S4 Sport",
            "miwear.watch.l62" to "Redmi Watch 4",
            "miwear.watch.m62" to "Xiaomi Watch S3",
            "miwear.watch.n62" to "Xiaomi Watch S3"
        )
    }

    fun extract(): ExtractResult {
        if (!inputFile.exists()) {
            return ExtractResult(
                success = false,
                errorMessage = "文件不存在: ${inputFile.absolutePath}"
            )
        }
        return try {
            when {
                inputFile.name.endsWith(".zip", ignoreCase = true) -> extractFromZip(inputFile)
                isLogCandidate(inputFile.name) -> extractFromSingleLog(inputFile)
                else -> ExtractResult(
                    success = false,
                    errorMessage = "请提供 ZIP 日志包或 .log 文件"
                )
            }
        } catch (e: Exception) {
            ExtractResult(success = false, errorMessage = e.message ?: "提取失败")
        }
    }

    private fun extractFromSingleLog(file: File): ExtractResult {
        val agg = TokenAggregator()
        val isBak = isBackupLog(file.name)
        scanStream(file.inputStream(), agg, isBak)
        return buildResult(agg, rawFilesCount = 1, scannedFilesCount = 1)
    }

    private fun extractFromZip(zipFile: File): ExtractResult {
        val agg = TokenAggregator()
        var rawCount = 0
        var scannedCount = 0

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            rawCount = entries.count { !it.isDirectory }
            // 优先扫描非 bak，便于窗口内先落到当前设备信息
            val sortedEntries = entries
                .filter { !it.isDirectory }
                .sortedBy { isBackupLog(it.name.substringAfterLast('/')) }

            for (entry in sortedEntries) {
                val baseName = entry.name.substringAfterLast('/')
                if (!isLogCandidate(baseName)) continue
                scannedCount++
                val isBak = isBackupLog(baseName)
                zip.getInputStream(entry).use { input ->
                    scanStream(input, agg, isBak)
                }
            }
        }
        return buildResult(agg, rawFilesCount = rawCount, scannedFilesCount = scannedCount)
    }

    /**
     * 按行扫描：
     * - 维护历史滑动窗口（token 之前的上下文）
     * - 命中 token 后再向前多读若干行（name/model 通常在 token 后面）
     */
    private fun scanStream(
        input: java.io.InputStream,
        agg: TokenAggregator,
        isBak: Boolean
    ) {
        val weight = if (isBak) WEIGHT_BAK else WEIGHT_MAIN
        val history = ArrayDeque<String>(WINDOW_SIZE)

        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line!!
                if (history.size >= WINDOW_SIZE) history.removeFirst()
                history.addLast(current)

                val matcher = TOKEN_PATTERN.matcher(current)
                var matched = false
                while (matcher.find()) {
                    matched = true
                    val value = matcher.group(1)?.lowercase() ?: continue
                    if (value == "null" || value.isEmpty()) continue

                    // 向前再读 LOOKAHEAD 行（name/model 在 token 后面几行）
                    repeat(LOOKAHEAD) {
                        val next = reader.readLine() ?: return@repeat
                        if (history.size >= WINDOW_SIZE) history.removeFirst()
                        history.addLast(next)
                    }
                    val (deviceName, model) = findDeviceInWindow(history)
                    agg.add(value, deviceName, model, weight)
                }
            }
        }
    }

    /** 在上下文窗口中查找最像设备的 name / model */
    private fun findDeviceInWindow(window: Collection<String>): Pair<String, String> {
        var name = ""
        var model = ""
        // 从后往前扫（靠近 token 的字段优先）
        for (line in window.reversed()) {
            if (name.isEmpty()) {
                // 优先 device.name，其次 productName
                val nm = NAME_PATTERN.matcher(line)
                if (nm.find()) {
                    val candidate = nm.group(1)?.trim().orEmpty()
                    if (looksLikeDeviceName(candidate)) {
                        name = candidate
                    }
                }
                if (name.isEmpty()) {
                    val pm = PRODUCT_NAME_PATTERN.matcher(line)
                    if (pm.find()) {
                        val candidate = pm.group(1)?.trim().orEmpty()
                        if (looksLikeDeviceName(candidate)) {
                            name = candidate
                        }
                    }
                }
            }
            if (model.isEmpty()) {
                val mm = MODEL_PATTERN.matcher(line)
                if (mm.find()) {
                    val candidate = mm.group(1)?.trim().orEmpty()
                    if (looksLikeModel(candidate)) {
                        model = candidate
                    }
                }
            }
            if (name.isNotEmpty() && model.isNotEmpty()) break
        }
        return name to model
    }

    private fun buildResult(
        agg: TokenAggregator,
        rawFilesCount: Int,
        scannedFilesCount: Int
    ): ExtractResult {
        val list = agg.toSortedList()
        return ExtractResult(
            success = true,
            authKeys = if (list.isEmpty()) emptyMap() else mapOf("token" to list.map { it.token }),
            tokens = list,
            rawFilesCount = rawFilesCount,
            scannedFilesCount = scannedFilesCount
        )
    }

    fun getPrimaryAuthKey(result: ExtractResult): String? {
        return result.tokens.firstOrNull()?.token ?: result.authKeys["token"]?.firstOrNull()
    }

    /** 聚合：同一 token 累加分数，并保留「最好」的设备信息 */
    private class TokenAggregator {
        private data class Acc(
            var score: Int = 0,
            var deviceName: String = "",
            var model: String = ""
        )

        private val map = linkedMapOf<String, Acc>()

        fun add(token: String, deviceName: String, model: String, weight: Int) {
            val acc = map.getOrPut(token) { Acc() }
            acc.score += weight
            // 优先保留非空、且更像设备的名称（后写入的主日志会覆盖空值）
            if (deviceName.isNotEmpty()) {
                if (acc.deviceName.isEmpty() || weight >= WEIGHT_MAIN) {
                    acc.deviceName = deviceName
                }
            }
            if (model.isNotEmpty()) {
                if (acc.model.isEmpty() || weight >= WEIGHT_MAIN) {
                    acc.model = model
                }
            }
        }

        fun toSortedList(): List<TokenInfo> {
            return map.entries
                .sortedByDescending { it.value.score }
                .map { (token, acc) ->
                    var name = acc.deviceName.trim()
                    val model = acc.model.trim()
                    // 若日志里没解析到 name，用常见型号表兜底
                    if (name.isEmpty() && model.isNotEmpty()) {
                        name = KNOWN_MODEL_NAMES[model] ?: ""
                    }
                    TokenInfo(
                        token = token,
                        deviceName = name,
                        model = model,
                        score = acc.score
                    )
                }
        }
    }
}

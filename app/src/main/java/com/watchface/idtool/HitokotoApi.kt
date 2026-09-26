package com.watchface.idtool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 一言（Hitokoto）API
 *
 * · 官方接口：https://v1.hitokoto.cn/?c=d&c=e&c=i&max_length=24
 *   （文学 / 影视 / 诗词分类，短句优先，与工具类应用气质相符）
 * · 5 秒超时；网络失败时回退到内置精选句子（随机一条），保证任何情况
 *   下主页都有内容展示
 * · 返回的 text/from 可能含 HTML 实体（&amp; 等），统一反转义
 */
object HitokotoApi {

    data class Hitokoto(val text: String, val from: String) {
        /** 完整引用串：「句子」——《 来源 》 */
        val attributed: String
            get() = if (from.isBlank()) text else "$text —— $from"
    }

    /** 内置回退句子（断网或接口异常时随机展示） */
    private val FALLBACKS = listOf(
        Hitokoto("凡是过往，皆为序章。", "莎士比亚"),
        Hitokoto("我们的征途，是星辰大海。", "银河英雄传说"),
        Hitokoto("慢慢来，比较快。", ""),
        Hitokoto("万物皆有裂痕，那是光照进来的地方。", "莱昂纳德·科恩"),
        Hitokoto("山高路远，看世界，也找自己。", ""),
        Hitokoto("精益求精，止于至善。", ""),
        Hitokoto("保持热爱，奔赴山海。", ""),
        Hitokoto("每个不曾起舞的日子，都是对生命的辜负。", "尼采")
    )

    private fun fallback(): Hitokoto = FALLBACKS.random()

    /** 拉取一条一言；失败返回内置句子（永不返回 null） */
    suspend fun fetch(): Hitokoto = withContext(Dispatchers.IO) {
        val remote = withTimeoutOrNull(5_000L) { fetchRemote() }
        remote ?: fallback()
    }

    private fun fetchRemote(): Hitokoto? {
        var conn: HttpURLConnection? = null
        return try {
            // 分类：d 文学 / e 影视 / i 诗词，长度限制 24 字以内保证排版优雅
            val qs = "c=d&c=e&c=i&max_length=24&encode=json" +
                    "&charset=" + URLEncoder.encode("utf-8", "UTF-8")
            conn = (URL("https://v1.hitokoto.cn/?$qs").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                // 手动声明避免透明 gzip 差异
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "WatchfaceIdTool/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) return fallback()
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val text = unescape(json.optString("hitokoto", "") ?: "").trim()
            if (text.isEmpty()) return fallback()
            val fromRaw = unescape(json.optString("from", "") ?: "").trim()
            val who = unescape(json.optString("from_who", "") ?: "").trim()
            val from = buildString {
                if (fromRaw.isNotBlank()) {
                    append("《")
                    append(fromRaw)
                    append("》")
                }
                if (who.isNotBlank()) {
                    if (this.isNotEmpty()) append(" ")
                    append(who)
                }
            }
            Hitokoto(text, from)
        } catch (_: Exception) {
            fallback()
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** 反转义常见 HTML 实体 */
    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

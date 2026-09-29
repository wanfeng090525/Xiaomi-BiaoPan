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
        /** 完整引用串：中文「句子 —— 《来源》」/ 英文「Sentence — Source」 */
        val attributed: String
            get() {
                if (from.isBlank()) return text
                return if (com.watchface.idtool.ui.AppLocale.lang == "en") "$text — $from"
                else "$text —— $from"
            }
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

    /** 英文模式内置句子：一律用名言的英文原文（不回译），来源标注原文出处 */
    private val FALLBACKS_EN = listOf(
        Hitokoto("What's past is prologue.", "Shakespeare · The Tempest"),
        Hitokoto("Our conquest is the sea of stars.", "Legend of the Galactic Heroes"),
        Hitokoto("Less, but better.", "Dieter Rams"),
        Hitokoto("There is a crack in everything, that's how the light gets in.", "Leonard Cohen · Anthem"),
        Hitokoto("Not all those who wander are lost.", "J.R.R. Tolkien"),
        Hitokoto("Perfection is achieved, not when there is nothing more to add, but when there is nothing left to take away.", "Antoine de Saint-Exupéry"),
        Hitokoto("Stay hungry, stay foolish.", "Steve Jobs"),
        Hitokoto("And those who were seen dancing were thought to be insane by those who could not hear the music.", "Nietzsche"),
        Hitokoto("Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
        Hitokoto("The best way to predict the future is to invent it.", "Alan Kay"),
        Hitokoto("We are all in the gutter, but some of us are looking at the stars.", "Oscar Wilde"),
        Hitokoto("It always seems impossible until it's done.", "Nelson Mandela"),
        Hitokoto("In the middle of difficulty lies opportunity.", "Albert Einstein"),
        Hitokoto("Whatever you do, do it well.", "Walt Disney"),
        Hitokoto("The only way to do great work is to love what you do.", "Steve Jobs"),
        Hitokoto("Do not go gentle into that good night.", "Dylan Thomas"),
        Hitokoto("Quality is not an act, it is a habit.", "Aristotle"),
        Hitokoto("The journey of a thousand miles begins with a single step.", "Lao Tzu")
    )

    /** 断网/异常兜底：按当前语言取内置句子 */
    private fun fallback(): Hitokoto =
        if (com.watchface.idtool.ui.AppLocale.lang == "en") FALLBACKS_EN.random()
        else FALLBACKS.random()

    /** 拉取一条一言；英文模式直接用内置英文句（hitokoto.cn 只提供中文），失败返回内置句子（永不返回 null） */
    suspend fun fetch(): Hitokoto = withContext(Dispatchers.IO) {
        if (com.watchface.idtool.ui.AppLocale.lang == "en") return@withContext fallback()
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
            // org.json 的 optString 在字段为 JSON null 时返回字符串 "null"，需还原为空
            fun opt(key: String): String =
                unescape(json.optString(key, "") ?: "").trim().takeIf { it != "null" } ?: ""
            val text = opt("hitokoto")
            if (text.isEmpty()) return fallback()
            val fromRaw = opt("from")
            val who = opt("from_who")
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

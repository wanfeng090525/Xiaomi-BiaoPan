package com.watchface.idtool

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.weiyan.sdk.WYHeartbeatResult
import com.weiyan.sdk.WYLoginResult
import com.weiyan.sdk.WYNoticeResult
import com.weiyan.sdk.WYUnbindResult
import com.weiyan.sdk.WYVerify
import com.weiyan.sdk.WYVersionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 微验卡密登录 / 解绑 / 公告 / 更新 / 心跳
 *
 * 底层调用 libwyverify.so（com.weiyan.sdk.WYVerify）：
 * 网络验证链接/接口调用码/协议密钥均以密文编译在 .so 内，
 * 必须使用授权密钥（WY_KEY）才能调用，错误时所有接口返回"密钥错误"。
 * 所有接口为同步阻塞调用，均置于 IO 线程执行。
 */
object SagAuthManager {

    /** 授权密钥：必须与 libwyverify.so 内置密钥一致 */
    private const val WY_KEY = "wanfeng"

    /** 心跳间隔（毫秒）：30 秒一次 */
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    /** 心跳失败重试的默认间隔（毫秒） */
    private const val HEARTBEAT_RETRY_MS = 10_000L

    private const val PREF = "weiyan_auth_v1"
    private const val KEY_KAMI = "kami"
    private const val KEY_TOKEN = "token"
    private const val KEY_END = "end_time"

    @Volatile
    private var wy: WYVerify? = null

    @Volatile
    var isLoggedIn: Boolean = false
        private set

    @Volatile
    var endTime: String = ""
        private set

    /** 卡密类型中文名（永久卡/天卡/次数卡/...），由 .so 内映射生成 */
    @Volatile
    var cardTypeName: String = ""
        private set

    /** 心跳在线人数（.so 心跳响应 msg.onlinenum） */
    @Volatile
    var onlineNum: String = ""
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var currentKamiMasked: String = ""
        private set

    private var currentKami: String = ""
    private var currentToken: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var restoreJob: Job? = null

    @Volatile
    private var restoring = false

    @Volatile
    var isRestoringSession: Boolean = false
        private set

    private val _restoreState = MutableStateFlow(false)
    val restoreState: StateFlow<Boolean> = _restoreState

    private val _loginState = MutableStateFlow(false)
    val loginState: StateFlow<Boolean> = _loginState

    private fun setLoginState(value: Boolean) {
        isLoggedIn = value
        _loginState.value = value
    }

    fun isConfigured(): Boolean = true

    @Synchronized
    fun init(context: Context? = null): Boolean {
        if (!SagConfig.ENABLED) return true
        if (wy != null) return true
        if (context == null) {
            lastError = "验证未初始化：缺少 Context"
            return false
        }
        return try {
            // 授权密钥错误时实例未授权，所有接口返回"密钥错误"
            wy = WYVerify(WY_KEY)
            true
        } catch (e: Throwable) {
            lastError = "微验初始化失败: ${e.message}"
            false
        }
    }

    fun ensureInit(context: Context): WYVerify? {
        if (!init(context)) return null
        return wy
    }

    @SuppressLint("HardwareIds")
    fun getMachineCode(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        return md5((androidId ?: "unknown") + "|" + context.packageName).uppercase()
    }

    private fun md5(s: String): String {
        val dig = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return dig.joinToString("") { "%02x".format(it) }
    }

    private fun maskKami(kami: String): String {
        if (kami.length <= 8) return "****"
        return kami.take(4) + "****" + kami.takeLast(4)
    }

    @SuppressLint("HardwareIds")
    private fun deriveKey(context: Context): ByteArray {
        val seed = (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "id") + "|" + context.packageName + "|weiyan"
        return MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    }

    private fun encryptKami(context: Context, plain: String): String {
        val key = SecretKeySpec(deriveKey(context), "AES")
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + enc.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(enc, 0, out, iv.size, enc.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decryptKami(context: Context, blob: String): String? {
        return try {
            val all = Base64.decode(blob, Base64.NO_WRAP)
            if (all.size < 17) return null
            val iv = all.copyOfRange(0, 16)
            val data = all.copyOfRange(16, all.size)
            val key = SecretKeySpec(deriveKey(context), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    data class AuthResult(
        val success: Boolean,
        val message: String,
        val endTime: String = "",
        val statecode: String = ""
    )

    /** 到期时间戳(秒) -> "yyyy-MM-dd HH:mm:ss" */
    private fun formatEndTime(ts: Long): String {
        if (ts <= 0) return ""
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts * 1000L))
        } catch (_: Exception) {
            ts.toString()
        }
    }

    /** 卡密时长类型中文名兜底（正常情况下 .so 已返回 kmtypeName/timetypeName） */
    private fun cardTypeText(kmtype: String): String = when (kmtype) {
        "free" -> "免费卡"
        "hour" -> "时卡"
        "day" -> "天卡"
        "week" -> "周卡"
        "month" -> "月卡"
        "season" -> "季卡"
        "year" -> "年卡"
        "longuse" -> "永久卡"
        "single" -> "次数卡"
        else -> ""
    }

    // ================= 心跳 =================

    /** 启动自动心跳：每 30 秒一次；令牌过期(code=105)时自动重新登录换取新 token */
    fun startHeartbeat(appContext: Context) {
        val ctx = appContext.applicationContext
        if (!SagConfig.ENABLED) return
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isLoggedIn) continue
                try {
                    heartbeatOnce(ctx)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun heartbeatOnce(context: Context) {
        val kami = currentKami
        val token = currentToken
        if (kami.isEmpty() || token.isEmpty()) return
        val v = ensureInit(context) ?: return
        val r: WYHeartbeatResult = withContext(Dispatchers.IO) {
            v.heartbeat(kami, getMachineCode(context), token)
        }
        when {
            // 数据过期/令牌失效：自动重新登录换取新 token
            r.code == 105L || (!r.success && r.msg?.contains("过期") == true) -> {
                val login = doLogin(context, kami, fromRestore = false)
                if (!login.success) {
                    // 重登失败（卡密失效/被封等）：登出
                    logout(context)
                    lastError = login.message
                }
            }
            r.success -> {
                endTime = formatEndTime(r.endTime)
                if (r.timetypeName?.isNotEmpty() == true) cardTypeName = r.timetypeName
                if (r.onlinenum?.isNotEmpty() == true) onlineNum = r.onlinenum
                lastError = ""
            }
            else -> {
                // 网络/服务器异常：保留登录态，稍后重试
                lastError = (r.msg ?: "").ifBlank { "心跳失败" }
                delay(HEARTBEAT_RETRY_MS)
            }
        }
    }

    // ================= 会话恢复 =================

    suspend fun restoreSession(context: Context) {
        if (!SagConfig.ENABLED) {
            setLoginState(true)
            isRestoringSession = false
            return
        }
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val kamiBlob = sp.getString(KEY_KAMI, "") ?: ""
        val kami = decryptKami(context, kamiBlob) ?: kamiBlob
        if (kami.isEmpty()) {
            isRestoringSession = false
            return
        }
        currentKami = kami
        currentKamiMasked = maskKami(kami)
        currentToken = sp.getString(KEY_TOKEN, "") ?: ""

        // 优先用保存的 token 心跳验证恢复会话；令牌失效则重新登录
        var ok = false
        val v = ensureInit(context)
        if (v != null && currentToken.isNotEmpty()) {
            val r = withContext(Dispatchers.IO) {
                v.heartbeat(kami, getMachineCode(context), currentToken)
            }
            if (r.success) {
                // 心跳验证通过：恢复登录态并重启心跳，否则 UI 仍显示未登录
                setLoginState(true)
                startHeartbeat(context)
                endTime = formatEndTime(r.endTime)
                if (r.timetypeName?.isNotEmpty() == true) cardTypeName = r.timetypeName
                if (r.onlinenum?.isNotEmpty() == true) onlineNum = r.onlinenum
                lastError = ""
                ok = true
            }
        }
        if (!ok) {
            val result = login(context, kami, fromRestore = true)
            ok = result.success
        }
        if (!ok) {
            currentKami = ""
            currentToken = ""
            endTime = ""
            cardTypeName = ""
            onlineNum = ""
            currentKamiMasked = ""
            setLoginState(false)
            sp.edit().clear().apply()
        }
        isRestoringSession = false
        _restoreState.value = false
    }

    fun restoreSessionAsync(context: Context) {
        if (restoring) return
        restoring = true
        isRestoringSession = true
        _restoreState.value = true
        restoreJob?.cancel()
        restoreJob = scope.launch {
            try {
                restoreSession(context.applicationContext)
            } finally {
                restoring = false
                isRestoringSession = false
                _restoreState.value = false
            }
        }
    }

    // ================= 登录 =================

    suspend fun login(context: Context, kami: String): AuthResult =
        login(context, kami, fromRestore = false)

    private suspend fun login(
        context: Context,
        kami: String,
        fromRestore: Boolean
    ): AuthResult = withContext(Dispatchers.IO) {
        doLogin(context, kami, fromRestore)
    }

    /** 同步登录（调用方需在 IO 线程） */
    private fun doLogin(context: Context, kami: String, fromRestore: Boolean): AuthResult {
        if (!SagConfig.ENABLED) {
            setLoginState(true)
            return AuthResult(true, "验证已关闭（调试）")
        }
        if (!init(context)) {
            return AuthResult(false, lastError.ifBlank { "SDK 初始化失败" })
        }
        val card = kami.trim()
        if (card.isEmpty()) return AuthResult(false, "请输入卡密")

        val v = wy!!
        return try {
            val r: WYLoginResult = v.login(card, getMachineCode(context))
            if (r.success) {
                currentKami = card
                currentToken = r.token
                currentKamiMasked = maskKami(card)
                endTime = when {
                    r.type == "single" -> "次数卡(${r.remain})"
                    r.endTime > 0 -> formatEndTime(r.endTime)
                    else -> ""
                }
                cardTypeName = (r.kmtypeName ?: "").ifEmpty { cardTypeText(r.kmtype) }
                setLoginState(true)
                lastError = ""
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(KEY_KAMI, encryptKami(context, card))
                    .putString(KEY_TOKEN, r.token)
                    .putString(KEY_END, endTime)
                    .apply()
                // 登录成功后自动启动心跳
                startHeartbeat(context)
                val tip = if (cardTypeName.isNotEmpty()) "（$cardTypeName）" else ""
                val end = if (endTime.isNotEmpty()) "，$endTime" else ""
                AuthResult(true, "登录成功$tip$end", endTime = endTime)
            } else {
                lastError = (r.msg ?: "").ifBlank { "登录失败" }
                if (!fromRestore) setLoginState(false)
                AuthResult(false, lastError)
            }
        } catch (e: Exception) {
            lastError = e.message ?: "登录异常"
            AuthResult(false, lastError)
        }
    }

    // ================= 解绑 =================

    suspend fun unbindKami(context: Context, kami: String? = null): AuthResult =
        withContext(Dispatchers.IO) {
            val card = (kami ?: currentKami).trim()

            if (!SagConfig.ENABLED) {
                logout(context)
                return@withContext AuthResult(true, "已退出登录")
            }

            if (card.isEmpty()) {
                logout(context)
                return@withContext AuthResult(true, "已退出登录")
            }

            if (!init(context)) {
                logout(context)
                return@withContext AuthResult(false, lastError.ifBlank { "SDK 初始化失败" })
            }

            val v = wy!!
            try {
                val r: WYUnbindResult = v.unbind(card, getMachineCode(context))
                logout(context)
                if (r.success) {
                    val remain = if (r.remain > 0) "（剩余解绑次数：${r.remain}）" else ""
                    AuthResult(true, "已退出登录（设备已解绑）$remain")
                } else {
                    AuthResult(true, "已退出登录（${(r.msg ?: "").ifBlank { "解绑失败" }}）")
                }
            } catch (e: Exception) {
                logout(context)
                AuthResult(true, "已退出登录（${e.message ?: "解绑异常"}）")
            }
        }

    fun logout(context: Context) {
        stopHeartbeat()
        setLoginState(false)
        currentKami = ""
        currentToken = ""
        currentKamiMasked = ""
        endTime = ""
        cardTypeName = ""
        onlineNum = ""
    }

    fun skipLogin() {
        isLoggedIn = true
        currentKamiMasked = "未登录"
    }

    fun loadSavedKami(context: Context): String {
        if (!SagConfig.REMEMBER_KAMI) return ""
        val blob = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_KAMI, "") ?: ""
        return decryptKami(context, blob) ?: blob
    }

    // ================= 公告 / 更新 =================

    data class NoticeVersion(
        val notice: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val updateLog: String = "",
        val updateUrl: String = ""
    )

    suspend fun fetchNoticeAndVersion(localVersion: String): NoticeVersion =
        withContext(Dispatchers.IO) {
            val v = wy
            if (v == null) {
                return@withContext NoticeVersion("", localVersion, false)
            }

            var notice = ""
            var latest = ""
            var hasUpdate = false
            var uplog = ""
            var upurl = ""

            try {
                val n: WYNoticeResult = v.getNotice()
                notice = if (n.success) n.notice else ""
            } catch (_: Exception) {
            }

            try {
                val u: WYVersionResult = v.checkUpdate(localVersion)
                if (u.success) {
                    latest = u.version
                    uplog = u.updateshow
                    upurl = unescapeHtmlEntities(u.updateurl)
                    hasUpdate = u.hasUpdate ||
                        (latest.isNotEmpty() && compareVersion(latest, localVersion) > 0)
                }
            } catch (_: Exception) {
            }

            NoticeVersion(notice, latest, hasUpdate, uplog, upurl)
        }

    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".", "-").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val pb = b.split(".", "-").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun unescapeHtmlEntities(s: String): String {
        if (s.isEmpty()) return s
        return s
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }
}

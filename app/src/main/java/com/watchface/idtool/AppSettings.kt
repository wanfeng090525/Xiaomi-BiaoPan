package com.watchface.idtool

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File

// ====================================================================
// 背景配置
// ====================================================================

/** 背景模式 */
object BgMode {
    /** 内置默认壁纸（应用随包图片） */
    const val DEFAULT = "default"
    /** 相册自定义图片（复制到应用私有目录） */
    const val GALLERY = "gallery"
    /** 纯色背景（自定义颜色） */
    const val COLOR = "color"
    /** 液态动态背景（渐变 + 光斑） */
    const val LIQUID = "liquid"
}

/** 当前背景配置（mode + 颜色） */
data class BgConfig(
    val mode: String = BgMode.LIQUID,
    val color: Long = 0xFF14151F
)

/**
 * 应用设置（SharedPreferences 持久化）
 *
 * · language           界面语言（中文 / 英文，"system" 跟随系统）
 * · densityFactor      显示密度缩放（DPI），拉条 80% ~ 110% 连续调节
 * · bgMode/bgColor     背景样式（默认壁纸 / 相册图片·含动图 / 纯色 / 液态动态）
 * · announceAutoShow   启动时自动弹出新公告（开关，默认开）
 */
object AppSettings {
    private const val PREFS = "app_settings"

    private const val KEY_LANG = "language"
    private const val KEY_DENSITY = "density_factor"
    private const val KEY_BG_MODE = "bg_mode"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_ANNOUNCE_AUTO = "announce_auto_show"

    /** 密度拉条范围：80% ~ 110% */
    const val DENSITY_MIN = 0.8f
    const val DENSITY_MAX = 1.1f

    @Volatile
    var densityFactor: Float = 1.0f
        private set

    /** 背景配置（Compose 响应式状态：修改后界面自动重组刷新） */
    val bgConfigState = mutableStateOf(BgConfig())
    val bgConfig: BgConfig get() = bgConfigState.value

    /** 启动时自动弹出新公告（响应式开关） */
    val announceAutoShowState = mutableStateOf(true)
    val announceAutoShow: Boolean get() = announceAutoShowState.value

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 启动时加载（语言由 Compose 侧读取） */
    fun load(context: Context) {
        val p = prefs(context)
        densityFactor = p.getFloat(KEY_DENSITY, 1.0f)
        bgConfigState.value = BgConfig(
            mode = p.getString(KEY_BG_MODE, BgMode.LIQUID) ?: BgMode.LIQUID,
            color = p.getLong(KEY_BG_COLOR, 0xFF14151F)
        )
        announceAutoShowState.value = p.getBoolean(KEY_ANNOUNCE_AUTO, true)
        com.watchface.idtool.ui.AppLocale.apply(p.getString(KEY_LANG, "zh") ?: "zh")
    }

    // ---------- 语言 ----------
    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANG, "zh") ?: "zh"

    fun setLanguage(context: Context, code: String) {
        prefs(context).edit().putString(KEY_LANG, code).apply()
        com.watchface.idtool.ui.AppLocale.apply(code)
    }

    // ---------- 密度 ----------
    fun setDensityFactor(context: Context, factor: Float) {
        // 钳制到 80% ~ 110%，并以 1% 为步长取整
        val clamped = factor.coerceIn(DENSITY_MIN, DENSITY_MAX)
        densityFactor = clamped
        prefs(context).edit().putFloat(KEY_DENSITY, clamped).apply()
    }

    // ---------- 公告开关 ----------
    fun setAnnounceAutoShow(context: Context, enabled: Boolean) {
        announceAutoShowState.value = enabled
        prefs(context).edit().putBoolean(KEY_ANNOUNCE_AUTO, enabled).apply()
    }

    // ---------- 背景 ----------
    /** 设置背景样式（立即生效并持久化） */
    fun setBackground(context: Context, mode: String, color: Long = bgConfig.color) {
        prefs(context).edit()
            .putString(KEY_BG_MODE, mode)
            .putLong(KEY_BG_COLOR, color)
            .apply()
        bgConfigState.value = BgConfig(mode, color)
    }

    /** 自定义壁纸文件（应用私有目录） */
    fun customWallpaperFile(context: Context): File =
        File(context.filesDir, "custom_wallpaper.jpg")

    /**
     * 从相册 URI 复制壁纸到私有目录并启用
     * @return 成功与否
     */
    fun setGalleryBackground(context: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(context.filesDir, "custom_wallpaper.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (tmp.length() == 0L) {
                tmp.delete()
                return false
            }
            val target = customWallpaperFile(context)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return false
            }
            setBackground(context, BgMode.GALLERY)
            true
        } catch (_: Exception) {
            false
        }
    }
}

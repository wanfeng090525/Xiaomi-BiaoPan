package com.watchface.idtool

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * 音效类型：不同控件使用不同音效
 *
 * · CLICK    通用点击（按钮 / 卡片 / 浮动钮）
 * · TOGGLE   开关切换（Switch / Dock 导航切换）
 * · SLIDER   拉条步进（显示密度等液态拉条档位）
 * · SUCCESS  成功确认（完成某项操作的柔和提示）
 */
enum class SoundType(val file: String) {
    CLICK("sounds/click.wav"),
    TOGGLE("sounds/toggle.wav"),
    SLIDER("sounds/slider.wav"),
    SUCCESS("sounds/success.wav");
}

/**
 * 点击音效（SoundPool 低延迟播放）
 *
 * · 音源：assets/sounds 下多份 WAV 音效（Python 合成的玻璃触击音，多音色调配）
 * · 音频通道：USAGE_MEDIA —— 跟随媒体音量，不受系统「触摸提示音」开关
 *   影响（此前用 SONIFICATION 通道在多数设备上被系统静音，导致只感
 *   觉到振动而听不到声音，正是用户反馈的问题）
 * · 预加载：MainActivity 启动时调用 ensureLoaded()，首次点击即可出声
 * · 失败重载：加载失败自动清空引用，下次点击重新尝试
 * · 开关：AppSettings.soundEnabled（设置页可切换，立即生效；关闭后
 *   同时静音并停止全部触感振动）
 */
object ClickSound {
    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<SoundType, Int>()

    @Volatile
    private var loaded = false

    @Volatile
    private var lastPlayAt: Long = 0L

    /** 启动时预加载（MainActivity.onCreate 调用，首次点击零延迟） */
    fun ensureLoaded(context: Context) {
        if (soundPool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    loaded = true
                } else {
                    loaded = false
                    soundPool = null
                    soundIds.clear()
                }
            }
            val appCtx = context.applicationContext
            SoundType.entries.forEach { type ->
                try {
                    // 从 assets 加载各类型音效，避免 R.raw 资源计入 dex 体积
                    soundIds[type] = pool.load(appCtx.assets.openFd(type.file), 1)
                } catch (_: Exception) {
                    // 单个音效缺失不影响其余音效加载
                }
            }
            soundPool = pool
        } catch (_: Exception) {
            soundPool = null
        }
    }

    /** 通用点击（向后兼容：未显式指定类型一律走 CLICK） */
    fun play(context: Context) = play(context, SoundType.CLICK)

    /**
     * 按控件类型播放对应反馈（音效与震动完全独立，可在设置页分别开关）：
     *   · 音效  仅当 AppSettings.soundEnabled 时走 SoundPool
     *   · 震动  仅当 AppSettings.vibrationEnabled 时走系统 Vibrator（Haptics）
     */
    fun play(context: Context, type: SoundType) {
        if (AppSettings.soundEnabled) playSound(context, type)
        if (AppSettings.vibrationEnabled) Haptics.play(context, type)
    }

    /** 音效通道：SoundPool 低延迟播放（震动由 Haptics 独立处理） */
    private fun playSound(context: Context, type: SoundType) {
        try {
            if (soundPool == null || !loaded) {
                soundPool?.release()
                soundPool = null
                soundIds.clear()
                ensureLoaded(context)
                // 重新加载中，本次不出声，下次点击生效
                return
            }
            // 拉条步进更频繁，节流阈值放宽避免破音堆叠
            val minGap = if (type == SoundType.SLIDER) 70L else 40L
            val now = System.currentTimeMillis()
            if (now - lastPlayAt < minGap) return
            lastPlayAt = now
            val id = soundIds[type] ?: soundIds[SoundType.CLICK] ?: return
            soundPool?.play(id, 1.0f, 1.0f, 1, 0, 1f)
        } catch (_: Exception) {
            soundPool = null
            soundIds.clear()
            loaded = false
        }
    }
}
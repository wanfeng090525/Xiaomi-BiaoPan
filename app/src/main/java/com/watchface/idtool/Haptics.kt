package com.watchface.idtool

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动反馈（Haptics · iOS 液态手感）
 *
 * 与点击音效完全独立的两套反馈通道：
 *   · 音效走 SoundPool（ClickSound，受 AppSettings.soundEnabled 控制）
 *   · 震动走系统 Vibrator（本文件 Haptics，受 AppSettings.vibrationEnabled 控制）
 * 二者可分别设置在设置页独立开/关，互不影响。
 *
 * 不同控件的震动节奏不同（对应 SoundType）：
 *   · CLICK    单次清脆短脉冲  —— 按钮 / 卡片 / 通用点击
 *   · TOGGLE   双连击脉冲      —— 开关 / Dock 导航切换
 *   · SLIDER   极轻单点       —— 液态拉条档位步进（长拖不震手）
 *   · SUCCESS  三连优雅脉冲    —— 成功确认类操作
 *
 * · 最低好系统版本：API 26+ 使用 VibrationEffect 精确波形，24~25 退化单震。
 * · 统一节流：同一毫秒内不重复叠震，避免拖动/连点破音般的震动堆叠。
 */
object Haptics {

    @Volatile
    private var lastVibrateAt: Long = 0L

    /** 取出系统震动器（API 31+ 取默认 VibratorManager；否则取 Vibrator 服务） */
    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /** 关闭未完成震动（关闭震动开关时调用） */
    fun cancel(context: Context) {
        runCatching { vibrator(context)?.cancel() }
    }

    /** 按控件类型播放对应震动（震动开关关闭时完全静默；节流防堆叠） */
    fun play(context: Context, type: SoundType) {
        if (!AppSettings.vibrationEnabled) return
        try {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            val now = System.currentTimeMillis()
            // 拉条步进频繁，阈值放宽
            val minGap = if (type == SoundType.SLIDER) 90L else 50L
            if (now - lastVibrateAt < minGap) return
            lastVibrateAt = now

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(effect(type))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(30)
            }
        } catch (_: Exception) {
            // 个别设备无震动器或震动失败：静默忽略
        }
    }

    /** 各控件对应的震动波形（均为无震动起始，-1 表示不重复） */
    private fun effect(type: SoundType): VibrationEffect = when (type) {
        SoundType.CLICK -> VibrationEffect.createWaveform(
            longArrayOf(0, 22),
            -1
        )
        SoundType.TOGGLE -> VibrationEffect.createWaveform(
            longArrayOf(0, 18, 26, 20),
            -1
        )
        SoundType.SLIDER -> VibrationEffect.createOneShot(
            12, VibrationEffect.DEFAULT_AMPLITUDE
        )
        SoundType.SUCCESS -> VibrationEffect.createWaveform(
            longArrayOf(0, 18, 30, 18, 34, 22),
            -1
        )
    }
}
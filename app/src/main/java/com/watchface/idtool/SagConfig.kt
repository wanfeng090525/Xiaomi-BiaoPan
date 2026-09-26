package com.watchface.idtool

/**
 * 微验网络验证配置（T4Verify）
 * 仅保留开关与本地行为配置；接口地址/密钥已内置于 T4Verify SDK。
 */
object SagConfig {

    /** 是否启用网络验证；false 时登录接口直接放行（调试用） */
    const val ENABLED = true

    /** 是否记住卡密（本地加密存储，登录框预填） */
    const val REMEMBER_KAMI = true
}

package com.watchface.idtool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.watchface.idtool.R
import kotlinx.coroutines.delay

/**
 * 启动罩：参照 KernelSU 管理器的开屏效果——
 * 纯白底，居中图标带 3D 旋转 + 弹性缩放入场，短暂停留后整体淡出进入主页。
 *
 * 分工：
 *  - Android 12+ 系统启动屏（values-v31 windowSplashScreen*，白底 + 图标）覆盖进程创建阶段；
 *  - 本组件覆盖 Compose 首帧渲染到首屏可交互之间的空档，同为白底无缝衔接。
 */
@Composable
fun LaunchSplash() {
    var visible by remember { mutableStateOf(true) }

    // 入场进度 0→1：旋转回正 + 缩放弹入（spring 自带回弹）
    val entrance = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(
            1f,
            spring(dampingRatio = 0.6f, stiffness = 380f)
        )
        delay(600)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(320, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        val t = entrance.value
                        scaleX = 0.55f + 0.45f * t
                        scaleY = 0.55f + 0.45f * t
                        rotationY = 90f * (1f - t)
                        alpha = t.coerceIn(0f, 1f)
                        cameraDistance = 14f * density
                    }
            )
        }
    }
}

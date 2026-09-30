package com.watchface.idtool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchface.idtool.R
import kotlinx.coroutines.delay

/**
 * 启动罩：Compose 首帧之上短暂展示品牌页，随后整体淡出并轻微放大消失。
 *
 * 分工：
 *  - Android 12+ 系统启动屏（values-v31 windowSplashScreen*）覆盖进程创建阶段；
 *  - 本组件覆盖 Compose 首帧渲染到首屏可交互之间的空档，视觉上无缝衔接
 *    （同为 #F2F2F7 底 + 居中品牌元素）。
 */
@Composable
fun LaunchSplash() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1150)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(380, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 1.05f,
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IOSPalette.groupedBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_ximi_logo),
                    contentDescription = null,
                    modifier = Modifier.size(104.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = AppLocale.t("表盘ID工具"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = IOSPalette.label
                )
            }
        }
    }
}

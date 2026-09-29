package com.watchface.idtool

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import com.watchface.idtool.ui.AppBackground
import com.watchface.idtool.ui.GlassNavTab
import com.watchface.idtool.ui.GlassNavBar
import com.watchface.idtool.ui.GlobalRippleOverlay
import com.watchface.idtool.ui.HistoryScreen
import com.watchface.idtool.ui.LoadingOverlay
import com.watchface.idtool.ui.LocalAppBackdrop
import com.watchface.idtool.ui.ModifyScreen
import com.watchface.idtool.ui.ResultDialog
import com.watchface.idtool.ui.SettingsScreen

import com.watchface.idtool.ui.ToastMessage
import com.watchface.idtool.ui.WatchFaceTheme
import com.watchface.idtool.ui.WelcomeScreen
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {

    /** DPI 密度缩放：在 Context 附加阶段以一致的方式缩放 density/scaledDensity */
    override fun attachBaseContext(newBase: Context) {
        AppSettings.load(newBase)
        val factor = AppSettings.densityFactor
        super.attachBaseContext(
            if (factor == 1f) newBase else DensityScaledContext(newBase, factor)
        )
    }

    /**
     * 按系数缩放 densityDpi，并让 density / scaledDensity / fontScale 保持同步。
     * 仅改 densityDpi 会造成 sp 文字与 dp 布局比例不一致，导致增大密度后文字异常显示；
     * 这里显式统一三者的转换关系，保证文字与布局同步缩放。
     */
    private class DensityScaledContext(base: Context, private val factor: Float) :
        android.content.ContextWrapper(base) {

        private val scaledResources: Resources by lazy {
            val res = super.getResources()
            val dm = res.displayMetrics
            val baseDensity = dm.density
            // 保留系统字体缩放，避免破坏“文字大小”辅助功能设置
            val fontScale = if (baseDensity > 0f) dm.scaledDensity / baseDensity else 1f
            val targetDpi = (dm.densityDpi * factor).roundToInt()
            val newDensity = targetDpi / 160f

            val cfg = Configuration(res.configuration)
            cfg.densityDpi = targetDpi
            cfg.fontScale = fontScale

            val wrapped = base.createConfigurationContext(cfg).resources
            wrapped.displayMetrics.apply {
                this.density = newDensity
                this.scaledDensity = newDensity * fontScale
                this.densityDpi = targetDpi
            }
            wrapped
        }

        @Deprecated("Deprecated in Java")
        override fun getResources(): Resources = scaledResources
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 取消沉浸式：状态栏 / 导航栏常驻显示，App 内容不延伸到系统栏之下
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // 关键：true = 内容自动避让系统栏（不再铺到状态栏底下）
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // 浅色背景 → 系统栏用深色图标
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        setContent {
            WatchFaceTheme {
                AppContent()
            }
        }
    }
}

/** 页面顺序（用于方向感知的转场动画） */
private val PAGES = listOf("home", "modify", "history", "settings")

@Composable
private fun AppContent() {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var currentPage by remember { mutableStateOf("home") }
    val context = LocalContext.current

    // 液态玻璃倾斜高光：全局单例加速计监听，玻璃镜面高光角度随手机倾斜实时变化。
    // 仅前台采集（后台停止，避免无谓耗电）；不支持 RuntimeShader 的机型内部自动跳过。
    val tiltLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(tiltLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    com.watchface.idtool.ui.LiquidGlassTilt.start(context)

                androidx.lifecycle.Lifecycle.Event.ON_PAUSE ->
                    com.watchface.idtool.ui.LiquidGlassTilt.stop()

                else -> Unit
            }
        }
        tiltLifecycleOwner.lifecycle.addObserver(observer)
        // 首次组合时可能已处于 RESUMED（不会再次收到 ON_RESUME），补一次启动
        if (tiltLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            com.watchface.idtool.ui.LiquidGlassTilt.start(context)
        }
        onDispose {
            tiltLifecycleOwner.lifecycle.removeObserver(observer)
            com.watchface.idtool.ui.LiquidGlassTilt.stop()
        }
    }

    // 恢复本地卡密登录态：首次启动后台异步验证，完成后自动登录
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.watchface.idtool.SagAuthManager.restoreSession(context)
    }

    // 观察会话恢复状态，用于显示启动加载遮罩
    val isRestoring by com.watchface.idtool.SagAuthManager.restoreState.collectAsState()
    val loggedIn by com.watchface.idtool.SagAuthManager.loginState.collectAsState()

    // ON_RESUME 时节流刷新权限状态（从 Shizuku 授权页返回后立即生效）
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 返回键：子页面先回主页；主页双击退出
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (currentPage != "home") {
            currentPage = "home"
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackAt = now
                Toast.makeText(context, com.watchface.idtool.ui.AppLocale.t("再按一次退出"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun switchPage(page: String) {
        if (page == "history") viewModel.loadRecords()
        currentPage = page
    }

    // AndroidLiquidGlass：全局背景折射层。AppBackground 将自身绘制内容
    // 注册到该层，所有 .liquidGlass() 玻璃组件据此做真实折射/模糊
    val appBackdrop = rememberLayerBackdrop()

    androidx.compose.runtime.CompositionLocalProvider(LocalAppBackdrop provides appBackdrop) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        // L0 背景：自定义壁纸 / 纯色 / 液态动态（全屏铺满，含系统栏区域；
        //          图片 ContentScale.Crop 保持原比例居中裁剪，任意屏幕比例不变形）
        AppBackground()

        // L1 内容区：避开系统栏与输入法
        // 注意：此处【不能】注册 layerBackdrop——内容区包含大量玻璃组件，
        // 把采样者放进被采样子树会造成同一 GraphicsLayer 边录边读（GL 反馈循环，
        // RenderThread SIGSEGV 闪退）。官方架构：注册层与玻璃组件必须为兄弟节点。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 页面内容：方向感知的滑动 + 淡入淡出转场
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val from = PAGES.indexOf(initialState).coerceAtLeast(0)
                    val to = PAGES.indexOf(targetState).coerceAtLeast(0)
                    val forward = to >= from
                    val enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) {
                                if (forward) it / 4 else -it / 4
                            }
                    val exit = fadeOut(tween(200)) +
                            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) {
                                if (forward) -it / 5 else it / 5
                            }
                    enter togetherWith exit
                },
                label = "pageTransition"
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        "home" -> WelcomeScreen(
                            viewModel = viewModel,
                            state = state,
                            onNavigateToModify = { currentPage = "modify" },
                            onNavigateToHistory = { switchPage("history") }
                        )
                        "modify" -> ModifyScreen(
                            viewModel = viewModel,
                            state = state,
                            onNavigateToHistory = { switchPage("history") }
                        )
                        "history" -> HistoryScreen(
                            viewModel = viewModel,
                            state = state
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            state = state
                        )
                    }
                }
            }

            // L2 底部导航栏（iOS：3 标签垂直胶囊 + 右侧圆形设置按钮）
            GlassNavBar(
                tabs = listOf(
                    GlassNavTab(Icons.Default.Home, "主页"),
                    GlassNavTab(Icons.Default.Build, "修改"),
                    GlassNavTab(Icons.Default.History, "记录")
                ),
                selected = when (currentPage) {
                    "home" -> 0
                    "modify" -> 1
                    "history" -> 2
                    // 设置页由右侧圆形按钮承载：越界索引让指示胶囊平滑淡出，
                    // 不再错误地停在「主页」上
                    else -> 3
                },
                onSelect = { index ->
                    switchPage(
                        when (index) {
                            0 -> "home"
                            1 -> "modify"
                            else -> "history"
                        }
                    )
                },
                onSettingsClick = { switchPage("settings") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )

            if (isRestoring || state.isLoading) {
                LoadingOverlay(if (isRestoring) "正在验证会话…" else state.loadingText)
            }

            state.resultMessage?.let { msg ->
                ResultDialog(
                    success = state.resultSuccess,
                    message = msg,
                    onDismiss = { viewModel.clearResult() }
                )
            }

            state.toastMessage?.let { toast ->
                ToastMessage(message = toast, onFinished = { viewModel.clearToast() })
            }
        }

        // L3 全局点击光效：View 层监听 · 零拦截 · 最顶层绘制
        GlobalRippleOverlay()
        }
    }
}

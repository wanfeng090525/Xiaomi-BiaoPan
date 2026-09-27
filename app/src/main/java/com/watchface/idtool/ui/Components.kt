package com.watchface.idtool.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.MotionEvent
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.watchface.idtool.AppSettings
import com.watchface.idtool.BgMode
import java.io.File
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ====================================================================
// 液态玻璃设计系统 v2（Liquid Glass · 深色基底 · 保时捷工程风）
//
// 对齐参考规格（控制中心式液态玻璃）：
//   · 玻璃填充 8~15% 白 + 顶部菲涅尔亮线 + 非对称左上/右下折射边
//   · 胶囊 / 大圆角连续曲面（squircle 观感）
//   · 激活态 = 提亮填充 + 色彩光晕扩散
//   · 全局 Barlow（保时捷工程字体），数字窄长锐利
//
// 层次结构：
//   L0  LiquidBackground   深蓝紫渐变 + 缓慢漂移霓虹光斑
//   L1  GlassCard          半透明玻璃面板
//   L2  GlassButton/Chip   胶囊玻璃按钮
//   L3  GlassNavBar        悬浮胶囊 Dock
// ====================================================================

/** 全局语义色（单色系规格：全部为中性白灰，无彩色按钮/图标） */
object AppColors {
    val success = Color(0xFFE9EBF4)
    val successDark = success
    val warning = Color(0xFFD9DEEB)
    val danger = Color(0xFFE9EBF4)
    val dangerDark = danger
    val info = Color(0xFFE9EBF4)
    val infoDark = info

    @Composable
    fun successAdaptive(): Color = success

    @Composable
    fun dangerAdaptive(): Color = danger

    @Composable
    fun infoAdaptive(): Color = info
}

// ====================================================================
// AndroidLiquidGlass（Kyant0/backdrop）真实液态玻璃接入 · v3 深度优化
//
//   · LocalAppBackdrop  全局折射采样源（AppBackground 注册）
//   · Modifier.liquidGlass(shape)  真实折射 / 磨砂 / 色散玻璃
//   · 效果顺序遵循官方文档：vibrancy ⇒ blur ⇒ lens
//   · lens 开启 depthEffect（厚度折射）+ chromaticAberration（边缘色散）
//   · 镜面高光由库 RuntimeShader Highlight 提供，角度随重力倾斜实时变化
//   · 深色基底上关闭库自带 Shadow（不可见且增加 GPU 负担）
//   · 按压缩放走 layerBlock（背景折射不跟手缩放，官方 Interactive 教程规格）
//   · API < 31 无 RenderEffect、< 33 无 RuntimeShader 时库内部自动降级
// ====================================================================

/** 全局液态玻璃背景层：由根布局的 AppBackground 注册，玻璃组件据此折射采样 */
val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 液态玻璃倾斜高光：全局唯一加速计监听。
 *
 * 单例注册（而非每个玻璃组件各注册一个 listener），所有 liquidGlass 组件共享
 * 同一角度状态，镜面高光方向随手机倾斜实时移动 —— iOS 液态玻璃的标志性反光。
 * 无加速计 / 传感器不可用时角度保持默认 45°（左上受光），功能自动降级。
 */
object LiquidGlassTilt {

    /** 高光角度（度）：atan2(y, x)，0° = 右侧受光，45° = 左上受光 */
    val angle = mutableFloatStateOf(45f)

    private var manager: SensorManager? = null
    private var listener: SensorEventListener? = null

    fun start(context: Context) {
        if (listener != null) return
        // 无 RuntimeShader 的机型高光完全由 painted 规格绘制，倾斜角度不参与渲染，
        // 不注册加速计（避免无谓耗电）
        if (!LiquidGlassShaderSupported) return
        val m = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accelerometer = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val target = atan2(event.values[1], event.values[0]) * (180f / PI.toFloat())
                // 低通滤波：抑制手持抖动，保持高光平滑
                val alpha = 0.15f
                angle.value = angle.value * (1f - alpha) + target * alpha
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        m.registerListener(l, accelerometer, SensorManager.SENSOR_DELAY_UI)
        manager = m
        listener = l
    }

    fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        manager = null
    }
}

/** 折射统一增益：历史逐点折射参数偏保守，放大后由形状尺寸上限兜底 */
private const val LIQUID_LENS_GAIN = 1.4f

/**
 * 库的镜面高光 / 边缘折射（lens）依赖 API 33+ 的 RuntimeShader：
 *   · API ≥ 33  真实折射 + 随倾斜实时转动的方向性镜面高光
 *   · API 31-32 仅有 blur / vibrancy；折射 no-op，高光退化为一圈均匀 0.5dp 白描边
 *   · API < 31  全部 no-op
 * 低版本没有可用的折射高光，玻璃质感必须由 [glass] 的 painted 规格
 * （菲涅尔顶缘亮线 + 非对称折射描边）承担，否则界面会退化成纯色色块。
 */
private val LiquidGlassShaderSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 真实液态玻璃材质（官方 get-started / ControlCenter 规格深度优化）：
 *   1. vibrancy  背景色彩提亮（iOS 同款）
 *   2. blur      磨砂模糊
 *   3. lens      边缘折射：历史参数按官方比例放大，并夹在形状圆角半径内
 *                （胶囊 / 圆形的圆角半径 = minDimension/2，故上限取 minDimension/2），
 *                同时开启 depthEffect（厚度折射）+ chromaticAberration（边缘色散）
 *
 * 镜面高光由库 RuntimeShader 绘制，角度取自 [LiquidGlassTilt]（随倾斜实时变化）；
 * 自绘描边在检出真实折射层时自动让位（见 [glass]），避免边缘双层高光发白。
 *
 * [layerBlock] 按压缩放等图形变换：变换只作用于「玻璃面板 + 内容」，
 * 背景折射保持原位（官方 Interactive 教程明确：graphicsLayer 缩放会让背景跟随缩放）。
 * 无背景层（LocalAppBackdrop 为 null）时安全降级为 painted 玻璃。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    blurRadius: Dp = 12.dp,
    lensHeight: Dp = 18.dp,
    lensAmount: Dp = 30.dp,
    depthEffect: Boolean = true,
    chromaticAberration: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null
): Modifier {
    val backdrop = LocalAppBackdrop.current ?: return this
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val minDimension = size.minDimension
            vibrancy()
            // 磨砂：略加强并限制上限，保持通透
            blur((blurRadius.toPx() * 1.25f).coerceAtMost(minDimension * 0.6f))
            // 折射：放大到官方的强折射区间，上限 = 形状圆角半径（minDimension/2）
            lens(
                refractionHeight = (lensHeight.toPx() * LIQUID_LENS_GAIN)
                    .coerceAtMost(minDimension * 0.5f),
                refractionAmount = (lensAmount.toPx() * LIQUID_LENS_GAIN)
                    .coerceAtMost(minDimension),
                depthEffect = depthEffect,
                chromaticAberration = chromaticAberration
            )
        },
        // 镜面高光：角度随重力倾斜实时转动（绘制期读取状态 → 自动失效重绘）。
        // 无 RuntimeShader 的机型上库画不出方向性高光，只会沿轮廓描一圈均匀白边，
        // 与 painted 顶缘亮线叠成双层描边，故直接关闭交由 [glass] 承担。
        highlight = if (LiquidGlassShaderSupported) {
            {
                Highlight(
                    alpha = 1f,
                    style = HighlightStyle.Default(angle = LiquidGlassTilt.angle.value, falloff = 2f)
                )
            }
        } else null,
        // 深色基底上投影不可见且拖慢 GPU：关闭库自带 Shadow
        shadow = null,
        layerBlock = layerBlock
    )
}

/**
 * 液态玻璃选中面板（LiquidBottomTabs 参考组件规格）：
 * 组合折射源（全局背景 + Dock 自身内容），按压时折射加深 + 色散 + 内阴影，
 * 玻璃面板随按压弹簧放大（背景不跟随缩放）。
 *
 * [pressProgress] 0f..1f 按压进度（弹簧驱动）。
 */
@Composable
fun Modifier.liquidGlassPanel(
    backdrop: Backdrop,
    shape: Shape,
    pressProgress: () -> Float,
    lensHeight: Dp = 10.dp,
    lensAmount: Dp = 14.dp
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        // 按压越深折射越强 + 开启色散（LiquidBottomTabs: lens(10p, 14p, chromaticAberration)）
        val p = pressProgress()
        val k = 0.5f + 0.5f * p
        lens(
            refractionHeight = lensHeight.toPx() * k,
            refractionAmount = lensAmount.toPx() * k,
            chromaticAberration = p > 0.15f
        )
    },
    highlight = {
        // 按压时顶部高光提亮（默认细高光 + 进度调制）
        Highlight(alpha = 0.4f + 0.4f * pressProgress())
    },
    innerShadow = {
        // 按压内阴影：液态下陷质感
        InnerShadow(radius = 6.dp * pressProgress(), alpha = 0.35f * pressProgress())
    },
    shadow = {
        Shadow(radius = 0.dp, alpha = 0f)
    },
    layerBlock = {
        val s = 1f + 0.05f * pressProgress()
        scaleX = s
        scaleY = s
    }
)

/** 玻璃材质参数 */
data class GlassColors(
    val tintTop: Color,
    val tintBottom: Color,
    val highlight: Color,
    val rimBright: Color,
    val rimDim: Color
)

/** 默认深色液态玻璃材质 */
@Composable
fun rememberGlassColors(
    tintTop: Color? = null,
    tintBottom: Color? = null
): GlassColors = GlassColors(
    tintTop = tintTop ?: GlassPalette.glassTintTop,
    tintBottom = tintBottom ?: GlassPalette.glassTintBottom,
    highlight = GlassPalette.highlight,
    rimBright = GlassPalette.rimBright,
    rimDim = GlassPalette.rimDim
)

/**
 * 液态玻璃材质绘制（v3 —— 与库高光分工）：
 *
 *   1. 玻璃主体    上亮下暗的低填充底（8~15% 白）
 *   2. 顶部光泽    自上而下渐隐的镜面高光（模拟厚玻璃）
 *   3. 菲涅尔亮缘  顶边 1.5px 亮线，向两端渐隐（抛光边缘）
 *   4. 折射描边    左上受光 → 右下背光的非对称渐变描边
 *
 * 当所在位置存在真实折射玻璃（[LocalAppBackdrop] 非空，即同时调用了
 * [liquidGlass]）时，3/4 的自绘描边会让位给库的 RuntimeShader Highlight
 * （随倾斜转动的方向性镜面高光），只保留 1/2 的色调与体积层 —— 否则两套
 * 边缘高光在轮廓上叠成「双层描边、边缘发白」的塑料感。
 * 弹窗等无背景层场景（LocalAppBackdrop 置空）仍走完整 painted 规格兜底。
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    colors: GlassColors,
    borderAlpha: Float = 1f,
    highlightAlpha: Float = 1f
): Modifier {
    // 仅当库真能画出方向性折射高光（API 33+ RuntimeShader）时自绘描边才让位；
    // 低版本库的 lens/highlight 全部降级，必须保留完整 painted 规格
    val hasRefraction = LiquidGlassShaderSupported && LocalAppBackdrop.current != null
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)

        // 统一裁剪到圆角轮廓内绘制：描边/高光一律不越出圆角边界，
        // 根治深色背景下小尺寸图标「四角白色残留 / 方形轮廓」渲染缺陷
        //（双描边沿轮廓线居中绘制时，外侧一半会透出圆角边界叠加成残影）
        val outlinePath = Path().apply { addOutline(outline) }
        clipPath(outlinePath) {
            // 1. 玻璃主体
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.tintTop, colors.tintBottom),
                    startY = 0f,
                    endY = size.height
                )
            )

            // 2. 顶部液态光泽（厚玻璃体积感）
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.highlight.copy(alpha = colors.highlight.alpha * 0.42f * highlightAlpha),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.45f
                )
            )

            if (!hasRefraction) {
                // 3. 菲涅尔顶缘亮线：顶部中段最亮、向两侧渐隐的抛光边缘
                drawOutline(
                    outline = outline,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            colors.rimBright.copy(alpha = 0f),
                            colors.rimBright.copy(alpha = colors.rimBright.alpha * 0.85f * borderAlpha),
                            colors.rimBright.copy(alpha = 0f)
                        ),
                        startX = 0f,
                        endX = size.width
                    ),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 4. 非对称折射描边：左上受光、右下背光
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.rimBright.copy(alpha = colors.rimBright.alpha * 0.65f * borderAlpha),
                            colors.rimDim.copy(alpha = colors.rimDim.alpha * 0.9f * borderAlpha)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

/** 激活光晕：以元素中心向外扩散的圆形柔光（浅色下低透明度）。
 *  注意必须用 drawCircle 而非 drawRect：矩形绘制的径向渐变会在四角
 *  留下可见的方形色块（「图标后面有正方形」问题的根因）。 */
fun Modifier.glow(color: Color, radiusFraction: Float = 1.1f): Modifier = this.drawBehind {
    val radius = size.minDimension * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.16f),
                color.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius
        ),
        radius = radius,
        center = Offset(size.width / 2f, size.height / 2f)
    )
}

/**
 * 深色玻璃下阴影为无操作：ColorOS 规格依靠边缘高光/磨砂分层，
 * 投影在深色基底上不可见且增加 GPU 负担。保留 API 兼容旧调用点。
 */
fun Modifier.glassShadow(
    @Suppress("UNUSED_PARAMETER") elevation: Dp,
    @Suppress("UNUSED_PARAMETER") shape: Shape
): Modifier = this

// ====================================================================
// 液态玻璃拉条（GlassSlider · iOS 液态风格）
//   · 玻璃渐变轨道 + 高光填充 + 圆形发光玻璃滑块
//   · 拖动跟手、松手弹簧归位
// ====================================================================

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val min = valueRange.start
    val max = valueRange.endInclusive
    val span = (max - min).coerceAtLeast(1e-4f)
    val intervals = steps + 1
    val fraction = ((value - min) / span).coerceIn(0f, 1f)

    var dragging by remember { mutableStateOf(false) }
    // 位置由 value 直接驱动，不再经过 Animatable 弹簧（此前松手弹簧归位会让
    // 滑块与手指不同步、拖不动；现在跟手零延迟）。仅保留按压放大的手感反馈。
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.2f else 1f,
        animationSpec = tween(110),
        label = "sliderThumbScale"
    )

    fun valueFromFraction(f: Float): Float {
        val clamped = f.coerceIn(0f, 1f)
        return if (steps > 0) {
            val k = (clamped * intervals).roundToInt().coerceIn(0, intervals)
            min + (k.toFloat() / intervals) * span
        } else {
            min + clamped * span
        }
    }

    BoxWithConstraints(modifier = modifier.height(32.dp)) {
        // 统一换算到像素，避免 Dp/Px 混算与 BoxScope.align 类型歧义
        val trackH = with(density) { 6.dp.toPx() }
        val thumbPx = with(density) { 24.dp.toPx() }
        val barH = with(density) { 32.dp.toPx() }
        val trackW = with(density) { maxWidth.toPx() }
        // 垂直居中：滑块 24dp 与轨道 6dp 对齐到同一条中心线
        val thumbTop = (barH - thumbPx) / 2f
        val trackTop = thumbTop + (thumbPx - trackH) / 2f
        // 滑块位置由 value 计算得出（去掉 spring 后纯跟手，无延迟）
        val thumbX = fraction * (trackW - thumbPx)
        val thumbCenter = thumbX + thumbPx / 2f

        // 未激活轨道（凹槽底）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .offset { IntOffset(0, trackTop.roundToInt()) }
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                }
        )

        // 已激活填充（左至滑块中心的白色液态渐变 + 微光）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .offset { IntOffset(0, trackTop.roundToInt()) }
                .drawBehind {
                    val w = thumbCenter.coerceIn(0f, size.width)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.88f),
                                Color.White.copy(alpha = 0.55f)
                            )
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
        )

        // 圆形玻璃滑块（纯显示，位置由 value 驱动，不承担任何手势——手势在下方整条触控层）
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset { IntOffset(thumbX.roundToInt(), thumbTop.roundToInt()) }
                .liquidGlass(
                    shape = CircleShape,
                    blurRadius = 3.dp,
                    lensHeight = 5.dp,
                    lensAmount = 8.dp,
                    // 拖动放大走 layerBlock：背景折射不跟手缩放
                    layerBlock = {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                )
                .glow(
                    Color.White.copy(alpha = if (dragging) 0.34f else 0.20f),
                    radiusFraction = 1.7f
                )
                .glass(CircleShape, rememberGlassColors())
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = size.minDimension * 0.32f,
                        center = this.center
                    )
                }
        )

        // 全宽触控层（最上层）：
        //   pointerInput 挂在「整条轨道」而非移动的滑块上——
        //   拖动时坐标原点相对整条固定，从任意位置按住都可滑，根治「拖不动」。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .pointerInput(intervals) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        fun fracOf(x: Float): Float =
                            (x / trackW.coerceAtLeast(1f)).coerceIn(0f, 1f)

                        onValueChange(valueFromFraction(fracOf(down.position.x)))
                        drag(down.id) { change ->
                            change.consume()
                            val f = fracOf(change.position.x)
                            onValueChange(valueFromFraction(f))
                        }
                        dragging = false
                        onValueChangeFinished?.invoke()
                    }
                }
        )
    }
}

/** 主按钮微光扫过：一道高光带周期性从左至右掠过 */
@Composable
fun Modifier.shimmerSweep(
    periodMillis: Int = 2800,
    bandColor: Color = Color.White
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return this.drawBehind {
        val bandWidth = size.width * 0.42f
        val x0 = progress * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    bandColor.copy(alpha = 0.28f),
                    Color.Transparent
                ),
                start = Offset(x0, 0f),
                end = Offset(x0 + bandWidth, size.height)
            )
        )
    }
}

/** 按压弹簧缩放（液态回弹手感） */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.955f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.52f,
            stiffness = 1600f
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ====================================================================
// 能量涟漪系统（Energy Ripple · 全局点击动感光效）
//
// 每次按压从触点迸发：
//   1. 能量核心  触点高亮光斑，快速膨胀并衰减（能量注入）
//   2. 主冲击波  一道亮环从触点扩散至组件边缘（easeOut 加速展开）
//   3. 次级回响  延迟 90ms 的第二道更宽光环（液体质感余波）
//   4. 边缘聚焦  冲击波抵达边缘时点亮轮廓线（能量汇聚描边）
// 涟漪自动裁剪到组件形状内，多层叠加自然融合。
// ====================================================================

private class EnergyRipple(val x: Float, val y: Float, val startNanos: Long)

/** 涟漪生命周期（ns）：核心 260ms / 冲击波 620ms / 回响 780ms，取最长 */
private const val RIPPLE_LIFETIME = 780_000_000L

/** 全局点击光效时长（比按钮涟漪略长，能量衰减更从容） */
private const val GLOBAL_RIPPLE_LIFETIME = 920_000_000L

@Composable
fun Modifier.pressRipple(
    interactionSource: MutableInteractionSource,
    clipShape: Shape? = null,
    color: Color = Color.White,
    intensity: Float = 1f
): Modifier {
    val ripples = remember { mutableStateListOf<EnergyRipple>() }
    // 时间驱动器：每帧推进，使 draw 阶段持续重估
    val tick by rememberInfiniteTransition(label = "rippleTick").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rippleTickT"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                ripples.add(
                    EnergyRipple(
                        interaction.pressPosition.x,
                        interaction.pressPosition.y,
                        System.nanoTime()
                    )
                )
            }
        }
    }

    return this.drawBehind {
        if (ripples.isEmpty()) return@drawBehind

        // 读取 tick：使 draw 依赖时间驱动器，涟漪存在期间每帧推进
        // （无涟漪时不读取 → 静止；首个涟漪加入 → 建立依赖 → 逐帧动画）
        @Suppress("UNUSED_EXPRESSION")
        tick.let { }

        val now = System.nanoTime()
        ripples.removeAll { now - it.startNanos > RIPPLE_LIFETIME }
        if (ripples.isEmpty()) return@drawBehind

        val clipPath: Path? = clipShape?.let { shape ->
            Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
        }

        val block: DrawScope.() -> Unit = {
            ripples.forEach { ripple ->
                val age = (now - ripple.startNanos).coerceAtLeast(0)
                val t = (age.toFloat() / RIPPLE_LIFETIME).coerceIn(0f, 1f)
                val cx = ripple.x
                val cy = ripple.y
                val reach = max(size.width, size.height) * 1.05f

                // ── 1. 能量核心：快速膨胀 + 衰减（前 33% 生命周期） ──
                val coreT = (t / 0.33f).coerceIn(0f, 1f)
                val coreR = reach * 0.30f * easeOutCubic(coreT)
                val coreA = (1f - coreT).pow(1.6f) * 0.42f * intensity
                if (coreA > 0.003f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = coreA * 1.5f),
                                color.copy(alpha = coreA),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = coreR.coerceAtLeast(1f)
                        ),
                        radius = coreR.coerceAtLeast(1f),
                        center = Offset(cx, cy)
                    )
                }

                // ── 2. 主冲击波（延迟 20ms 启动） ──
                val waveT = ((t - 0.02f) / 0.80f).coerceIn(0f, 1f)
                if (waveT > 0f && waveT < 1f) {
                    val wR = reach * easeOutQuart(waveT)
                    val wA = (1f - waveT).pow(1.3f) * 0.55f * intensity
                    drawCircle(
                        color = Color.White.copy(alpha = wA),
                        radius = wR.coerceAtLeast(1f),
                        center = Offset(cx, cy),
                        style = Stroke(width = (2.2.dp.toPx() * (1f - waveT * 0.55f)).coerceAtLeast(0.6f))
                    )
                    // 冲击波内侧微弱辉光填充
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                color.copy(alpha = wA * 0.35f),
                                Color.White.copy(alpha = wA * 0.80f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = wR.coerceAtLeast(1f)
                        ),
                        radius = wR.coerceAtLeast(1f),
                        center = Offset(cx, cy)
                    )
                }

                // ── 3. 次级回响（延迟 120ms，更宽更淡） ──
                val echoT = ((t - 0.15f) / 0.85f).coerceIn(0f, 1f)
                if (echoT > 0f && echoT < 1f) {
                    val eR = reach * easeOutQuart(echoT) * 1.10f
                    val eA = (1f - echoT).pow(2f) * 0.26f * intensity
                    drawCircle(
                        color = color.copy(alpha = eA),
                        radius = eR.coerceAtLeast(1f),
                        center = Offset(cx, cy),
                        style = Stroke(width = (1.2.dp.toPx() * (1f - echoT * 0.6f)).coerceAtLeast(0.4f))
                    )
                }

                // ── 4. 边缘聚焦：冲击波高峰期（25%~65%）点亮轮廓 ──
                val edgeT = ((t - 0.25f) / 0.40f).coerceIn(0f, 1f)
                if (edgeT > 0f && edgeT < 1f && clipShape != null) {
                    val edgeA = sin(edgeT * PI.toFloat()) * 0.38f * intensity
                    val outline = clipShape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline = outline,
                        color = Color.White.copy(alpha = edgeA),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        }

        if (clipPath != null) {
            clipPath(clipPath, clipOp = androidx.compose.ui.graphics.ClipOp.Intersect, block = block)
        } else {
            block(this)
        }
    }
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)
private fun easeOutQuart(t: Float): Float = 1f - (1f - t).pow(4)

/** 呼吸脉冲（用于状态点等小元素） */
@Composable
fun Modifier.pulse(minScale: Float = 0.85f, maxScale: Float = 1.18f, period: Int = 1600): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "pulseT"
    )
    val scale = minScale + (maxScale - minScale) * (0.5f + 0.5f * sin(t * 2f * PI.toFloat()))
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ====================================================================
// L0 背景（游戏级渲染 · Game-Grade Ambient Render）
//
// 与主流 3A 游戏的环境渲染管线同思路的分层合成：
//   1. Base Pass    四角多点深度渐变（非简单线性：暗角先行的体积底色）
//   2. Light Pass   五团大尺度软光域（双层衰减 falloff 模拟散射体积光）
//   3. Sweep Pass   极缓旋转的扫光（sweep gradient 模拟光源扫过）
//   4. Horizon Pass 底部冷色地平线辉光 + 上下夹暗（景深压缩）
//   5. Snow Pass    雪花粒子层（视差双层 + 摆动下落）
//   6. Vignette     径向暗角（镜头光学特性，聚焦中央）
//   7. Grain Pass   胶片颗粒噪声（Overlay 混合，消除渐变条带 = 真实感关键）
// ====================================================================

private data class AmbientBlob(
    val color: Color,
    val baseX: Float,          // 中心基础位置（0~1）
    val baseY: Float,
    val radius: Float,         // 相对短边的倍率
    val driftAmp: Float,       // 漂移幅度
    val phase: Float,          // 相位差
    val maxAlpha: Float
)

/** 胶片颗粒噪声纹理（128×128 中灰抖动，创建一次全局复用；Overlay 混合下呈双向明暗颗粒） */
private fun createNoiseBitmap(): ImageBitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val rnd = kotlin.random.Random(42)
    for (i in pixels.indices) {
        // 围绕中灰 128 的对称抖动：Overlay 混合时 >128 提亮、<128 压暗
        val g = 96 + rnd.nextInt(65)
        pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientT"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepT"
    )

    val blobs = listOf(
        AmbientBlob(Color(0xFF8A72FF), 0.14f, 0.20f, 1.45f, 0.11f, 0.00f, 0.38f), // 紫 · 左上
        AmbientBlob(Color(0xFF4E74FF), 0.88f, 0.36f, 1.30f, 0.10f, 1.70f, 0.34f), // 蓝 · 右上
        AmbientBlob(Color(0xFF35C8BE), 0.28f, 0.88f, 1.18f, 0.09f, 3.10f, 0.24f), // 青 · 左下
        AmbientBlob(Color(0xFFB06A9E), 0.84f, 0.93f, 1.10f, 0.08f, 4.50f, 0.18f), // 玫瑰 · 右下
        AmbientBlob(Color(0xFF5E6BD8), 0.52f, 0.55f, 1.60f, 0.06f, 2.40f, 0.16f)  // 靛 · 中央体积光
    )

    // 胶片颗粒：纹理只创建一次，ShaderBrush 平铺整屏
    val grainBrush = remember {
        val noise = createNoiseBitmap()
        ShaderBrush(
            ImageShader(
                noise,
                TileMode.Repeated,
                TileMode.Repeated
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val minDim = size.minDimension

                // ── 1. Base Pass：四角多点深度渐变 ──
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF101128),
                            Color(0xFF171737),
                            Color(0xFF1D1B3E),
                            Color(0xFF252047)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                // 顶部再压一层深色，模拟镜头上缘进光衰减
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0A1C).copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.30f
                    )
                )

                // ── 2. Light Pass：五团软光域（双层衰减散射） ──
                blobs.forEach { b ->
                    val cx = size.width * (b.baseX + b.driftAmp * sin(t + b.phase))
                    val cy = size.height * (b.baseY + b.driftAmp * cos(t * 0.8f + b.phase))
                    val breathe = 0.82f + 0.18f * sin(t * 1.3f + b.phase)
                    // 内核（亮）
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                b.color.copy(alpha = b.maxAlpha * breathe),
                                b.color.copy(alpha = b.maxAlpha * 0.40f * breathe),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = minDim * b.radius * 0.62f
                        ),
                        radius = minDim * b.radius * 0.62f,
                        center = Offset(cx, cy)
                    )
                    // 外层散射（宽而淡，模拟大气散射）
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                b.color.copy(alpha = b.maxAlpha * 0.22f * breathe),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = minDim * b.radius
                        ),
                        radius = minDim * b.radius,
                        center = Offset(cx, cy)
                    )
                }

                // ── 3. Sweep Pass：极缓旋转扫光 ──
                val sweepCx = size.width * 0.5f
                val sweepCy = size.height * 0.34f
                val sweepColors = List(12) { i ->
                    val a = 0.030f + 0.030f * sin(sweep + i * (PI / 6f).toFloat())
                    Color.White.copy(alpha = a.coerceAtLeast(0f))
                }
                drawCircle(
                    brush = Brush.sweepGradient(colors = sweepColors, center = Offset(sweepCx, sweepCy)),
                    radius = minDim * 1.4f,
                    center = Offset(sweepCx, sweepCy)
                )

                // ── 4. Horizon Pass：底部冷色地平线 + 上下夹暗 ──
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3B4E8F).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        startY = size.height * 0.72f,
                        endY = size.height
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF08081A).copy(alpha = 0.50f),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF0A0A20).copy(alpha = 0.58f)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )

                // ── 6. Vignette：径向暗角（镜头光学） ──
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF05050F).copy(alpha = 0.42f)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.46f),
                        radius = minDim * 1.05f
                    )
                )

                // ── 7. Grain Pass：胶片颗粒（Overlay 混合去条带） ──
                drawRect(
                    brush = grainBrush,
                    alpha = 0.045f,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Overlay
                )
            }
    )
}

// ====================================================================
// 应用背景（AppBackground · 可自定义）
//
// 四种模式（设置页可切换，默认液态动态背景）：
//   LIQUID   液态动态背景（游戏级渐变 + 光斑）· 应用默认
//   GALLERY  相册自定义图片（复制到私有目录，IO 线程解码）
//   COLOR    纯色背景 + 暗角 + 胶片颗粒
//
// 原内置「默认壁纸」已按需求移除，default_wallpaper.jpg 一并删除。
// 图片一律 ContentScale.Crop：保持原比例居中裁剪铺满屏幕，
// 任意屏幕比例（16:9 / 18:9 / 折叠屏 / 平板）均无拉伸变形。
// ====================================================================

@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val cfg = AppSettings.bgConfig
    // 注册为全局液态玻璃的折射采样源：所有 liquidGlass() 组件都会真实
    // 采样/折射此处绘制的背景（壁纸、纯色或液态动态）
    val backdrop = LocalAppBackdrop.current
    val bgModifier = if (backdrop != null) modifier.layerBackdrop(backdrop) else modifier
    when (cfg.mode) {
        BgMode.LIQUID -> LiquidBackground(bgModifier)

        BgMode.COLOR -> ColorBackground(cfg.color, bgModifier)

        BgMode.GALLERY -> {
            val context = LocalContext.current
            val path = remember { AppSettings.customWallpaperFile(context).absolutePath }
            val bitmap by produceState<Bitmap?>(initialValue = null, path) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val bound = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, bound)
                        var sample = 1
                        while (max(bound.outWidth, bound.outHeight) / (sample * 2) >= 1440) sample *= 2
                        BitmapFactory.decodeFile(
                            path,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                    }.getOrNull()
                }
            }
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = bgModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                PhotoScrim(bgModifier)
            } else {
                // 图片尚未解码完成：深色打底避免闪白
                ColorBackground(0xFF14151F, bgModifier)
            }
        }

        else -> {
            // 原「默认壁纸」已移除，历史遗留 DEFAULT / 未知模式统一兜底为液态动态
            LiquidBackground(bgModifier)
        }
    }
}

/** 图片压暗蒙版：纵向渐变 + 轻暗角，保证玻璃卡片与文字可读 */
@Composable
private fun PhotoScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF06070C).copy(alpha = 0.40f),
                            Color(0xFF06070C).copy(alpha = 0.20f),
                            Color(0xFF06070C).copy(alpha = 0.24f),
                            Color(0xFF05060A).copy(alpha = 0.55f)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF05060A).copy(alpha = 0.18f)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.maxDimension * 0.85f
                    )
                )
            }
    )
}

/** 纯色背景：底色 + 径向提亮/暗角 + 胶片颗粒（细节质感） */
@Composable
private fun ColorBackground(color: Long, modifier: Modifier = Modifier) {
    val grainBrush = remember {
        ShaderBrush(ImageShader(createNoiseBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = Color(color))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.30f)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = size.maxDimension * 0.90f
                    )
                )
                drawRect(
                    brush = grainBrush,
                    alpha = 0.045f,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Overlay
                )
            }
    )
}

// ====================================================================
// 雪花层（Snowfall · 真实降雪 · 前景覆盖）
//
// 三层景深模拟真实雪幕：
//   · 远景 34 片：小而慢的失焦光斑（大虚化 → 空气纵深）
//   · 中景 30 片：柔光雪粒（下落轨迹清晰可辨）
//   · 近景 20 片：六臂晶体雪花（描线结晶 + 各自自转）
// 真实感细节：
//   · 双频正弦摆动（两支不同频率叠加 → 无规律自然漂移）
//   · 阵风场：整层慢速左右漂移，远近层位移不同（视差）
//   · 闪烁：每片透明度低频呼吸（雪晶折射的微光变化）
//   · 旋转：晶体自转（每周期整数圈 → t 回绕时连续不跳变）
//   · 层次透明度：近景最亮、远景朦胧（相机景深）
// ====================================================================

private data class Snowflake(
    val x0: Float,          // 基础横坐标（0~1）
    val y0: Float,          // 基础纵坐标（0~1）
    val radius: Float,      // 半径（相对短边倍率）
    val speed: Int,         // 每周期下落整屏数（整数保证循环连续）
    val sway1: Float,       // 主摆动幅度
    val sway2: Float,       // 次摆动幅度
    val swayFreq2: Int,     // 次摆频（整数保证循环连续）
    val phase: Float,       // 相位
    val alpha: Float,       // 基础透明度
    val twinkleFreq: Int,   // 闪烁频率（整数）
    val twinklePhase: Float,
    val spinTurns: Int,     // 每周期自转整圈数（0 = 不转）
    val spinDir: Float,     // 自转方向 ±1
    val layer: Int          // 0 远景 1 中景 2 近景
)

/** 六臂晶体雪花路径（单位坐标，中心原点，臂长 1） */
private fun buildCrystalPath(): Path {
    val p = Path()
    val armCount = 6
    repeat(armCount) { i ->
        val a = i * (PI / 3f).toFloat()
        val dx = cos(a)
        val dy = sin(a)
        // 主臂：中心 → 尖端
        p.moveTo(0f, 0f)
        p.lineTo(dx, dy)
        // 一级侧枝（臂长 55% 处，±60° 分叉）
        val b1x = dx * 0.55f
        val b1y = dy * 0.55f
        val ba = (PI / 3f).toFloat()
        val l1 = 0.30f
        p.moveTo(b1x, b1y)
        p.lineTo(b1x + cos(a + ba) * l1, b1y + sin(a + ba) * l1)
        p.moveTo(b1x, b1y)
        p.lineTo(b1x + cos(a - ba) * l1, b1y + sin(a - ba) * l1)
        // 二级小枝（臂长 82% 处）
        val b2x = dx * 0.82f
        val b2y = dy * 0.82f
        val l2 = 0.17f
        p.moveTo(b2x, b2y)
        p.lineTo(b2x + cos(a + ba) * l2, b2y + sin(a + ba) * l2)
        p.moveTo(b2x, b2y)
        p.lineTo(b2x + cos(a - ba) * l2, b2y + sin(a - ba) * l2)
    }
    return p
}

@Composable
fun SnowfallLayer(
    modifier: Modifier = Modifier,
    farCount: Int = 34,
    midCount: Int = 30,
    nearCount: Int = 20
) {
    val transition = rememberInfiniteTransition(label = "snow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snowT"
    )

    val crystalPath = remember { buildCrystalPath() }

    val flakes = remember(farCount, midCount, nearCount) {
        val rnd = kotlin.random.Random(2026)
        buildList {
            // 远景：小 / 慢 / 朦胧失焦
            repeat(farCount) {
                add(
                    Snowflake(
                        x0 = rnd.nextFloat(), y0 = rnd.nextFloat(),
                        radius = 0.5f + rnd.nextFloat() * 0.8f,
                        speed = 1,
                        sway1 = 0.004f + rnd.nextFloat() * 0.007f,
                        sway2 = 0.002f + rnd.nextFloat() * 0.004f,
                        swayFreq2 = if (rnd.nextBoolean()) 2 else 3,
                        phase = rnd.nextFloat() * (2f * PI).toFloat(),
                        alpha = 0.10f + rnd.nextFloat() * 0.18f,
                        twinkleFreq = 1 + rnd.nextInt(2),
                        twinklePhase = rnd.nextFloat() * (2f * PI).toFloat(),
                        spinTurns = 0, spinDir = 1f, layer = 0
                    )
                )
            }
            // 中景：中等 / 柔光雪粒
            repeat(midCount) {
                add(
                    Snowflake(
                        x0 = rnd.nextFloat(), y0 = rnd.nextFloat(),
                        radius = 1.0f + rnd.nextFloat() * 1.3f,
                        speed = if (rnd.nextBoolean()) 1 else 2,
                        sway1 = 0.009f + rnd.nextFloat() * 0.014f,
                        sway2 = 0.004f + rnd.nextFloat() * 0.008f,
                        swayFreq2 = if (rnd.nextBoolean()) 2 else 3,
                        phase = rnd.nextFloat() * (2f * PI).toFloat(),
                        alpha = 0.22f + rnd.nextFloat() * 0.30f,
                        twinkleFreq = 2 + rnd.nextInt(3),
                        twinklePhase = rnd.nextFloat() * (2f * PI).toFloat(),
                        spinTurns = 0, spinDir = 1f, layer = 1
                    )
                )
            }
            // 近景：大 / 快 / 六臂晶体 + 自转
            repeat(nearCount) {
                add(
                    Snowflake(
                        x0 = rnd.nextFloat(), y0 = rnd.nextFloat(),
                        radius = 2.2f + rnd.nextFloat() * 2.8f,
                        speed = 3 + rnd.nextInt(2),
                        sway1 = 0.016f + rnd.nextFloat() * 0.022f,
                        sway2 = 0.008f + rnd.nextFloat() * 0.012f,
                        swayFreq2 = 2 + rnd.nextInt(2),
                        phase = rnd.nextFloat() * (2f * PI).toFloat(),
                        alpha = 0.48f + rnd.nextFloat() * 0.40f,
                        twinkleFreq = 3 + rnd.nextInt(3),
                        twinklePhase = rnd.nextFloat() * (2f * PI).toFloat(),
                        spinTurns = 1 + rnd.nextInt(2),
                        spinDir = if (rnd.nextBoolean()) 1f else -1f,
                        layer = 2
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val shortSide = size.minDimension
                val tau = (2f * PI).toFloat()

                // 阵风场：双频叠加的缓慢左右漂移
                val gust = 0.016f * sin(tau * t + 0.7f) + 0.008f * sin(tau * 2f * t)

                flakes.forEach { f ->
                    // 纵向取模循环（整数速度 → 回绕连续）
                    val yNorm = (f.y0 + t * f.speed) % 1f
                    // 入屏 / 出屏渐隐（上下 8% 区域）
                    val edgeFade = when {
                        yNorm < 0.08f -> yNorm / 0.08f
                        yNorm > 0.92f -> (1f - yNorm) / 0.08f
                        else -> 1f
                    }
                    // 双频摆动 + 视差风场（远景受风影响小）
                    val windScale = when (f.layer) {
                        0 -> 0.45f
                        1 -> 0.80f
                        else -> 1f
                    }
                    val sway = f.sway1 * sin(tau * t + f.phase) +
                            f.sway2 * sin(tau * f.swayFreq2 * t + f.phase * 1.7f)
                    val cx = w * (f.x0 + gust * windScale + sway)
                    val cy = h * yNorm
                    val r = shortSide * f.radius * 0.0058f
                    // 闪烁（±25% 低频呼吸）
                    val tw = 0.75f + 0.25f * sin(tau * f.twinkleFreq * t + f.twinklePhase)
                    val a = (f.alpha * tw).coerceIn(0f, 1f) * edgeFade
                    if (a <= 0.01f) return@forEach

                    when (f.layer) {
                        0 -> {
                            // 远景：失焦光斑（径向渐变虚化圆）
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = a * 0.55f),
                                        Color.White.copy(alpha = a * 0.22f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = r * 3.2f
                                ),
                                radius = r * 3.2f,
                                center = Offset(cx, cy)
                            )
                        }

                        1 -> {
                            // 中景：柔光雪粒（核 + 辉光）
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = a),
                                        Color.White.copy(alpha = a * 0.40f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = r * 2.2f
                                ),
                                radius = r * 2.2f,
                                center = Offset(cx, cy)
                            )
                        }

                        else -> {
                            // 近景：六臂晶体（自转）+ 底层柔光
                            val spinDeg = f.spinDir * (360f * t * f.spinTurns + f.phase * 57.29578f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = a * 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = r * 2.6f
                                ),
                                radius = r * 2.6f,
                                center = Offset(cx, cy)
                            )
                            withTransform({
                                translate(cx, cy)
                                rotate(spinDeg, pivot = Offset.Zero)
                                scale(r, r, pivot = Offset.Zero)
                            }) {
                                drawPath(
                                    path = crystalPath,
                                    color = Color.White.copy(alpha = a),
                                    style = Stroke(
                                        width = 0.11f,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }
    )
}

// ====================================================================
// 全局点击光效层（Global Energy Ripple Overlay）
//
// 覆盖整个屏幕（含导航栏、弹窗之外的任意区域）：
//   · 触摸监听挂载在 View 层（AndroidComposeView），onTouch 返回
//     false = 事件原样继续派发给 Compose 界面 → 零拦截，
//     按钮 / 滑条 / 开关 / 底部导航页面切换完全不受影响
//   · 触点迸发能量涟漪（核心光斑 + 双冲击波 + 回响 + 星芒）
//   · 涟漪绘制在所有内容之上，实现「点哪哪亮」
// ====================================================================

@Composable
fun GlobalRippleOverlay(modifier: Modifier = Modifier) {
    val ripples = remember { mutableStateListOf<EnergyRipple>() }
    val tick by rememberInfiniteTransition(label = "globalRippleTick").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "globalRippleTickT"
    )

    // View 层观察者：只在 ACTION_DOWN 记录触点，永不消费事件
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = android.view.View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                ripples.add(EnergyRipple(event.x, event.y, System.nanoTime()))
                // 防御上限：避免极端连点堆积
                if (ripples.size > 10) ripples.removeAt(0)
            }
            false
        }
        view.setOnTouchListener(listener)
        onDispose { view.setOnTouchListener(null) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (ripples.isEmpty()) return@drawBehind
                @Suppress("UNUSED_EXPRESSION")
                tick.let { }

                val now = System.nanoTime()
                ripples.removeAll { now - it.startNanos > RIPPLE_LIFETIME }
                if (ripples.isEmpty()) return@drawBehind

                ripples.forEach { ripple ->
                    val age = (now - ripple.startNanos).coerceAtLeast(0)
                    val tR = (age.toFloat() / RIPPLE_LIFETIME).coerceIn(0f, 1f)
                    val cx = ripple.x
                    val cy = ripple.y
                    val reach = size.maxDimension * 0.55f

                    // 1. 能量核心
                    val coreT = (tR / 0.33f).coerceIn(0f, 1f)
                    val coreR = reach * 0.34f * easeOutCubic(coreT)
                    val coreA = (1f - coreT).pow(1.6f) * 0.50f
                    if (coreA > 0.003f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = coreA * 1.4f),
                                    Color(0xFFD9DEEB).copy(alpha = coreA * 0.7f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = coreR.coerceAtLeast(1f)
                            ),
                            radius = coreR.coerceAtLeast(1f),
                            center = Offset(cx, cy)
                        )
                    }

                    // 2. 主冲击波
                    val waveT = ((tR - 0.02f) / 0.80f).coerceIn(0f, 1f)
                    if (waveT > 0f && waveT < 1f) {
                        val wR = reach * easeOutQuart(waveT)
                        val wA = (1f - waveT).pow(1.3f) * 0.60f
                        drawCircle(
                            color = Color.White.copy(alpha = wA),
                            radius = wR.coerceAtLeast(1f),
                            center = Offset(cx, cy),
                            style = Stroke(width = (2.6.dp.toPx() * (1f - waveT * 0.55f)).coerceAtLeast(0.6f))
                        )
                    }

                    // 3. 次级回响
                    val echoT = ((tR - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    if (echoT > 0f && echoT < 1f) {
                        val eR = reach * easeOutQuart(echoT) * 1.12f
                        val eA = (1f - echoT).pow(2f) * 0.30f
                        drawCircle(
                            color = Color(0xFFD9DEEB).copy(alpha = eA),
                            radius = eR.coerceAtLeast(1f),
                            center = Offset(cx, cy),
                            style = Stroke(width = (1.3.dp.toPx() * (1f - echoT * 0.6f)).coerceAtLeast(0.4f))
                        )
                    }

                    // 4. 星芒闪光（能量迸发瞬间，十字光线）
                    val flareT = (tR / 0.18f).coerceIn(0f, 1f)
                    if (flareT < 1f) {
                        val flareA = (1f - flareT).pow(2f) * 0.55f
                        val flareLen = reach * 0.30f * easeOutCubic(flareT)
                        val stroke = (2.0.dp.toPx() * (1f - flareT)).coerceAtLeast(0.5f)
                        repeat(4) { i ->
                            val ang = i * (PI / 2f).toFloat()
                            drawLine(
                                color = Color.White.copy(alpha = flareA),
                                start = Offset(cx + flareLen * 0.25f * cos(ang), cy + flareLen * 0.25f * sin(ang)),
                                end = Offset(cx + flareLen * cos(ang), cy + flareLen * sin(ang)),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }
    )
}

// ====================================================================
// L1 玻璃卡片
// ====================================================================

@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tintTop: Color? = null,
    tintBottom: Color? = null,
    contentPadding: Dp = 16.dp,
    shadowElevation: Dp = 7.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = rememberGlassColors(tintTop, tintBottom)
    val clickInteraction = remember(onClick != null) { MutableInteractionSource() }
    // 按压进度：走 liquidGlass 的 layerBlock 缩放玻璃面板，
    // 背景折射保持原位（graphicsLayer 缩放会让折射背景跟随缩放，官方文档禁止）
    val cardPressed by clickInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 1600f),
        label = "cardPressScale"
    )
    val base = if (onClick != null) {
        Modifier
            .clickable(interactionSource = clickInteraction, indication = null) {
                onClick()
            }
            .pressRipple(clickInteraction, clipShape = shape, intensity = 1.1f)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .glassShadow(shadowElevation, shape)
            .then(base)
            .liquidGlass(
                shape = shape,
                // 卡片 24dp 圆角：轻磨砂保通透，lens height ≤ 最小圆角半径
                blurRadius = 6.dp,
                lensHeight = 12.dp,
                lensAmount = 20.dp,
                layerBlock = {
                    scaleX = cardScale
                    scaleY = cardScale
                }
            )
            .glass(shape, colors)
            .padding(contentPadding),
        content = content
    )
}

// ====================================================================
// L2 胶囊玻璃按钮
// ====================================================================

enum class GlassButtonStyle { Primary, Glass, Danger }

/**
 * 液态玻璃按钮（胶囊形 · 保时捷工程风 · 单色系规格，无彩色）。
 * Primary: 提亮玻璃（半透明白玻璃 + 明亮边环 + 浅色内容 + 微光扫过）
 * Glass:   透明玻璃底 + 亮中性内容
 * Danger:  与 Glass 同规格的中性玻璃（保留枚举兼容旧调用点）
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    height: Dp = 48.dp,
    shimmer: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    // 按压进度走 liquidGlass layerBlock（背景折射不跟手缩放）
    val pressed by interaction.collectIsPressedAsState()
    val pressScaleAnim by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 1600f),
        label = "buttonPressScale"
    )

    val container = when (style) {
        GlassButtonStyle.Primary -> {
            // 提亮玻璃规格：半透明白玻璃底 + 亮边环 + 内容光晕（与整体液态玻璃同语言）
            val glass = Modifier
                .glassShadow(8.dp, shape)
                .glow(Color.White.copy(alpha = 0.22f), radiusFraction = 1.6f)
                .glass(
                    shape,
                    GlassColors(
                        tintTop = Color.White.copy(alpha = 0.26f),
                        tintBottom = Color.White.copy(alpha = 0.12f),
                        highlight = Color.White.copy(alpha = 0.42f),
                        rimBright = Color.White.copy(alpha = 0.80f),
                        rimDim = Color.Black.copy(alpha = 0.12f)
                    )
                )
            if (shimmer) glass.shimmerSweep(bandColor = Color(0xFFD9DEEB)) else glass
        }

        GlassButtonStyle.Glass -> Modifier
            .glassShadow(4.dp, shape)
            .glass(shape, rememberGlassColors())

        GlassButtonStyle.Danger -> Modifier
            .glassShadow(4.dp, shape)
            .glass(shape, rememberGlassColors())
    }

    val contentColor = when (style) {
        GlassButtonStyle.Primary -> Color(0xFFF3F5FA)
        GlassButtonStyle.Glass -> Color(0xFFE9EBF4)
        GlassButtonStyle.Danger -> Color(0xFFE9EBF4)
    }

    Box(
        modifier = modifier
            .height(height)
            .liquidGlass(
                shape = shape,
                blurRadius = 6.dp,
                lensHeight = 12.dp,
                lensAmount = 20.dp,
                layerBlock = {
                    scaleX = pressScaleAnim
                    scaleY = pressScaleAnim
                }
            )
            .then(container)
            .pressRipple(
                interaction,
                clipShape = shape,
                color = when (style) {
                    GlassButtonStyle.Primary -> Color.White
                    GlassButtonStyle.Glass -> Color(0xFFD9DEEB)
                    GlassButtonStyle.Danger -> Color(0xFFD9DEEB)
                },
                intensity = 1.15f
            )
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

/** 圆形玻璃图标按钮 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    tintTop: Color? = null,
    tintBottom: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    // 图标无彩色规格：容器与图标统一中性白玻璃
    val colors = rememberGlassColors()
    // 按压进度走 liquidGlass layerBlock（背景折射不跟手缩放）
    val iconPressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (iconPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 1600f),
        label = "iconPressScale"
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .glassShadow(3.dp, CircleShape)
            .liquidGlass(
                shape = CircleShape,
                blurRadius = 4.dp,
                lensHeight = 6.dp,
                lensAmount = 12.dp,
                layerBlock = {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            )
            .glass(CircleShape, colors)
            .pressRipple(interaction, clipShape = CircleShape, color = tint, intensity = 1.2f)
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color(0xFFE9EBF4),
            modifier = Modifier.size(size * 0.47f)
        )
    }
}

/**
 * 可选中胶囊玻璃芯片：选中时提亮填充 + 主题色光晕 + 描边点亮。
 * 对应参考图中圆形快捷开关的「激活态」规格。
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    // 单色系规格：选中态为高亮白玻璃，不使用主题色
    val active = Color.White

    val borderColor by animateColorAsState(
        targetValue = if (selected) active else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(240),
        label = "chipBorder"
    )
    val fillTop by animateColorAsState(
        targetValue = if (selected) active.copy(alpha = 0.30f)
        else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(240),
        label = "chipTop"
    )
    val fillBottom by animateColorAsState(
        targetValue = if (selected) active.copy(alpha = 0.14f)
        else Color.White.copy(alpha = 0.04f),
        animationSpec = tween(240),
        label = "chipBottom"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "chipScale"
    )
    // 按压进度走 liquidGlass layerBlock（背景折射不跟手缩放）
    val chipPressed by interaction.collectIsPressedAsState()
    val chipPressScale by animateFloatAsState(
        targetValue = if (chipPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 1600f),
        label = "chipPressScale"
    )

    Box(
        modifier = modifier
            .glassShadow(if (selected) 6.dp else 2.dp, shape)
            .liquidGlass(
                shape = shape,
                blurRadius = 4.dp,
                lensHeight = 8.dp,
                lensAmount = 12.dp,
                layerBlock = {
                    val s = scale * chipPressScale
                    scaleX = s
                    scaleY = s
                }
            )
            .glow(if (selected) active else Color.Transparent, radiusFraction = 1.5f)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                // 玻璃底
                drawOutline(
                    outline,
                    Brush.verticalGradient(listOf(fillTop, fillBottom))
                )
                // 顶部菲涅尔亮线
                drawOutline(
                    outline,
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            if (selected) Color.White.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                // 非对称描边（未选中浅灰、选中主色）
                drawOutline(
                    outline,
                    Brush.linearGradient(
                        listOf(
                            borderColor.copy(alpha = if (selected) 0.85f else 0.85f),
                            borderColor.copy(alpha = if (selected) 0.30f else 0.30f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    style = Stroke(width = if (selected) 1.6.dp.toPx() else 1.dp.toPx())
                )
            }
            .pressRipple(
                interaction,
                clipShape = shape,
                color = if (selected) active else Color.White,
                intensity = 1.05f
            )
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
            }
            .padding(vertical = 9.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** 呼吸状态点 */
@Composable
fun GlowDot(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 8.dp) {
    Box(
        modifier
            .size(dotSize)
            .pulse()
            .glow(color, radiusFraction = 2.6f)
            .background(color, CircleShape)
    )
}

// ====================================================================
// L3 悬浮胶囊玻璃导航栏（参考图规格）
//
//   · 居中悬浮胶囊容器（非通栏、自适应宽度）
//   · 选中项：图标 + 文字双元素展开，独立圆角高亮底（圆中圆）
//   · 未选中项：仅文字（灰色），节省横向空间
//   · 切换时容器宽度弹性自适应 + 图标弹出动画
// ====================================================================

data class GlassNavTab(val icon: ImageVector, val label: String)

/** Dock 内部：单个 Tab 的位置/宽度，用于滑动指示条测量 */
private data class TabMetrics(val left: Int, val width: Int)

@Composable
fun GlassNavBar(
    tabs: List<GlassNavTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // -1 = 无选中（当前页由卫星按钮承载，如设置页）
    val safeIndex = if (selected in tabs.indices) selected else -1
    val density = LocalDensity.current

    // 各 Tab 位置（onGloballyPositioned 采集；坐标基于内容区，指示条同处内容区故直接对齐）
    var tabMetrics by remember { mutableStateOf(List<TabMetrics?>(tabs.size) { null }) }

    // 光斑（指示条）位置用「连续小数索引」表达：0.0 = 第 1 个 Tab，1.5 = 第 2、3 个之间。
    //   · 点击切换 → 弹簧扫到目标索引（对齐视频里光斑从旧标签扫到新标签）
    //   · 手指拖动 → 直接取手指位置的连续索引，逐帧贴手
    // 用单一动画量，避免位移/宽度两条动画各自为政造成的迟滞与抖动。
    val slot = remember { Animatable(0f) }
    // 拖动中光斑的跟手位置（null = 未拖动）。
    // 手势所在的 AwaitPointerEventScope 是 RestrictsSuspension 作用域，只能调用
    // 该作用域自身的挂起函数，不能直接调 Animatable.snapTo，故跟手位置经此状态
    // 传出，由下方 LaunchedEffect 在普通协程里落地。
    var dragSlot by remember { mutableStateOf<Float?>(null) }
    // 渲染用位置：拖动中贴手，否则取动画值
    val slotValue = dragSlot ?: slot.value

    // 拖动中：暂停「选中项驱动的弹簧」，把控制权完全交给手势，避免两个动画互相打断
    var dragging by remember { mutableStateOf(false) }
    // 拖动中的预览选中项：图标/文字高亮跟手，但整页切换推迟到松手提交。
    // 拖动途中每越过一个 Tab 就切一次页会反复触发 320ms 整页转场，
    // 页面又全是液态玻璃，这才是原来「滑动不流畅」的主因。
    var previewIndex by remember { mutableStateOf(-1) }
    // 手势闭包内必须读到最新值：pointerInput 只在 key 变化时重启，直接捕获
    // safeIndex / onSelect 会读到首次组合时的旧值——这正是原来
    //「拖动只能在前两个 Tab 之间来回」的根因。
    val safeIndexState = rememberUpdatedState(safeIndex)
    val onSelectState = rememberUpdatedState(onSelect)

    // 高亮/光斑跟随的实际索引：拖动时跟手预览，否则跟随已提交选中项
    val activeIndex = if (dragging && previewIndex in tabs.indices) previewIndex else safeIndex

    // Tab 间距 3.dp，用于把光斑覆盖到相邻 Tab 之间的缝隙
    val gapPx = with(density) { 3.dp.toPx() }

    // 第 i 个 Tab 的「槽」左右边界（含左右半个间距 → 相邻槽首尾相接，不留空档）
    fun slotBounds(i: Int): Pair<Float, Float> {
        val m = tabMetrics.getOrNull(i) ?: return 0f to 0f
        return (m.left - gapPx / 2f) to (m.left + m.width + gapPx / 2f)
    }

    // Tab 中心 x：作为「手指位置 → 连续索引」分段线性映射的锚点
    fun centerOf(i: Int): Float {
        val m = tabMetrics.getOrNull(i) ?: return 0f
        return m.left + m.width / 2f
    }

    // 手指 x → 连续索引（两端钳制），保证跟手过程无跳变
    fun indexAt(x: Float): Float {
        for (i in 0 until tabs.size - 1) {
            val c0 = centerOf(i)
            val c1 = centerOf(i + 1)
            if (x <= c1) {
                if (c1 <= c0) return i.toFloat()
                return i + ((x - c0) / (c1 - c0)).coerceIn(0f, 1f)
            }
        }
        return (tabs.size - 1).toFloat()
    }

    // 连续索引 → 光斑 rect：左右边界各自插值，宽度随相邻 Tab 宽度自然形变
    fun indicatorRect(f: Float): Pair<Float, Float> {
        val last = tabs.size - 1
        val c = f.coerceIn(0f, last.toFloat())
        val i0 = floor(c).toInt().coerceIn(0, last)
        val i1 = (i0 + 1).coerceAtMost(last)
        val t = c - i0
        val (l0, r0) = slotBounds(i0)
        val (l1, r1) = slotBounds(i1)
        val l = l0 + (l1 - l0) * t
        val r = r0 + (r1 - r0) * t
        return l to (r - l)
    }

    // 选中项变化 → 光斑弹簧扫到目标 Tab；拖动期间不动，交由手势跟手
    LaunchedEffect(dragging, safeIndex, tabMetrics) {
        if (dragging) return@LaunchedEffect
        dragSlot?.let {
            // 先把动画值对齐到手指松开处，再起步吸附，避免中间回跳一帧
            slot.snapTo(it)
            dragSlot = null
        }
        if (safeIndex in tabs.indices) {
            slot.animateTo(safeIndex.toFloat(), spring(dampingRatio = 0.62f, stiffness = 480f))
        }
    }

    // AndroidLiquidGlass 深度对接（LiquidBottomTabs 参考规格）：
    //   · tabsBackdrop  记录 Dock 自身图标/文字，指示条经 CombinedBackdrop 一并折射
    //   · pressProgress 选中 Tab 按压进度：折射加深 + 色散 + 内阴影 + 面板弹簧放大
    val appBackdrop = LocalAppBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = if (appBackdrop != null) rememberCombinedBackdrop(appBackdrop, tabsBackdrop) else null
    var pressedTabIndex by remember { mutableStateOf(-1) }
    val pressProgress by animateFloatAsState(
        targetValue = if (safeIndex >= 0 && (pressedTabIndex == safeIndex || dragging)) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "tabPressProgress"
    )

    Box(
        modifier = modifier
            .liquidGlass(
                // G2 连续胶囊：官方 shapes 库，圆角更圆润
                shape = Capsule(),
                blurRadius = 8.dp,
                lensHeight = 20.dp,
                lensAmount = 24.dp,
                layerBlock = {
                    val s = 1f + 0.015f * pressProgress
                    scaleX = s
                    scaleY = s
                }
            )
            .glass(RoundedCornerShape(50), rememberGlassColors())
            .padding(horizontal = 7.dp, vertical = 7.dp)
    ) {
        // 隐藏副本行：透明绘制进 tabsBackdrop，供指示条折射采样
        // （参考组件同款手法：副本先行绘制，保证指示条读取到当帧内容）
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0f }
                .layerBackdrop(tabsBackdrop)
        ) {
            tabs.forEachIndexed { _, tab ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(38.dp).padding(horizontal = 13.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = Color(0xFFF3F5FA),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                        color = Color(0xFFF3F5FA),
                        maxLines = 1
                    )
                }
            }
        }

        // 滑动高亮光斑：真实液态玻璃面板（折射背景 + Dock 内容），垫底绘制。
        // 位置/宽度每帧由连续索引派生 → 拖动贴手、点击弹簧扫过
        if (activeIndex in tabs.indices) {
            val (indLeft, indWidth) = indicatorRect(slotValue)
            // 离目标越远越「胖」，落下后收拢 → 复刻视频里光斑扫过的液态拉伸
            val travel = abs(slotValue - activeIndex.toFloat())
            val stretch = with(density) { (travel * 8.dp.toPx()).coerceAtMost(16.dp.toPx()) }
            Box(
                modifier = Modifier
                    .offset { IntOffset((indLeft - stretch / 2f).roundToInt(), 0) }
                    .width(with(density) { (indWidth + stretch).toDp() })
                    .height(38.dp)
                    .then(
                        if (indicatorBackdrop != null) {
                            Modifier.liquidGlassPanel(
                                backdrop = indicatorBackdrop,
                                shape = Capsule(),
                                pressProgress = { pressProgress }
                            )
                        } else {
                            Modifier
                        }
                    )
                    .glow(Color.White.copy(alpha = 0.18f), radiusFraction = 1.7f)
                    .glass(
                        RoundedCornerShape(19.dp),
                        GlassColors(
                            // 提亮玻璃规格：半透明白玻璃 + 亮边环 + 浅色内容（与原选中态一致）
                            tintTop = Color.White.copy(alpha = 0.24f),
                            tintBottom = Color.White.copy(alpha = 0.10f),
                            highlight = Color.White.copy(alpha = 0.40f),
                            rimBright = Color.White.copy(alpha = 0.78f),
                            rimDim = Color.Black.copy(alpha = 0.12f)
                        )
                    )
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerInput(tabs.size) {
                // 拖动切页：光斑与图标高亮连续跟手，松手才提交整页切换；
                // 未超过触摸斜率仍交给 Tab 的 clickable，保证纯点击不被误判为拖动。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x
                    var isDrag = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (!isDrag) {
                                // 未超过触摸斜率前不算拖拽（避免点击时双重切换）
                                if (abs(change.position.x - downX) <
                                    viewConfiguration.touchSlop
                                ) continue
                                isDrag = true
                                dragging = true
                            }
                            val f = indexAt(change.position.x)
                            // 只写状态，不做挂起调用（本作用域禁止）
                            dragSlot = f
                            previewIndex = f.roundToInt().coerceIn(0, tabs.size - 1)
                            // 消费拖动事件，避免子 Tab 的 clickable 在松手时补一次点击
                            change.consume()
                        }
                    } finally {
                        if (isDrag) {
                            // 松手才真正切页：整段拖动只触发一次整页转场，避免拖动途中抖动
                            val target = previewIndex
                            if (target in tabs.indices && target != safeIndexState.value) {
                                onSelectState.value(target)
                            }
                            previewIndex = -1
                            dragging = false
                        }
                    }
                }
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                // 用 activeIndex：拖动时图标/文字高亮跟手预览，松手提交后与选中项一致
                val isSelected = index == activeIndex
                val interaction = remember { MutableInteractionSource() }
                // 按压追踪：选中 Tab 的按压进度驱动液态指示条折射/色散/放大
                val tabPressed by interaction.collectIsPressedAsState()
                LaunchedEffect(tabPressed) {
                    if (tabPressed) {
                        pressedTabIndex = index
                    } else if (pressedTabIndex == index) {
                        pressedTabIndex = -1
                    }
                }
                // iOS 液态 Dock：所有 Tab 始终同时显示「图标 + 文字」，
                // 非选中整体收缩 + 变暗，选中项以弹簧微弹到全亮 + 原大。
                // 选中态的玻璃指示条在底层随切换弹滑，避免"仅选中展开图标"的割裂动画。
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFF3F5FA)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    animationSpec = tween(220),
                    label = "navColor$index"
                )
                val contentAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.46f,
                    animationSpec = tween(200),
                    label = "navAlpha$index"
                )
                val contentScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.89f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 680f),
                    label = "navScale$index"
                )

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .onGloballyPositioned { coords ->
                            val m = TabMetrics(coords.positionInParent().x.roundToInt(), coords.size.width)
                            if (tabMetrics[index] != m) {
                                tabMetrics = tabMetrics.toMutableList().also { it[index] = m }
                            }
                        }
                        .pressRipple(
                            interaction,
                            clipShape = RoundedCornerShape(19.dp),
                            color = Color.White,
                            intensity = 1.1f
                        )
                        .clickable(interactionSource = interaction, indication = null) {
                            if (!isSelected) {
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                            alpha = contentAlpha
                        }
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// L3 分离式圆形玻璃按钮（导航卫星按钮，参考图“+”钮形态）
//
//   · 与主导航胶囊物理分离、独立成圆
//   · 纯图标无文字（46dp 触控友好）
//   · 激活时高亮白玻璃底 + 实心深色图标（与导航选中态同规格）
// ====================================================================

@Composable
fun GlassFabButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 20.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val iconColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFF3F5FA)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        animationSpec = tween(240),
        label = "fabIconColor"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (selected) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f),
        label = "fabIconRotation"
    )
    val idleColors = rememberGlassColors()
    // 激活态：提亮玻璃（半透明白玻璃 + 亮边环 + 浅色图标），与整体液态玻璃同语言
    val activeColors = GlassColors(
        tintTop = Color.White.copy(alpha = 0.24f),
        tintBottom = Color.White.copy(alpha = 0.10f),
        highlight = Color.White.copy(alpha = 0.40f),
        rimBright = Color.White.copy(alpha = 0.80f),
        rimDim = Color.Black.copy(alpha = 0.12f)
    )
    // 按压进度走 liquidGlass layerBlock（背景折射不跟手缩放）
    val fabPressed by interaction.collectIsPressedAsState()
    val fabPressScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 1600f),
        label = "fabPressScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer {
                // 激活时轻微放大，强化「卫星按钮」选中感
                val s = if (selected) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .glow(
                if (selected) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                radiusFraction = 1.4f
            )
            .liquidGlass(
                shape = CircleShape,
                blurRadius = 10.dp,
                lensHeight = 10.dp,
                lensAmount = 20.dp,
                // 按压缩放走 layerBlock：背景折射不跟手缩放
                layerBlock = {
                    val s = fabPressScale
                    scaleX = s
                    scaleY = s
                }
            )
            .glass(CircleShape, if (selected) activeColors else idleColors)
            .pressRipple(
                interaction,
                clipShape = CircleShape,
                color = if (selected) Color(0xFFD9DEEB) else Color.White,
                intensity = 1.25f
            )
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

// ====================================================================
// 悬浮 Dock 栏（参考视频样式 · 液态玻璃）
//
//   · 液态玻璃胶囊浮岛，底部悬浮
//   · 垂直布局：图标在上、文字在下
//   · 选中项：高亮玻璃圆形气泡随切换滑动
//   · 中间：绿色实心胶囊 + 主操作按钮
// ====================================================================

data class DockTab(val icon: ImageVector, val label: String)

@Composable
fun DockBar(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    centerIcon: ImageVector,
    centerContentDescription: String,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val safeIndex = if (selected in tabs.indices) selected else -1
    val bubbleSize = 48.dp
    val activeGreen = Color(0xFF07C160)

    // 各 Tab 中心 x（px，相对于 Row）
    var tabCenters by remember { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }

    // 气泡弹簧滑动
    val bubbleAnim = remember { Animatable(0f) }
    LaunchedEffect(safeIndex, tabCenters.toList()) {
        if (safeIndex >= 0 && tabCenters[safeIndex] > 0f) {
            bubbleAnim.animateTo(
                tabCenters[safeIndex],
                spring(dampingRatio = 0.62f, stiffness = 480f)
            )
        }
    }

    // 液态玻璃折射层（与 GlassNavBar 同款架构）
    val appBackdrop = LocalAppBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = if (appBackdrop != null) rememberCombinedBackdrop(appBackdrop, tabsBackdrop) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(68.dp)
            .liquidGlass(
                shape = Capsule(),
                blurRadius = 8.dp,
                lensHeight = 20.dp,
                lensAmount = 24.dp
            )
            .glass(RoundedCornerShape(50), rememberGlassColors())
            .padding(horizontal = 5.dp, vertical = 6.dp)
    ) {
        // 隐藏副本：录制进 tabsBackdrop 供气泡折射采样
        Row(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0f }
                .layerBackdrop(tabsBackdrop),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.take(2).forEachIndexed { index, tab ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockTabColumn(tab = tab, selected = index == safeIndex)
                }
            }
            Box(Modifier.size(width = 56.dp, height = 44.dp))
            tabs.drop(2).forEachIndexed { offset, tab ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockTabColumn(tab = tab, selected = offset + 2 == safeIndex)
                }
            }
        }

        // 滑动玻璃气泡
        if (safeIndex >= 0) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (bubbleAnim.value - with(density) { (bubbleSize / 2).toPx() }).roundToInt(),
                            y = 0
                        )
                    }
                    .size(bubbleSize)
                    .align(Alignment.CenterStart)
                    .then(
                        if (indicatorBackdrop != null) {
                            Modifier.liquidGlassPanel(
                                backdrop = indicatorBackdrop,
                                shape = CircleShape,
                                pressProgress = { 1f }
                            )
                        } else Modifier
                    )
                    .glow(Color.White.copy(alpha = 0.15f), radiusFraction = 1.5f)
                    .glass(
                        CircleShape,
                        GlassColors(
                            tintTop = Color.White.copy(alpha = 0.24f),
                            tintBottom = Color.White.copy(alpha = 0.10f),
                            highlight = Color.White.copy(alpha = 0.40f),
                            rimBright = Color.White.copy(alpha = 0.75f),
                            rimDim = Color.Black.copy(alpha = 0.12f)
                        )
                    )
            )
        }

        // 可见行
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.take(2).forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val cx = coords.positionInParent().x + coords.size.width / 2f
                            if (tabCenters[index] != cx) tabCenters[index] = cx
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    DockTabColumn(tab = tab, selected = index == safeIndex)
                }
            }

            // 中间绿色实心胶囊按钮
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 44.dp)
                    .glow(activeGreen.copy(alpha = 0.45f), radiusFraction = 1.3f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(activeGreen)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCenterClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    centerIcon,
                    contentDescription = centerContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            tabs.drop(2).forEachIndexed { offset, tab ->
                val index = offset + 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val cx = coords.positionInParent().x + coords.size.width / 2f
                            if (tabCenters[index] != cx) tabCenters[index] = cx
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    DockTabColumn(tab = tab, selected = index == safeIndex)
                }
            }
        }
    }
}

@Composable
private fun DockTabColumn(tab: DockTab, selected: Boolean) {
    val color by animateColorAsState(
        targetValue = if (selected) Color(0xFFF3F5FA)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        animationSpec = tween(220),
        label = "dockTabColor"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = color,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = color,
            maxLines = 1
        )
    }
}

// ====================================================================
// 交错入场动画
// ====================================================================

@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItem: Int = 55,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(14).toLong() * delayPerItem)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(440, easing = FastOutSlowInEasing)) { it / 5 } +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)
                ),
        exit = fadeOut(tween(120))
    ) {
        content()
    }
}

// ====================================================================
// 玻璃弹窗基座
// ====================================================================

@Composable
private fun DialogEntrance(content: @Composable () -> Unit) {
    // 弹窗运行在独立窗口，跨窗口采样主窗口折射层会坐标错位——
    // 置空 LocalAppBackdrop 让弹窗内玻璃降级为纯 painted 材质
    CompositionLocalProvider(LocalAppBackdrop provides null) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(60)
            shown = true
        }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)
            ),
            exit = fadeOut(tween(150))
        ) {
            content()
        }
    }
}

// ====================================================================
// 加载遮罩
// ====================================================================

@Composable
fun LoadingOverlay(text: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            DialogEntrance {
                GlassCard(
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = 28.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LiquidDotsLoader()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** 三颗液态加载球 */
@Composable
fun LiquidDotsLoader(dotSize: Dp = 12.dp, color: Color = Color(0xFFE9EBF4)) {
    val transition = rememberInfiniteTransition(label = "loader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "loaderT"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val phase = t * 2f * PI.toFloat() + i * (2f * PI.toFloat() / 3f)
            val wave = max(0f, sin(phase))
            val scale = 0.55f + 0.45f * wave
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.35f + 0.65f * wave
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

// ====================================================================
// 结果弹窗
// ====================================================================

@Composable
fun ResultDialog(
    success: Boolean,
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntrance {
            GlassCard(
                shape = RoundedCornerShape(28.dp),
                contentPadding = 24.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var iconShown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(80)
                        iconShown = true
                    }
                    AnimatedVisibility(
                        visible = iconShown,
                        enter = scaleIn(
                            initialScale = 0.2f,
                            animationSpec = spring(
                                dampingRatio = 0.38f,
                                stiffness = 520f
                            )
                        ) + fadeIn(tween(120))
                    ) {
                        val iconColor = if (success) AppColors.successAdaptive() else AppColors.dangerAdaptive()
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .glow(iconColor, radiusFraction = 1.6f)
                                .glass(
                                    CircleShape,
                                    rememberGlassColors(
                                        tintTop = iconColor.copy(alpha = 0.24f),
                                        tintBottom = iconColor.copy(alpha = 0.10f)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    StaggeredItem(index = 1) {
                        Text(
                            text = if (success) "操作成功" else "操作失败",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StaggeredItem(index = 2) {
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    StaggeredItem(index = 3) {
                        GlassButton(
                            text = "完成",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// 确认弹窗
// ====================================================================

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogEntrance {
            GlassCard(
                shape = RoundedCornerShape(28.dp),
                contentPadding = 24.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .glow(AppColors.warning, radiusFraction = 1.6f)
                            .glass(
                                CircleShape,
                                rememberGlassColors(
                                    tintTop = AppColors.warning.copy(alpha = 0.22f),
                                    tintBottom = AppColors.warning.copy(alpha = 0.10f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassButton(
                            text = "取消",
                            onClick = onDismiss,
                            style = GlassButtonStyle.Glass,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = confirmText,
                            onClick = onConfirm,
                            style = GlassButtonStyle.Danger,
                            shimmer = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// 液态玻璃吐司
// ====================================================================

@Composable
fun ToastMessage(message: String, onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        visible = true
        delay(2200)
        visible = false
        delay(300)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.padding(bottom = 108.dp),
            enter = fadeIn(tween(200)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
            ) { it / 2 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(240)) { it / 3 }
        ) {
            GlassCard(
                shape = RoundedCornerShape(50),
                contentPadding = 0.dp,
                tintTop = Color(0xFF3C3F52).copy(alpha = 0.88f),
                tintBottom = Color(0xFF23252F).copy(alpha = 0.82f)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ====================================================================
// 工具
// ====================================================================

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", size, units[unitIndex])
}

/** 秒数 → 可读时长（跟随界面语言） */
internal fun formatDuration(seconds: Long): String {
    if (seconds < 0) return AppLocale.t("0 秒")
    if (seconds < 60) return AppLocale.tf("{0} 秒", seconds)
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (remain == 0L) AppLocale.tf("{0} 分钟", minutes)
    else AppLocale.tf("{0} 分 {1} 秒", minutes, remain)
}

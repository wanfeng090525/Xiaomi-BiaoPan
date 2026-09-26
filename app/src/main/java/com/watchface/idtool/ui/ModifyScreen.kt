package com.watchface.idtool.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchface.idtool.ClickSound
import com.watchface.idtool.AppSettings
import com.watchface.idtool.MainViewModel
import com.watchface.idtool.RecordStore
import com.watchface.idtool.UiState
import com.watchface.idtool.WatchfaceParser

/**
 * 修改页：液态玻璃重构版
 *
 * 结构：
 *   1. 文件拖放区（大玻璃卡，加载成功后染绿 + 对勾徽章弹出）
 *   2. 文件信息卡（ID / 名称 / 大小 / 文件名）
 *   3. 新 ID 设置卡（玻璃输入框 + 实时校验指示 + 模式胶囊 + 骰子生成）
 *   4. 名称修改卡（保持 / 自定义 胶囊 + 展开输入框）
 *   5. 保存按钮（渐变玻璃主按钮 + 微光扫过）
 *   6. 快捷操作网格（复制原/新 ID、重置、历史）
 */
@Composable
fun ModifyScreen(
    viewModel: MainViewModel,
    state: UiState,
    onNavigateToHistory: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var localToast by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } catch (_: Exception) {
                null
            } ?: it.toString().substringAfterLast("/").let { name ->
                java.net.URLDecoder.decode(name, "UTF-8")
            }
            viewModel.loadFile(it, fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ============ 标题 ============
        StaggeredItem(index = 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon = Icons.Default.Tag, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "修改表盘",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "修改 ID 与名称，导出到 Download",  // 走 Text 包装器翻译
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ============ 文件选择区 ============
        StaggeredItem(index = 1) {
            FileDropCard(
                fileName = state.originalFileName,
                hasFile = state.fileInfo != null,
                onClick = {
                    if (viewModel.requireLogin()) filePicker.launch(arrayOf("*/*"))
                }
            )
        }

        // ============ 已加载：全部功能卡片（展开动画） ============
        AnimatedVisibility(
            visible = state.fileInfo != null,
            enter = fadeIn(tween(300)) + expandVertically(
                tween(360, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(240))
        ) {
            val info = state.fileInfo ?: return@AnimatedVisibility

            Column {
                Spacer(Modifier.height(14.dp))

                // ---- 文件信息卡 ----
                StaggeredItem(index = 2) {
                    SectionCard(
                        icon = Icons.Default.Description,
                        title = "文件信息",
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        InfoRow("表盘 ID", info.id, mono = true, highlight = true)
                        InfoDivider()
                        InfoRow("表盘名称", info.name.ifEmpty { "(空)" })
                        InfoDivider()
                        InfoRow("文件大小", RecordStore.formatBytes(info.size))
                        InfoDivider()
                        InfoRow("文件名", state.originalFileName, maxLines = true)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- 设置新 ID ----
                StaggeredItem(index = 3) {
                    SectionCard(
                        icon = Icons.Default.Tag,
                        title = "设置新 ID",
                        tint = MaterialTheme.colorScheme.secondary
                    ) {
                        GlassTextField(
                            value = state.newId,
                            onValueChange = { viewModel.setNewId(it) },
                            placeholder = "输入 9 或 12 位纯数字",
                            keyboardType = KeyboardType.Number,
                            mono = true,
                            maxLength = 12
                        )

                        // 实时校验指示（图标 + 文案随状态切换）
                        val error = WatchfaceParser.validateId(state.newId)
                        val valid = state.newId.isNotEmpty() && error == null
                        IdValidationHint(id = state.newId, valid = valid)

                        Spacer(Modifier.height(12.dp))

                        // 位数模式胶囊
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "random" to "随机 9/12",
                                "9" to "9 位",
                                "12" to "12 位"
                            ).forEach { (mode, label) ->
                                GlassChip(
                                    text = label,
                                    selected = state.selectedMode == mode,
                                    onClick = { viewModel.selectMode(mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 骰子生成
                        GlassButton(
                            text = "生成随机 ID",
                            icon = Icons.Default.Casino,
                            onClick = { viewModel.generateRandomId() },
                            style = GlassButtonStyle.Glass,
                            height = 42.dp,
                            shimmer = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- 修改表盘名称 ----
                StaggeredItem(index = 4) {
                    SectionCard(
                        icon = Icons.Default.Label,
                        title = "表盘名称",
                        tint = AppColors.infoAdaptive()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "keep" to "保持原名",
                                "custom" to "自定义名称"
                            ).forEach { (mode, label) ->
                                GlassChip(
                                    text = label,
                                    selected = state.nameMode == mode,
                                    onClick = { viewModel.selectNameMode(mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 自定义名称输入框（展开动画）
                        AnimatedVisibility(
                            visible = state.nameMode == "custom",
                            enter = fadeIn(tween(280)) + expandVertically(
                                tween(320, easing = FastOutSlowInEasing)
                            ),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(220))
                        ) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                GlassTextField(
                                    value = state.customName,
                                    onValueChange = { viewModel.setCustomName(it) },
                                    placeholder = "输入新的表盘名称",
                                    maxLength = 30
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "名称将写入表盘文件，留空则清除名称",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ---- 保存按钮 ----
                StaggeredItem(index = 5) {
                    GlassButton(
                        text = "保存修改",
                        icon = Icons.Default.Save,
                        onClick = { viewModel.saveFile() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---- 快捷操作 ----
                StaggeredItem(index = 6) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassToolCard(
                                icon = Icons.Default.ContentCopy,
                                title = "复制原 ID",
                                subtitle = info.id,
                                iconTint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(info.id))
                                    localToast = "原 ID 已复制"
                                }
                            )
                            GlassToolCard(
                                icon = Icons.Default.ContentCopy,
                                title = "复制新 ID",
                                subtitle = state.newId.ifEmpty { "尚未生成" },
                                iconTint = AppColors.successAdaptive(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (state.newId.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(state.newId))
                                        localToast = "新 ID 已复制"
                                    } else {
                                        localToast = "暂无新 ID，请先生成"
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassToolCard(
                                icon = Icons.Default.Refresh,
                                title = "重新选择",
                                subtitle = "清除当前状态",
                                iconTint = AppColors.warning,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.resetAll() }
                            )
                            GlassToolCard(
                                icon = Icons.Default.History,
                                title = "修改记录",
                                subtitle = "查看历史",
                                iconTint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToHistory
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }

    localToast?.let { msg ->
        ToastMessage(message = msg, onFinished = { localToast = null })
    }
}

// ====================================================================
// 文件选择大卡（加载后染绿 + 徽章弹出）
// ====================================================================

@Composable
private fun FileDropCard(
    fileName: String,
    hasFile: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    val success = AppColors.successAdaptive()
    val borderSpec by animateColorAsState(
        targetValue = if (hasFile) success else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "dropBorder"
    )
    val colors = rememberGlassColors(
        tintTop = if (hasFile) success.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        tintBottom = if (hasFile) success.copy(alpha = 0.06f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassShadow(8.dp, shape)
            .pressScale(interaction, pressedScale = 0.97f)
            .glass(shape, colors)
            .drawBehind {
                // 呼吸描边：加载后为绿色，未加载为蓝色
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        listOf(
                            borderSpec.copy(alpha = 0.75f),
                            borderSpec.copy(alpha = 0.25f)
                        )
                    ),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .pressRipple(interaction, clipShape = shape, color = if (hasFile) Color(0xFFE9EBF4) else Color(0xFFD9DEEB), intensity = 1.15f)
            .clickable(interactionSource = interaction, indication = null) {
                if (AppSettings.soundEnabled) {
                    ClickSound.play(context)
                }
                onClick()
            }
            .padding(vertical = 22.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 图标容器：加载后弹出对勾徽章
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .glow(borderSpec.copy(alpha = 0.30f), radiusFraction = 1.5f)
                        .glass(CircleShape, rememberGlassColors()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "选择文件",
                        // 跟随主题，避免深浅色/缩放后发灰发虚
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                // 加载成功徽章（弹簧弹出）
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasFile,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = scaleIn(
                        initialScale = 0.2f,
                        animationSpec = spring(dampingRatio = 0.35f, stiffness = 600f)
                    ) + fadeIn(tween(150)),
                    exit = fadeOut(tween(120))
                ) {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .glow(Color.White.copy(alpha = 0.30f), radiusFraction = 1.5f)
                            .glass(
                                CircleShape,
                                rememberGlassColors(
                                    tintTop = Color.White.copy(alpha = 0.30f),
                                    tintBottom = Color.White.copy(alpha = 0.14f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFF3F5FA),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = fileName to hasFile,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 6 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 6 })
                },
                label = "dropText"
            ) { (name, loaded) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loaded) {
                        Text(
                            text = name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = success,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "已加载 · 点击重新选择",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "点击选择表盘文件",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "支持 .bin 表盘文件",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// 分区卡片：图标 + 标题 + 玻璃内容区
// ====================================================================

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    tint: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    GlassCard(shape = RoundedCornerShape(22.dp), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = tint, size = 30.dp, iconSize = 15.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(13.dp))
        content()
    }
}

// ====================================================================
// 信息行
// ====================================================================

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    highlight: Boolean = false,
    maxLines: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) NumericFonts else null,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = if (maxLines) 1 else Int.MAX_VALUE,
            overflow = if (maxLines) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 14.dp)
        )
    }
}

@Composable
private fun InfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 0.dp)
            .glass(
                RoundedCornerShape(1.dp),
                rememberGlassColors(
                    tintTop = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    tintBottom = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                )
            )
    )
}

// ====================================================================
// 玻璃输入框
// ====================================================================

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    maxLength: Int = 100
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (focused) primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(240),
        label = "fieldBorder"
    )
    val shape = RoundedCornerShape(14.dp)
    val colors = rememberGlassColors(
        tintTop = if (focused) primary.copy(alpha = 0.10f) else null,
        tintBottom = if (focused) primary.copy(alpha = 0.04f) else null
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassShadow(4.dp, shape)
            .glass(shape, colors)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        listOf(borderColor.copy(alpha = 0.8f), borderColor.copy(alpha = 0.3f))
                    ),
                    style = Stroke(width = if (focused) 1.6.dp.toPx() else 1.1.dp.toPx())
                )
            }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (mono) NumericFonts else AppFonts,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                    // 字符计数（聚焦且有内容时显示）
                    AnimatedVisibility(visible = focused && value.isNotEmpty()) {
                        Text(
                            text = "${value.length}/$maxLength",
                            fontSize = 10.sp,
                            fontFamily = NumericFonts,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ====================================================================
// ID 校验提示（图标 + 文案随状态动画切换）
// ====================================================================

@Composable
private fun IdValidationHint(id: String, valid: Boolean) {
    AnimatedContent(
        targetState = when {
            id.isEmpty() -> null
            valid -> "✓"
            else -> "✗"
        },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.7f, animationSpec = spring(
                dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium
            ))) togetherWith fadeOut(tween(140))
        },
        label = "idHint",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) { mark ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (mark) {
                null -> {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "仅支持 9 位或 12 位纯数字",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "✓" -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFE9EBF4)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        AppLocale.tf("有效的 {0} 位 ID", id.length),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFE9EBF4)
                    )
                    Spacer(Modifier.width(5.dp))
                    val error = WatchfaceParser.validateId(id)
                    Text(
                        error ?: "ID 无效",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.warning
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (id.isNotEmpty()) {
                Text(
                    text = AppLocale.tf("{0} 位", id.length),
                    fontSize = 11.sp,
                    fontFamily = NumericFonts,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ====================================================================
// 图标徽章
// ====================================================================

@Composable
internal fun IconBadge(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp
) {
    // 图标无彩色规格：仅保留极淡柔光，容器与图标统一中性（圆形容器，避免图标后方出现方形底）
    Box(
        modifier = Modifier
            .size(size)
            .glow(Color.White.copy(alpha = 0.13f), radiusFraction = 1.4f)
            .glass(
                CircleShape,
                rememberGlassColors()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFFE9EBF4),
            modifier = Modifier.size(iconSize)
        )
    }
}

// ====================================================================
// 快捷操作玻璃小卡
// ====================================================================

@Composable
internal fun GlassToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = {
            if (AppSettings.soundEnabled) {
            }
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        contentPadding = 12.dp,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon = icon, tint = iconTint)
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

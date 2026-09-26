package com.watchface.idtool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchface.idtool.SagAuthManager
import com.watchface.idtool.SagConfig
import kotlinx.coroutines.launch

/**
 * 微验卡密登录页
 * - 登录：SagAuthManager.login(context, kami)
 * - 解绑：SagAuthManager.unbindKami(context, kami)
 */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var kami by remember { mutableStateOf(SagAuthManager.loadSavedKami(context)) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun setMsg(text: String, error: Boolean) {
        message = text
        isError = error
    }

    fun doLogin() {
        if (loading) return
        loading = true
        message = ""
        keyboard?.hide()
        scope.launch {
            val result = SagAuthManager.login(context, kami)
            loading = false
            if (result.success) {
                setMsg(
                    if (result.endTime.isNotEmpty()) "登录成功，到期：${result.endTime}"
                    else "登录成功",
                    false
                )
                onLoginSuccess()
            } else {
                setMsg(result.message, true)
            }
        }
    }

    fun doUnbind() {
        if (loading) return
        if (kami.isBlank()) {
            setMsg("请先输入要解绑的卡密", true)
            return
        }
        loading = true
        message = ""
        keyboard?.hide()
        scope.launch {
            val result = SagAuthManager.unbindKami(context, kami)
            loading = false
            setMsg(result.message, !result.success)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "卡密验证",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (SagConfig.ENABLED) "登录后可使用全部功能；换机请先解绑"
            else "验证已关闭（调试）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = kami,
            onValueChange = { kami = it; message = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("卡密") },
            placeholder = { Text("请输入卡密") },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE9EBF4).copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedLabelColor = Color(0xFFE9EBF4),
                cursorColor = Color(0xFFE9EBF4),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { doLogin() }),
            enabled = !loading
        )

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = if (isError) Color(0xFFFF8A80) else Color(0xFF7CFBA7)
            )
        }

        Spacer(Modifier.height(22.dp))

        GlassButton(
            text = if (loading) "处理中…" else "登录",
            onClick = {
                if (!loading && (kami.isNotBlank() || !SagConfig.ENABLED)) doLogin()
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        GlassButton(
            text = "取消解锁",
            onClick = { if (!loading) doUnbind() },
            modifier = Modifier.fillMaxWidth(),
            style = GlassButtonStyle.Glass,
            shimmer = false
        )

        }

}

package com.glassous.dsha.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/**
 * 访问认证弹窗：插件（`@fonlan/dsh-web-auth`）要求输入访问密码时，替代它自己那张网页登录页。
 *
 * 观感与关闭确认弹窗一致；密码框带锁形图标与显隐切换，与连接组编辑页的凭据字段同一套写法。
 * 密码默认预填该连接组已记住的那一份，一键确认即可登录。
 *
 * @param serverError 服务端回显的失败文案（重新弹窗时带回来），非空即高亮输入框并显示
 */
@Composable
internal fun WebAuthLoginDialog(
    initialPassword: String,
    serverError: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_auth_login_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.web_auth_login_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WebAuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.label_web_auth_password),
                    errorText = serverError,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_web_auth_login))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 首次使用弹窗：服务端还没设置过密码，由这里确定访问密码。
 *
 * 两栏都预填已记住的密码（该部署被重置回未设置状态时，沿用原密码最省事），
 * 校验与服务端一致：至少 [WEB_AUTH_MIN_PASSWORD_LENGTH] 位、两次输入一致。
 */
@Composable
internal fun WebAuthSetupDialog(
    initialPassword: String,
    serverError: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf(initialPassword) }
    var confirmation by remember { mutableStateOf(initialPassword) }
    // 本地校验优先于服务端回显：用户一改输入就清掉，避免旧提示一直挂着
    var localError by remember { mutableStateOf<Int?>(null) }
    val errorText = localError?.let { stringResource(it) } ?: serverError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_auth_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.web_auth_setup_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WebAuthPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    label = stringResource(R.string.label_web_auth_password),
                    errorText = errorText,
                )
                WebAuthPasswordField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        localError = null
                    },
                    label = stringResource(R.string.label_web_auth_password_confirm),
                    errorText = "",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    localError = when {
                        password.length < WEB_AUTH_MIN_PASSWORD_LENGTH ->
                            R.string.error_web_auth_password_too_short

                        password != confirmation -> R.string.error_web_auth_password_mismatch
                        else -> null
                    }
                    if (localError == null) onConfirm(password)
                },
            ) {
                Text(stringResource(R.string.action_web_auth_setup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 访问认证弹窗里的密码框：锁形图标 + 显隐切换，与编辑页的凭据字段同一套观感。 */
@Composable
private fun WebAuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = errorText.isNotEmpty(),
        supportingText = if (errorText.isEmpty()) null else {
            { Text(errorText) }
        },
        leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(
                        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = stringResource(
                        if (visible) R.string.action_hide_password else R.string.action_show_password,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

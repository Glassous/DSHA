package com.glassous.dsha.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R
import com.glassous.dsha.ui.clearFocusOnTap
import com.glassous.dsha.ui.dshaViewModel
import com.glassous.dsha.ui.settings.LinkSettingCard
import com.glassous.dsha.ui.settings.SwitchSettingCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: String?,
    onBack: () -> Unit,
) {
    val viewModel: ProfileEditViewModel = dshaViewModel(key = "profile-edit-${profileId ?: "new"}") { application ->
        ProfileEditViewModel(application, profileId)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var versionPickerVisible by remember { mutableStateOf(false) }

    Scaffold(
        // 点击顶部栏、表单间隙等非输入区域时收起键盘
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.background,
        // 底部沉浸：滚动内容一直画到屏幕底边（含小白条区域），滚动中内容从底边滑出；
        // 底部安全距离改由滚动内容末尾的 Spacer 承担（见下方 Column），滚到底时最后一项停在小白条之上。
        // 顶部状态栏由顶栏自己让出，左右挖孔区仍按窗口 insets 让出。
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (viewModel.isEditing) R.string.title_edit_profile else R.string.title_new_profile,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    // 删除不可逆，统一走确认弹窗（与列表页卡片菜单一致）
                    if (viewModel.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (!viewModel.loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // 键盘补白：内层 padding 随滚动内容一起走，补出 IME 中超出导航栏的部分
                // （导航栏那一段已由末尾 Spacer 承担），滚到底时最后一项正好停在键盘上方。
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicInfoFields(viewModel)

            CredentialsFields(viewModel)

            // 只在「打开本页时已经记住过访问密码」的连接组上出现：
            // 没用过访问认证插件的连接组，编辑页与以前完全一致
            if (viewModel.webAuthRemembered) {
                WebAuthFields(viewModel)
            }

            WebAdaptFields(
                viewModel = viewModel,
                onPickVersion = { versionPickerVisible = true },
            )

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = !viewModel.saving,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }

            Spacer(Modifier.height(16.dp))
            // 底部安全距离：作为内容末尾的留白随滚动移动——滚到底时最后一项（保存按钮）
            // 停在小白条之上，滚动过程中内容则一直延伸到屏幕底边（沉浸）
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, viewModel.name.ifBlank { viewModel.baseUrl })) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (versionPickerVisible) {
        HarnessVersionPickerDialog(
            current = viewModel.harnessVersion,
            onSelect = { version ->
                viewModel.harnessVersion = version
                versionPickerVisible = false
            },
            onDismiss = { versionPickerVisible = false },
        )
    }
}

@Composable
private fun BasicInfoFields(viewModel: ProfileEditViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.section_basic_info),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = viewModel.baseUrl,
            onValueChange = { viewModel.baseUrl = it },
            label = { Text(stringResource(R.string.label_base_url)) },
            placeholder = { Text(stringResource(R.string.hint_base_url)) },
            singleLine = true,
            isError = viewModel.baseUrlError != null,
            supportingText = viewModel.baseUrlError?.let { error -> { Text(stringResource(error)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text(stringResource(R.string.label_profile_name)) },
            placeholder = { Text(stringResource(R.string.hint_profile_name)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 账号密码：本组专属的凭据开关与字段。
 *
 * 关闭时字段一并收起，保存也不校验——DeepSeek Harness 本身没有登录模块，
 * 账号密码只是 1Panel 等反向代理在入口加 Basic 鉴权的产物，只有部分部署需要。
 */
@Composable
private fun CredentialsFields(viewModel: ProfileEditViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.section_credentials),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchSettingCard(
            title = stringResource(R.string.setting_credentials_title),
            description = stringResource(R.string.setting_credentials_desc),
            checked = viewModel.credentialsEnabled,
            onCheckedChange = { viewModel.credentialsEnabled = it },
        )

        if (!viewModel.credentialsEnabled) return@Column

        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text(stringResource(R.string.label_username)) },
            singleLine = true,
            isError = viewModel.usernameError != null,
            supportingText = viewModel.usernameError?.let { error -> { Text(stringResource(error)) } },
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text(stringResource(R.string.label_password)) },
            singleLine = true,
            isError = viewModel.passwordError != null,
            supportingText = viewModel.passwordError?.let { error -> { Text(stringResource(error)) } },
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { viewModel.passwordVisible = !viewModel.passwordVisible }) {
                    Icon(
                        painter = painterResource(
                            if (viewModel.passwordVisible) {
                                R.drawable.ic_visibility_off
                            } else {
                                R.drawable.ic_visibility
                            },
                        ),
                        contentDescription = stringResource(
                            if (viewModel.passwordVisible) R.string.action_hide_password else R.string.action_show_password,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            visualTransformation = if (viewModel.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 访问认证：已记住的访问密码与自动登录开关。
 *
 * 这段整体只在「已经记住过访问密码」时才渲染（见调用处），所以没用过访问认证插件的连接组
 * 编辑页零变化。密码可以随时改或清空——清空并保存后，下次打开该连接会重新弹窗询问。
 */
@Composable
private fun WebAuthFields(viewModel: ProfileEditViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.section_web_auth),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = viewModel.webAuthPassword,
            onValueChange = { viewModel.webAuthPassword = it },
            label = { Text(stringResource(R.string.label_web_auth_password)) },
            supportingText = { Text(stringResource(R.string.setting_web_auth_password_desc)) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.webAuthPasswordVisible = !viewModel.webAuthPasswordVisible },
                ) {
                    Icon(
                        painter = painterResource(
                            if (viewModel.webAuthPasswordVisible) {
                                R.drawable.ic_visibility_off
                            } else {
                                R.drawable.ic_visibility
                            },
                        ),
                        contentDescription = stringResource(
                            if (viewModel.webAuthPasswordVisible) {
                                R.string.action_hide_password
                            } else {
                                R.string.action_show_password
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            visualTransformation = if (viewModel.webAuthPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        SwitchSettingCard(
            title = stringResource(R.string.setting_web_auth_auto_login_title),
            description = stringResource(R.string.setting_web_auth_auto_login_desc),
            checked = viewModel.webAuthAutoLogin,
            onCheckedChange = { viewModel.webAuthAutoLogin = it },
        )
    }
}

/**
 * 网页适配：本组专属的响应式适配开关与 Harness 版本。
 *
 * 版本行只在开启适配后出现——关闭适配后不做任何组件改造，此时选哪个版本都没有意义，
 * 露出来反而像是还有一项要配。行样式与设置页一致（大标题 + 小描述 + 右侧开关/箭头），
 * 两处观感统一。
 */
@Composable
private fun WebAdaptFields(
    viewModel: ProfileEditViewModel,
    onPickVersion: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.section_web_adapt),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchSettingCard(
            title = stringResource(R.string.setting_adaptive_title),
            description = stringResource(R.string.setting_adaptive_desc),
            checked = viewModel.adaptiveCompatEnabled,
            onCheckedChange = { viewModel.adaptiveCompatEnabled = it },
        )

        if (viewModel.adaptiveCompatEnabled) {
            LinkSettingCard(
                title = stringResource(R.string.setting_harness_version_title),
                description = stringResource(
                    R.string.setting_harness_version_desc,
                    viewModel.harnessVersion.label,
                ),
                onClick = onPickVersion,
            )
        }
    }
}


package com.glassous.dsha.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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

    Scaffold(
        // 点击顶部栏、表单间隙等非输入区域时收起键盘
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.background,
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
                // Scaffold 的 innerPadding 已经让出了导航栏（小白条）高度，
                // 这里只补 IME 中超出导航栏的部分，避免键盘弹出时底部多出一段空白。
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicInfoFields(viewModel)

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

        // 账号密码由应用设置统一控制：关闭时不显示字段，保存也不校验
        if (viewModel.credentialsEnabled) {
            CredentialsFields(viewModel)
        } else {
            Text(
                text = stringResource(R.string.setting_credentials_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 账号密码字段：仅在应用设置开启凭据时显示。 */
@Composable
private fun CredentialsFields(viewModel: ProfileEditViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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


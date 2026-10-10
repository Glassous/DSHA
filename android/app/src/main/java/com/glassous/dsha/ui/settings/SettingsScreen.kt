package com.glassous.dsha.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.glassous.dsha.R
import com.glassous.dsha.data.AppSettings
import com.glassous.dsha.settingsRepository
import com.glassous.dsha.ui.dshaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = dshaViewModel { SettingsViewModel(it) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        val current = settings
        if (current == null) {
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchSettingRow(
                title = stringResource(R.string.setting_credentials_title),
                description = stringResource(R.string.setting_credentials_desc),
                checked = current.credentialsEnabled,
                onCheckedChange = viewModel::setCredentialsEnabled,
            )
            SwitchSettingRow(
                title = stringResource(R.string.setting_adaptive_title),
                description = stringResource(R.string.setting_adaptive_desc),
                checked = current.adaptiveCompatEnabled,
                onCheckedChange = viewModel::setAdaptiveCompatEnabled,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 一行开关设置：标题（+ 可选说明）+ 右侧开关；整行可点，开关自身不重复响应。
 * 首次启动弹窗里说明文案放在页面上方，此处只传标题。
 */
@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

/** 设置页与快捷设置弹窗共用的应用级设置状态。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.settingsRepository

    /** null 表示尚未加载完成：避免用默认值误判"首启未确认"。 */
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCredentialsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCredentialsEnabled(enabled) }
    }

    fun setAdaptiveCompatEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAdaptiveCompatEnabled(enabled) }
    }

    /** 快捷设置确认：保存两项开关并标记完成，之后启动不再弹出。 */
    fun completeOnboarding(credentialsEnabled: Boolean, adaptiveCompatEnabled: Boolean) {
        viewModelScope.launch {
            repository.setCredentialsEnabled(credentialsEnabled)
            repository.setAdaptiveCompatEnabled(adaptiveCompatEnabled)
            repository.setOnboardingCompleted(true)
        }
    }
}

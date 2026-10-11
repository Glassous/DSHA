package com.glassous.dsha.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    var agreementVisible by remember { mutableStateOf(false) }
    var languagePickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 语言由系统按应用保存：进页面时读一次即可。切换语言会重建 Activity（配置变化），
    // 这里以 Configuration 为 key，万一将来改为自行处理配置变化也能重新读到最新选择
    val configuration = LocalConfiguration.current
    val appLanguage = remember(configuration) { context.currentAppLanguage() }

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
            SwitchSettingCard(
                title = stringResource(R.string.setting_credentials_title),
                description = stringResource(R.string.setting_credentials_desc),
                checked = current.credentialsEnabled,
                onCheckedChange = viewModel::setCredentialsEnabled,
            )
            SwitchSettingCard(
                title = stringResource(R.string.setting_adaptive_title),
                description = stringResource(R.string.setting_adaptive_desc),
                checked = current.adaptiveCompatEnabled,
                onCheckedChange = viewModel::setAdaptiveCompatEnabled,
            )
            LinkSettingCard(
                title = stringResource(R.string.setting_language_title),
                description = stringResource(appLanguage.labelRes),
                onClick = { languagePickerVisible = true },
            )
            LinkSettingCard(
                title = stringResource(R.string.setting_agreement_title),
                description = stringResource(R.string.setting_agreement_desc),
                onClick = { agreementVisible = true },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    // 用户协议随时可查：首次同意只发生在快捷设置第一页，这里只是只读的查看入口
    if (agreementVisible) {
        UserAgreementDialog(onDismiss = { agreementVisible = false })
    }

    if (languagePickerVisible) {
        LanguagePickerDialog(
            current = appLanguage,
            onSelect = { language ->
                languagePickerVisible = false
                context.applyAppLanguage(language)
            },
            onDismiss = { languagePickerVisible = false },
        )
    }
}

/**
 * 设置页里的一项开关：卡片外壳 + 大标题 + 小描述 + 右侧开关；整行可点，开关自身不重复响应。
 */
@Composable
fun SwitchSettingCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingRowShell(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
    ) {
        SwitchSettingContent(title = title, description = description, checked = checked)
    }
}

/**
 * 同一项开关，但不带卡片外壳：快捷设置本身就是一张大卡片，内部再套卡片会显得零碎。
 * 排版（大标题 + 小描述 + 右侧开关）与设置页完全一致，两处观感统一。
 */
@Composable
fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwitchSettingContent(title = title, description = description, checked = checked)
    }
}

/** 一项开关的内容：左侧大标题 + 小描述，右侧开关。 */
@Composable
private fun RowScope.SwitchSettingContent(
    title: String,
    description: String,
    checked: Boolean,
) {
    SettingRowLabels(title = title, description = description)
    Spacer(Modifier.width(16.dp))
    Switch(
        checked = checked,
        onCheckedChange = null,
    )
}

/** 设置页里的一项跳转：卡片外壳 + 大标题 + 小描述 + 右侧箭头，用于打开查看类页面或弹窗。 */
@Composable
private fun LinkSettingCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingRowShell(modifier = modifier, onClick = onClick) {
        SettingRowLabels(title = title, description = description)
        Spacer(Modifier.width(16.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 设置行外壳：圆角卡片与整行点击样式统一在这里，避免每种设置行各写一份。 */
@Composable
private fun SettingRowShell(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 设置行左侧文字块：大标题 + 小描述，两级的字号差就是这一行主要的视觉结构。 */
@Composable
private fun RowScope.SettingRowLabels(title: String, description: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    /** 快捷设置第一页同意用户协议：记下后本次即可继续，之后启动不再要求同意。 */
    fun acceptAgreement() {
        viewModelScope.launch { repository.setAgreementAccepted(true) }
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

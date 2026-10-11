package com.glassous.dsha.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/**
 * 应用语言：跟随系统，或指定某一门内置语言。
 *
 * 选择本身走系统的按应用语言（Android 13+ 的 [LocaleManager]，见 [applyAppLanguage]/[currentAppLanguage]）；
 * 模块里另外负责一件事——记下设备语言。
 *
 * 原因：一旦设置了按应用语言，系统就不再向应用暴露设备语言，那时应用读到的 Configuration
 * 都已经变成应用语言，无法反推出设备本来的语言。所以趁"跟随系统"期间（此时应用语言就是
 * 设备语言，[detectedDeviceLanguageCode] 读得到真值）把它记下来（[recordDeviceLanguage]），
 * 之后即便用户把应用切成英文，「跟随系统」那一行仍然显示「跟随系统（简体中文）」。
 *
 * 设备语言未知时（例如用户先在系统设置里改过语言、本机还没记过）不做猜测，界面只显示「跟随系统」。
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

private const val LANGUAGE_PREFS = "dsha_language"
private const val KEY_DEVICE_LANGUAGE = "device_language_code"

/** 设备语言记录的存储：语言这一块自成一份，和连接组、应用设置分开。 */
private fun Context.languagePrefs(): SharedPreferences =
    getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)

/**
 * 设备语言（ISO 码），读不到时为 null。
 *
 * 跟随系统时直接以当前配置为准（应用语言即设备语言，且能反映设备语言的后续变化）；
 * 已经设置了按应用语言时用跟随系统期间记下的那份。
 */
fun Context.deviceLanguageCode(): String? =
    detectedDeviceLanguageCode() ?: languagePrefs().getString(KEY_DEVICE_LANGUAGE, null)

/**
 * 记下设备语言。只在跟随系统期间写入——这时应用语言就是设备语言；
 * 一旦设置了按应用语言，系统就不再暴露设备语言，只能靠这份记录。
 */
fun Context.recordDeviceLanguage() {
    val detected = detectedDeviceLanguageCode() ?: return
    languagePrefs().edit().putString(KEY_DEVICE_LANGUAGE, detected).apply()
}

/** 跟随系统时的设备语言：设置了按应用语言就返回 null（这时读到的都是应用语言）。 */
private fun Context.detectedDeviceLanguageCode(): String? {
    val overridden = getSystemService(LocaleManager::class.java)?.applicationLocales
    if (overridden != null && !overridden.isEmpty) return null
    return resources.configuration.locales
        .takeUnless { it.isEmpty }
        ?.get(0)
        ?.language
}

/** 应用当前语言：系统里没有记录（或记录的不是内置语言）时即跟随系统。 */
fun Context.currentAppLanguage(): AppLanguage {
    val stored = getSystemService(LocaleManager::class.java)?.applicationLocales
    val language = stored?.takeUnless { it.isEmpty }?.get(0)?.language ?: return AppLanguage.SYSTEM
    return AppLanguage.entries.firstOrNull {
        it != AppLanguage.SYSTEM && it.tag?.substringBefore('-') == language
    } ?: AppLanguage.SYSTEM
}

/**
 * 切换应用语言：只把选择交给系统。语言变化会作为配置变更让 Activity 重建，
 * 资源随之切换，这里不需要自己 recreate。
 */
fun Context.applyAppLanguage(language: AppLanguage) {
    val manager = getSystemService(LocaleManager::class.java) ?: return
    val tag = language.tag
    manager.applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

/**
 * 快捷设置是否需要问一次语言：设备语言既不是中文也不是英文时才问。
 * 这两种语言的设备界面已经符合预期，不必多问；设备语言未知时也不问（用户已经选过语言）。
 */
fun Context.needsLanguageChoice(): Boolean {
    val language = deviceLanguageCode() ?: return false
    return language != "zh" && language != "en"
}

/**
 * 语言在界面上的名字。「跟随系统」带上设备语言（如「跟随系统（简体中文）」），
 * 与设备语言无关地保持不变；设备语言未知时只显示「跟随系统」。
 * 内置语言名按自身语言书写，因此不参与翻译（见 translatable="false"）。
 */
@Composable
fun AppLanguage.optionName(deviceLanguageCode: String?): String = when (this) {
    AppLanguage.SYSTEM -> deviceLanguageName(deviceLanguageCode)
        ?.let { stringResource(R.string.language_system_with, it) }
        ?: stringResource(R.string.language_system)

    AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}

/** 设备语言对应界面上的哪种语言：中文设备是简体中文，其余（含英文）都是英语兜底。 */
@Composable
private fun deviceLanguageName(deviceLanguageCode: String?): String? = when {
    deviceLanguageCode == null -> null
    deviceLanguageCode == "zh" -> stringResource(R.string.language_chinese)
    else -> stringResource(R.string.language_english)
}

/**
 * 语言选项：跟随系统与各内置语言各占一行，当前生效的一项由单选标记标出。
 * 设置页的弹窗与快捷设置的语言页共用。
 */
@Composable
fun LanguageOptionList(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 跟随系统期间顺手把设备语言记下来，用户之后切成别的语言时才有设备语言可显示
    LaunchedEffect(context) { context.recordDeviceLanguage() }
    val deviceLanguage = remember(context) { context.deviceLanguageCode() }
    Column(modifier = modifier) {
        AppLanguage.entries.forEach { language ->
            LanguageOptionRow(
                label = language.optionName(deviceLanguage),
                selected = language == current,
                onSelect = { onSelect(language) },
            )
        }
    }
}

/** 语言选项的一行：选项名 + 单选标记，整行可点。 */
@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 语言选择弹窗：跟随系统 + 各内置语言，选中即生效。 */
@Composable
fun LanguagePickerDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_language)) },
        text = {
            LanguageOptionList(
                current = current,
                onSelect = onSelect,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

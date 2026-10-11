package com.glassous.dsha.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/**
 * 应用内语言。选择交给系统按应用记录（Android 13+ 的按应用语言设置），
 * [SYSTEM] 表示不记录任何语言，界面语言跟随系统——这也就是首次启动时"识别设备"的默认行为。
 *
 * 语言名按自身语言书写，因此中英文名不参与翻译（见 translatable="false"）。
 */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    CHINESE("zh-CN", R.string.language_chinese),
    ENGLISH("en", R.string.language_english),
    ;

    /** 语言码：与系统里记录的语言比较用，系统可能记成 en-US、zh-Hans-CN 这类变体。 */
    val languageCode: String? get() = tag?.substringBefore('-')
}

/**
 * 当前应用语言。系统里没有记录（或记录的不是内置语言）时按"跟随系统"处理，
 * 此时资源落到默认的简体中文或 values-en。
 */
fun Context.currentAppLanguage(): AppLanguage {
    val locales = getSystemService(LocaleManager::class.java)?.applicationLocales
    val language = locales?.takeUnless { it.isEmpty }?.get(0)?.language ?: return AppLanguage.SYSTEM
    return AppLanguage.entries.firstOrNull { it.languageCode == language } ?: AppLanguage.SYSTEM
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
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(language) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = language == current,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(language.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

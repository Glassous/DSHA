package com.glassous.dsha.ui.profile

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
import com.glassous.dsha.data.HarnessVersion

/**
 * Harness 版本选项列表：每个已适配版本一行，当前生效的一项由单选标记标出。
 *
 * 列表内容取自 [HarnessVersion.supported]，只罗列本应用真正适配过的版本——
 * 适配脚本与版本一一对应，列出未适配的版本只会让用户选到一个不生效的档位。
 */
@Composable
fun HarnessVersionOptionList(
    current: HarnessVersion,
    onSelect: (HarnessVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HarnessVersion.supported.forEach { version ->
            HarnessVersionOptionRow(
                label = version.label,
                selected = version == current,
                onSelect = { onSelect(version) },
            )
        }
    }
}

/** 版本选项的一行：版本号 + 单选标记，整行可点。 */
@Composable
private fun HarnessVersionOptionRow(
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

/** 版本选择弹窗：已适配版本的单选列表，选中即生效。 */
@Composable
fun HarnessVersionPickerDialog(
    current: HarnessVersion,
    onSelect: (HarnessVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_harness_version_title)) },
        text = {
            HarnessVersionOptionList(
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

package com.glassous.dsha.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/** 设置页查看协议时正文的最大高度：超出后正文自身滚动，按钮保持可见。 */
private val DIALOG_BODY_MAX_HEIGHT = 420.dp

/**
 * 用户协议正文：快捷设置第一页与设置页的查看弹窗共用同一份内容，
 * 两处各写一份容易改一处漏一处。
 */
@Composable
fun UserAgreementBody(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.agreement_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AgreementSection(R.string.agreement_section_service_title, R.string.agreement_section_service_body)
        AgreementSection(R.string.agreement_section_credentials_title, R.string.agreement_section_credentials_body)
        AgreementSection(R.string.agreement_section_privacy_title, R.string.agreement_section_privacy_body)
        AgreementSection(R.string.agreement_section_liability_title, R.string.agreement_section_liability_body)
        AgreementSection(R.string.agreement_section_change_title, R.string.agreement_section_change_body)
    }
}

/** 设置页里的协议查看弹窗：只读，关闭即返回。 */
@Composable
fun UserAgreementDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_user_agreement)) },
        text = { UserAgreementBody(Modifier.heightIn(max = DIALOG_BODY_MAX_HEIGHT)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/** 同意勾选行：整行可点，开关自身不重复响应；未勾选时快捷设置不能进入下一页。 */
@Composable
fun AgreementAcceptRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.agreement_accept_checkbox),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 一节协议：小标题 + 正文段落。 */
@Composable
private fun AgreementSection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

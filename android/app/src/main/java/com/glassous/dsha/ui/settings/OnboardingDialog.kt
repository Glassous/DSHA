package com.glassous.dsha.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/** 快捷设置共两页：账号密码、响应式兼容。 */
private const val ONBOARDING_PAGE_COUNT = 2

/**
 * 首次启动的快捷设置弹窗：分两页引导，每页先一行说明、再一行功能开关。
 *
 * 只有最后一页点"完成"才会保存并标记已确认；点弹窗外或返回键只是本次关闭（不保存），
 * 因此未完成确认时重新进入应用仍会弹出。
 */
@Composable
fun OnboardingDialog(
    initialCredentialsEnabled: Boolean,
    initialAdaptiveCompatEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (credentialsEnabled: Boolean, adaptiveCompatEnabled: Boolean) -> Unit,
) {
    var credentialsEnabled by remember { mutableStateOf(initialCredentialsEnabled) }
    var adaptiveCompatEnabled by remember { mutableStateOf(initialAdaptiveCompatEnabled) }
    var page by remember { mutableIntStateOf(0) }
    val onLastPage = page == ONBOARDING_PAGE_COUNT - 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    // 前进从左滑入、后退反向滑出，与翻页方向一致
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "onboarding-page",
            ) { current ->
                when (current) {
                    0 -> OnboardingPage(
                        description = stringResource(R.string.onboarding_credentials_desc),
                        switchTitle = stringResource(R.string.setting_credentials_title),
                        checked = credentialsEnabled,
                        onCheckedChange = { credentialsEnabled = it },
                    )

                    else -> OnboardingPage(
                        description = stringResource(R.string.onboarding_adaptive_desc),
                        switchTitle = stringResource(R.string.setting_adaptive_title),
                        checked = adaptiveCompatEnabled,
                        onCheckedChange = { adaptiveCompatEnabled = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onLastPage) {
                        onConfirm(credentialsEnabled, adaptiveCompatEnabled)
                    } else {
                        page++
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (onLastPage) R.string.onboarding_confirm else R.string.onboarding_next,
                    ),
                )
            }
        },
        dismissButton = {
            if (page > 0) {
                TextButton(onClick = { page-- }) {
                    Text(stringResource(R.string.onboarding_prev))
                }
            }
        },
    )
}

/** 单页内容：第一行说明，第二行功能开关。 */
@Composable
private fun OnboardingPage(
    description: String,
    switchTitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchSettingRow(
            title = switchTitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

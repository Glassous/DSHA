package com.glassous.dsha.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassous.dsha.R
import com.glassous.dsha.data.ReleaseInfo

private const val TAG = "UpdateDialog"

/** 卡片与屏幕边缘的留白，比快捷设置那张大卡片收窄一些：更新说明不需要占满整屏。 */
private val CARD_MARGIN_HORIZONTAL = 20.dp

/** 卡片内边距，以及标题、正文、按钮之间的间距。 */
private val CARD_PADDING = 24.dp
private val CARD_SECTION_SPACING = 16.dp

/** 卡片阴影：与快捷设置弹窗保持一致，让卡片从背景里浮起来。 */
private val CARD_SHADOW_ELEVATION = 8.dp

/** 长日志的正文最多占屏幕高度的比例：超出部分在卡片内滚动，按钮始终留在屏幕里。 */
private const val CHANGELOG_MAX_HEIGHT_RATIO = 0.42f

/** 两行按钮之间的纵向间距。 */
private val ACTION_ROW_SPACING = 4.dp

/**
 * 有新版本的弹窗：标题是版本号，正文是 GitHub 发布说明（Markdown），
 * 三个动作分别是取消（本次不提示）、忽略此版本（以后不再提示该版本）与下载（浏览器直链下载）。
 * 按钮分两行：第一行取消居左、下载居右，忽略此版本在第二行靠右——英文按钮文案长，
 * 三个挤一行会把按钮压到换行。
 */
@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
) {
    val context = LocalContext.current
    // 正文高度按屏幕比例设上限，长日志不把弹窗撑到顶天立地
    val changelogMaxHeight = (LocalConfiguration.current.screenHeightDp * CHANGELOG_MAX_HEIGHT_RATIO).dp

    UpdateDialogCard(
        title = stringResource(R.string.update_dialog_title, release.version),
        onDismiss = onDismiss,
        body = {
            MarkdownText(
                text = release.changelog.ifBlank { stringResource(R.string.update_no_changelog) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = changelogMaxHeight)
                    .verticalScroll(rememberScrollState()),
            )
        },
        actions = {
            // 第一行两端对齐：取消居左、下载居右；忽略此版本单独一行靠右
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = { openInBrowser(context, release.downloadUrl) }) {
                    Text(stringResource(R.string.update_action_download))
                }
            }
            Spacer(Modifier.height(ACTION_ROW_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.update_action_ignore))
                }
            }
        },
    )
}

/**
 * 只有一句结论的弹窗：手动检测查到的最新结果与失败提示都走它，
 * 与有新版本的弹窗共用同一张卡片，区别只在正文与按钮。
 */
@Composable
fun UpdateMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    UpdateDialogCard(
        title = title,
        onDismiss = onDismiss,
        body = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

/** 两款更新弹窗共用的卡片外壳：尺寸、留白、按钮区都一致，只有正文与按钮不同。 */
@Composable
private fun UpdateDialogCard(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // 关掉平台的默认宽度限制，卡片宽度由下面这段内容自己决定
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CARD_MARGIN_HORIZONTAL),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = CARD_SHADOW_ELEVATION,
        ) {
            Column(modifier = Modifier.padding(CARD_PADDING)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(CARD_SECTION_SPACING))
                body()
                Spacer(Modifier.height(CARD_SECTION_SPACING))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = actions,
                )
            }
        }
    }
}

/**
 * 交给浏览器下载：APK 直链交给系统处理，应用自身不下载也不安装，
 * 避免引入存储权限与安装 Intent 的一套流程。没有可用应用时提示一下，不留静默失败。
 */
private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "没有可处理该链接的应用：$url", e)
        Toast.makeText(context, R.string.web_no_browser, Toast.LENGTH_SHORT).show()
    }
}

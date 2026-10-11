package com.glassous.dsha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R

/** 小块之间的间距，比设置页主卡片之间（12dp）略紧：几块合起来是一个模块。 */
private val AREA_SPACING = 10.dp

/** 小块的圆角与内边距。 */
private val AREA_SHAPE = RoundedCornerShape(18.dp)
private val AREA_PADDING_HORIZONTAL = 18.dp
private val AREA_PADDING_VERTICAL = 14.dp

/** 检测按钮里的图标与进度圈直径。 */
private val CONTENT_ICON_SIZE = 16.dp

/**
 * 设置页最下方的"检测更新"模块。
 *
 * 排版比页面其它部分轻一档：一个大标题（右上角带当前版本），下面依次是几块独立的小区域——
 * 自动检测开关与检测更新（手动触发）。每块只有一行中等偏小的标题与右侧控件，
 * 没有描述文字，模块本身也不套边框。
 *
 * 检测结果不在这里展示：统一的弹窗挂在导航宿主上，自动检测与手动检测共用同一处。
 */
@Composable
fun UpdateSection(
    autoCheck: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    currentVersion: String,
    checking: Boolean,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AREA_SPACING),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.update_section_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 顺手把当前版本摆出来：检测更新的模块里，这是最常被看一眼的信息
            if (currentVersion.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_section_version, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        UpdateArea(onClick = { onAutoCheckChange(!autoCheck) }) {
            AreaTitle(stringResource(R.string.setting_update_title))
            Switch(checked = autoCheck, onCheckedChange = null)
        }
        UpdateArea {
            AreaTitle(stringResource(R.string.update_check_now_title))
            CheckNowButton(checking = checking, onCheckNow = onCheckNow)
        }
    }
}

/**
 * 模块里的一小块：圆角卡片 + 一行内容。整块可点（开关那两块与设置页其它开关行一致），
 * 传 null 表示这一块只响应内部的控件。
 */
@Composable
private fun UpdateArea(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AREA_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AREA_PADDING_HORIZONTAL, vertical = AREA_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 小块里的标题：比设置页主卡片的中等偏小一档，占满剩余宽度，把右侧控件顶到行尾。 */
@Composable
private fun RowScope.AreaTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    )
}

/** 手动检测：检测进行中换成进度圈并禁用，避免重复触发。 */
@Composable
private fun CheckNowButton(
    checking: Boolean,
    onCheckNow: () -> Unit,
) {
    Spacer(Modifier.width(12.dp))
    FilledTonalButton(
        onClick = onCheckNow,
        enabled = !checking,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(CONTENT_ICON_SIZE),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(CONTENT_ICON_SIZE),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (checking) R.string.update_checking else R.string.update_check_action,
            ),
        )
    }
}

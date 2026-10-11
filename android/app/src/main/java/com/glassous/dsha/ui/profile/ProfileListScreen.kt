package com.glassous.dsha.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.dsha.R
import com.glassous.dsha.data.Profile
import com.glassous.dsha.ui.theme.BrandGradient
import com.glassous.dsha.ui.transition.SharedElementKey
import com.glassous.dsha.ui.transition.containerTransformSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onConnect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: ProfilesViewModel = viewModel()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    // 列表已按最近使用时间倒序（上次启动的置顶），首个有过启动记录的组即"上次启动"
    val lastLaunchedId = profiles.firstOrNull { it.lastUsedAt > 0L }?.id
    // 新增连接入口（悬浮按钮与空状态的加号按钮互斥出现，共用同一个共享元素 key）：
    // 点击后按钮的圆角方块展开成"新建连接组"页的整页容器
    val newProfileContainer = Modifier.containerTransformSource(SharedElementKey.NewProfileContainer)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 顶栏固定，不随列表滚动折叠
            TopAppBar(
                title = {
                    // TopAppBar 的标题默认从 16dp 起，补 4dp 才能与下方卡片的 20dp 左边缘对齐
                    BrandLogo(
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.cd_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onNew,
                    shape = RoundedCornerShape(20.dp),
                    // 无阴影
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                    modifier = newProfileContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.cd_new_profile),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            EmptyState(
                onNew = onNew,
                newProfileModifier = newProfileContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                // 列表铺满整屏：滚动时内容从固定顶栏后方穿过，首项由内边距让开顶栏
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isLastLaunched = profile.id == lastLaunchedId,
                        onConnect = { onConnect(profile.id) },
                        onEdit = { onEdit(profile.id) },
                        onDelete = { pendingDelete = profile },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isLastLaunched: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onConnect,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        // 无阴影：层次交给底色区分
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLastLaunched) {
                    Spacer(Modifier.height(6.dp))
                    LastLaunchedBadge()
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(R.string.cd_connect),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.cd_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CardActionMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

/** 名称正下方的"上次启动"标签：与名称文字左对齐的浅色胶囊。 */
@Composable
private fun LastLaunchedBadge() {
    Text(
        text = stringResource(R.string.profile_last_launched),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyState(
    onNew: () -> Unit,
    newProfileModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 加号区域本身就是"新建连接组"按钮：不再另设一个文案按钮，入口集中到一处
        EmptyAddTile(
            onClick = onNew,
            modifier = newProfileModifier,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profile_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空状态入口的尺寸、圆角与按下缩放。 */
private val EMPTY_ADD_TILE_SIZE = 104.dp
private val EMPTY_ADD_TILE_CORNER = 36.dp
private const val EMPTY_ADD_TILE_PRESSED_SCALE = 0.94f

/**
 * 空状态中央的新增入口：整块虚线区域就是按钮，同时是容器变换（按钮展开成整页）的起点。
 * 应用全局关闭了水波纹，这里用按下轻微缩小作为反馈。
 */
@Composable
private fun EmptyAddTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) EMPTY_ADD_TILE_PRESSED_SCALE else 1f,
        label = "empty-add-tile-press",
    )
    val tileColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(EMPTY_ADD_TILE_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .drawBehind {
                val corner = CornerRadius(EMPTY_ADD_TILE_CORNER.toPx())
                // 先铺底色把入口做成一块可点的卡片，再画虚线框
                drawRoundRect(color = tileColor, cornerRadius = corner)
                drawRoundRect(
                    color = outline.copy(alpha = 0.55f),
                    cornerRadius = corner,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.cd_new_profile),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CardActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        // 无阴影、无色调抬升，靠底色与形状区分浮层
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/** 品牌标识 [R.drawable.dsh] 的视口宽高比（187 x 24）。 */
private const val BRAND_LOGO_ASPECT_RATIO = 187f / 24f

/** [R.drawable.dsh] 中图形主体的填充色（近白，原图按深色底设计）。 */
private val BrandMarkMainColor = Color(0xFFF9FAFB)

/** [R.drawable.dsh] 中 HARNESS 徽章文字的填充色（深灰）。 */
private val BrandMarkAccentColor = Color(0xFF353638)

/**
 * 顶栏品牌标识：整幅 [R.drawable.dsh]（海豚 + 文字 + 徽章）作为一体显示，高度与标题行高一致。
 * 原图按深色底配色，深色主题直接原样显示；浅色主题做两点线性重新着色——主体映射为顶栏
 * 内容色，HARNESS 文字映射为白色，避免文字与其底衬同被压深后糊成一整块纯黑长方形。
 */
@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val logoHeight = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
    Image(
        painter = painterResource(R.drawable.dsh),
        contentDescription = stringResource(R.string.app_title),
        colorFilter = if (isSystemInDarkTheme()) {
            null
        } else {
            ColorFilter.colorMatrix(
                remapColorMatrix(main = LocalContentColor.current, accent = Color.White),
            )
        },
        modifier = modifier.size(
            width = logoHeight * BRAND_LOGO_ASPECT_RATIO,
            height = logoHeight,
        ),
    )
}

/**
 * 生成逐通道线性映射矩阵：把 [BrandMarkMainColor] / [BrandMarkAccentColor] 分别映射到
 * [main] / [accent]。图形只有这两种填充色，两点的线性映射即可精确还原、不引入色偏。
 * 偏移列按 ColorMatrix 约定以 0…255 为单位，故乘回 255。
 */
private fun remapColorMatrix(main: Color, accent: Color): ColorMatrix {
    fun axis(fromMain: Float, toMain: Float, fromAccent: Float, toAccent: Float): Pair<Float, Float> {
        val scale = (toMain - toAccent) / (fromMain - fromAccent)
        return scale to (toMain - scale * fromMain)
    }

    val (rScale, rOffset) =
        axis(BrandMarkMainColor.red, main.red, BrandMarkAccentColor.red, accent.red)
    val (gScale, gOffset) =
        axis(BrandMarkMainColor.green, main.green, BrandMarkAccentColor.green, accent.green)
    val (bScale, bOffset) =
        axis(BrandMarkMainColor.blue, main.blue, BrandMarkAccentColor.blue, accent.blue)

    return ColorMatrix(
        floatArrayOf(
            rScale, 0f, 0f, 0f, rOffset * 255f,
            0f, gScale, 0f, 0f, gOffset * 255f,
            0f, 0f, bScale, 0f, bOffset * 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

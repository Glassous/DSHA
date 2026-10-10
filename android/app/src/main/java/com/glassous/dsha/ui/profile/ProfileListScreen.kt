package com.glassous.dsha.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.dsha.R
import com.glassous.dsha.data.Profile
import com.glassous.dsha.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onConnect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
) {
    val viewModel: ProfilesViewModel = viewModel()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 顶栏固定，不随列表滚动折叠
            TopAppBar(
                title = {
                    // TopAppBar 的标题默认从 16dp 起，补 4dp 才能与下方卡片的 20dp 左边缘对齐
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandLogo()
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_new_profile),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            EmptyState(
                onNew = onNew,
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
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
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
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.cd_connect),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
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

@Composable
private fun EmptyState(onNew: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .drawBehind {
                    drawRoundRect(
                        color = outline.copy(alpha = 0.55f),
                        cornerRadius = CornerRadius(36.dp.toPx()),
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
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profile_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNew) {
            Text(stringResource(R.string.action_new_profile))
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
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/** 与 [R.drawable.ic_brand_logo] 的视口宽高比保持一致（234 x 171）。 */
private const val BRAND_LOGO_ASPECT_RATIO = 234f / 171f

/** 顶栏品牌图标：无底色，直接用主题强调色绘制海豚图案，高度与标题文字相同。 */
@Composable
private fun BrandLogo() {
    val titleStyle = MaterialTheme.typography.titleLarge
    val logoHeight = with(LocalDensity.current) { titleStyle.fontSize.toDp() }
    Image(
        painter = painterResource(R.drawable.ic_brand_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(LocalContentColor.current),
        modifier = Modifier
            .height(logoHeight)
            .aspectRatio(BRAND_LOGO_ASPECT_RATIO),
    )
}

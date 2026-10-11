package com.glassous.dsha.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassous.dsha.R

/** 快捷设置的页序：语言、用户协议、账号密码、响应式兼容。 */
private const val PAGE_LANGUAGE = 0
private const val PAGE_AGREEMENT = 1
private const val PAGE_CREDENTIALS = 2
private const val PAGE_ADAPTIVE = 3

/** 完整页序；语言页是否出现由设备语言决定，见 [needsLanguageChoice]。 */
private val ONBOARDING_PAGES = listOf(PAGE_LANGUAGE, PAGE_AGREEMENT, PAGE_CREDENTIALS, PAGE_ADAPTIVE)

/** 卡片与屏幕边缘的留白：卡片借此放大到接近整屏，又不至于贴着屏幕边框。 */
private val CARD_MARGIN_HORIZONTAL = 20.dp
private val CARD_MARGIN_VERTICAL = 40.dp

/** 卡片内边距，以及标题、内容、按钮之间的间距。 */
private val CARD_PADDING = 24.dp
private val CARD_SECTION_SPACING = 16.dp

/** 卡片阴影：背景被虚化后层次变平，靠阴影把卡片垫起来。 */
private val CARD_SHADOW_ELEVATION = 8.dp

/**
 * 首次启动的快捷设置弹窗：一张接近整屏的大卡片，每页一项（标题 + 描述 + 控件），排版与设置页一致。
 * 页序为语言、用户协议、账号密码、响应式兼容，其中语言页只在设备语言既不是中文也不是英文时出现：
 * 这两种语言的设备界面已经符合预期，不必多问一句。
 *
 * 语言放在协议之前：界面语言不符合预期时，用户可以在同意条款前先换语言。
 * 协议页必须先勾选同意才能继续；同意前卡片不可关闭（点卡片外或返回键都无效），
 * 因此未同意时应用无法使用。同意之后回到原有规则：只有最后一页点"完成"才会保存并标记已确认，
 * 中途关闭只是本次关闭（不保存），下次启动仍会弹出。
 *
 * 卡片外的背景由 DshaNavHost 统一虚化，这里只管卡片本身。
 */
@Composable
fun OnboardingDialog(
    initialCredentialsEnabled: Boolean,
    initialAdaptiveCompatEnabled: Boolean,
    initialAgreementAccepted: Boolean,
    onDismiss: () -> Unit,
    onAgreementAccepted: () -> Unit,
    onConfirm: (credentialsEnabled: Boolean, adaptiveCompatEnabled: Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 语言由系统按应用保存：换语言会重建 Activity（配置变化），以 Configuration 为 key 重新读取
    val configuration = LocalConfiguration.current
    val appLanguage = remember(configuration) { context.currentAppLanguage() }
    // 本次实际要走的页序：设备语言是中文或英文时语言页不存在
    val showLanguagePage = remember(context) { context.needsLanguageChoice() }
    val pages = remember(showLanguagePage) {
        if (showLanguagePage) ONBOARDING_PAGES else ONBOARDING_PAGES - PAGE_LANGUAGE
    }
    // 状态用 rememberSaveable：翻页与勾选要能扛过换语言带来的 Activity 重建。
    // 存的是页在页序里的下标（语言页可能不出现），越界时夹回有效范围
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(pages.indices)]
    val onLastPage = pageIndex >= pages.lastIndex
    var credentialsEnabled by rememberSaveable { mutableStateOf(initialCredentialsEnabled) }
    var adaptiveCompatEnabled by rememberSaveable { mutableStateOf(initialAdaptiveCompatEnabled) }
    // 勾选只决定按钮是否可点，本身还不算同意
    var agreementChecked by rememberSaveable { mutableStateOf(initialAgreementAccepted) }
    // 同意是否已生效（本次按下或此前同意过）：未生效时弹窗不可关闭
    var agreementAccepted by rememberSaveable { mutableStateOf(initialAgreementAccepted) }

    Dialog(
        // 未同意协议前不提供任何退出方式：本次会话里没有"稍后再说"
        onDismissRequest = { if (agreementAccepted) onDismiss() },
        // 关掉平台的默认宽度限制，卡片尺寸才能由下面这段内容自己决定
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CARD_MARGIN_HORIZONTAL, vertical = CARD_MARGIN_VERTICAL),
            shape = MaterialTheme.shapes.extraLarge,
            // 卡片色与设置页的行卡片一致，避免大卡片在浅色主题里和虚化背景糊在一起
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = CARD_SHADOW_ELEVATION,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CARD_PADDING),
            ) {
                Text(
                    text = stringResource(
                        when (page) {
                            PAGE_LANGUAGE -> R.string.title_language
                            PAGE_AGREEMENT -> R.string.title_user_agreement
                            else -> R.string.onboarding_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(CARD_SECTION_SPACING))
                // 内容区固定占满卡片剩余高度：翻页时卡片不跳动，协议正文也能拿到更大空间
                Box(modifier = Modifier.weight(1f)) {
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
                            PAGE_LANGUAGE -> LanguagePage(
                                current = appLanguage,
                                onSelect = context::applyAppLanguage,
                            )

                            PAGE_AGREEMENT -> AgreementPage(
                                checked = agreementChecked,
                                onCheckedChange = { agreementChecked = it },
                            )

                            PAGE_CREDENTIALS -> OnboardingPage(
                                title = stringResource(R.string.setting_credentials_title),
                                description = stringResource(R.string.setting_credentials_desc),
                                checked = credentialsEnabled,
                                onCheckedChange = { credentialsEnabled = it },
                            )

                            else -> OnboardingPage(
                                title = stringResource(R.string.setting_adaptive_title),
                                description = stringResource(R.string.setting_adaptive_desc),
                                checked = adaptiveCompatEnabled,
                                onCheckedChange = { adaptiveCompatEnabled = it },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(CARD_SECTION_SPACING))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 第一页（语言页或协议页）没有上一页可退
                    if (pageIndex > 0) {
                        TextButton(onClick = { pageIndex-- }) {
                            Text(stringResource(R.string.onboarding_prev))
                        }
                    }
                    // 中间留一段弹性空档，把确认按钮顶到右侧
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        // 协议页只有勾选同意后才能继续，其余页面始终可点
                        enabled = page != PAGE_AGREEMENT || agreementChecked,
                        onClick = {
                            when {
                                page == PAGE_AGREEMENT -> {
                                    agreementAccepted = true
                                    onAgreementAccepted()
                                    pageIndex++
                                }

                                onLastPage -> onConfirm(credentialsEnabled, adaptiveCompatEnabled)

                                else -> pageIndex++
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    page == PAGE_AGREEMENT -> R.string.agreement_accept
                                    onLastPage -> R.string.onboarding_confirm
                                    else -> R.string.onboarding_next
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 语言页：小描述 + 语言选项列表；选中即生效（系统会重建 Activity，界面随之切换）。 */
@Composable
private fun LanguagePage(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_language_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LanguageOptionList(
            current = current,
            onSelect = onSelect,
        )
    }
}

/** 协议页：小描述 + 占满剩余高度的协议正文 + 同意勾选框（大标题由卡片标题承担）。 */
@Composable
private fun AgreementPage(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.agreement_onboarding_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 正文占据剩余高度并自身滚动：说明与勾选框位置固定，卡片再小也不会把勾选框挤没
        UserAgreementBody(modifier = Modifier.weight(1f))
        AgreementAcceptRow(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 单页内容：一项设置，排版与设置页完全一致——大标题 + 小描述 + 右侧开关。
 * 内容在卡片剩余高度里居中，避免高卡片上方空一大截。
 */
@Composable
private fun OnboardingPage(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        SwitchSettingRow(
            title = title,
            description = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

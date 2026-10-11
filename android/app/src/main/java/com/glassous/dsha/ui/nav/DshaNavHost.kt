package com.glassous.dsha.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassous.dsha.ui.dshaViewModel
import com.glassous.dsha.ui.profile.ProfileEditScreen
import com.glassous.dsha.ui.profile.ProfileListScreen
import com.glassous.dsha.ui.settings.OnboardingDialog
import com.glassous.dsha.ui.settings.SettingsScreen
import com.glassous.dsha.ui.settings.SettingsViewModel
import com.glassous.dsha.ui.transition.ContainerTransformPageEnter
import com.glassous.dsha.ui.transition.ContainerTransformPageExit
import com.glassous.dsha.ui.transition.ContainerTransformTarget
import com.glassous.dsha.ui.transition.SharedElementDestination
import com.glassous.dsha.ui.transition.SharedElementHost
import com.glassous.dsha.ui.transition.SharedElementKey
import com.glassous.dsha.ui.web.WebScreen

/** 路由定义集中在这里，避免各界面硬编码字符串。 */
object DshaRoutes {

    const val ARG_PROFILE_ID = "profileId"

    const val PROFILES = "profiles"
    const val EDIT = "edit"
    const val SETTINGS = "settings"

    /** 新建（profileId 缺省）与编辑共用一条路由。 */
    const val EDIT_PATTERN = "$EDIT?$ARG_PROFILE_ID={$ARG_PROFILE_ID}"

    const val WEB_PATTERN = "web/{$ARG_PROFILE_ID}"

    fun edit(profileId: String? = null): String =
        if (profileId.isNullOrBlank()) EDIT else "$EDIT?$ARG_PROFILE_ID=${Uri.encode(profileId)}"

    fun web(profileId: String): String = "web/${Uri.encode(profileId)}"
}

/** 覆盖式转场时长：进入与退出用同一节奏，观感统一。 */
private const val TRANSITION_DURATION_MS = 300

/** 快捷设置弹窗打开时的背景虚化半径，以及虚化进出的过渡时长。 */
private val ONBOARDING_BLUR_RADIUS = 20.dp
private const val ONBOARDING_BLUR_DURATION_MS = 250

/**
 * 背景虚化：半径为 0 时什么都不加。
 * blur 的默认边缘策略即使半径为 0 也会套一层 graphicsLayer（用于裁剪），不能无条件挂上去。
 */
private fun Modifier.backgroundBlur(radius: Dp): Modifier =
    if (radius > 0.dp) blur(radius) else this

/** 覆盖式转场：新页面从右侧滑入，盖住保持原位的旧页面。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideInFromRight(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(TRANSITION_DURATION_MS),
    )

/** 覆盖式转场：当前页向右滑出，露出下方静止的旧页面。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutToRight(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(TRANSITION_DURATION_MS),
    )

/**
 * 编辑连接组路由同时承载"新建"（无 profileId）与"编辑"两种入口，
 * 只有新建那一侧与列表页的新增连接按钮是共享元素的两端。
 *
 * 必须先限定在编辑路由上：列表页、设置页等页面本身就没有 profileId 参数，
 * 只看参数会把它们一并算成新建页，其可预测返回动画（predictivePopExitTransition）
 * 会被误设成无位移而消失。
 */
private fun NavBackStackEntry?.isNewProfile(): Boolean =
    this?.destination?.route == DshaRoutes.EDIT_PATTERN &&
        arguments?.getString(DshaRoutes.ARG_PROFILE_ID).isNullOrBlank()

@Composable
fun DshaNavHost(navController: NavHostController = rememberNavController()) {
    val settingsViewModel: SettingsViewModel = dshaViewModel { SettingsViewModel(it) }
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    // 弹窗被关掉后本次会话不再显示；未点"完成"确认时，下次启动仍会弹出
    var onboardingDismissed by remember { mutableStateOf(false) }

    // 快捷设置：首次启动（尚未完成确认）或尚未同意用户协议时出现，非 null 即为要弹出
    val onboardingSettings = settings?.takeIf {
        (!it.onboardingCompleted || !it.agreementAccepted) && !onboardingDismissed
    }
    // 弹窗打开期间把主内容整体虚化，卡片像浮在毛玻璃上；关闭后虚化再退回去
    val blurRadius by animateDpAsState(
        targetValue = if (onboardingSettings != null) ONBOARDING_BLUR_RADIUS else 0.dp,
        animationSpec = tween(ONBOARDING_BLUR_DURATION_MS),
        label = "onboarding-blur",
    )

    // 共享元素宿主：导航图外层提供作用域与绘制 overlay，页面内部的元素标注才生效
    SharedElementHost(modifier = Modifier.fillMaxSize().backgroundBlur(blurRadius)) {
        NavHost(
            navController = navController,
            startDestination = DshaRoutes.PROFILES,
            // 覆盖式转场：新页面盖在旧页面上方从右滑入，旧页面保持原位不动（被覆盖）；
            // 返回时当前页向右滑出，露出下方静止的旧页面。
            enterTransition = { slideInFromRight() },
            // 旧页面保持原位不动、让新页面盖上去：用一段与滑入同时长的 alpha 1 → 1 动画
            // 占住整个转场时间，避免旧页面提前退场导致背后露出窗口底色。
            exitTransition = { fadeOut(targetAlpha = 1f, animationSpec = tween(TRANSITION_DURATION_MS)) },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideOutToRight() },
            predictivePopEnterTransition = { EnterTransition.None },
            // 手势预测退场：新建页提交后是整页收缩回按钮，滑动预览会与最终结果相反，故不做位移
            predictivePopExitTransition = {
                if (initialState.isNewProfile()) ExitTransition.None else slideOutToRight()
            },
        ) {
            composable(DshaRoutes.PROFILES) {
                // 登记本页的进出场作用域：页内"新增连接按钮"的共享元素标注依赖它
                SharedElementDestination(this) {
                    ProfileListScreen(
                        onConnect = { profileId -> navController.navigate(DshaRoutes.web(profileId)) },
                        onEdit = { profileId -> navController.navigate(DshaRoutes.edit(profileId)) },
                        onNew = { navController.navigate(DshaRoutes.edit()) },
                        onSettings = { navController.navigate(DshaRoutes.SETTINGS) },
                    )
                }
            }

            composable(
                route = DshaRoutes.EDIT_PATTERN,
                arguments = listOf(
                    navArgument(DshaRoutes.ARG_PROFILE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
                // 新建（无 profileId）时页面本身不滑入：展开与淡入全部交给共享元素容器变换，
                // 编辑既有连接组则保持原有的右滑入。
                enterTransition = {
                    if (targetState.isNewProfile()) ContainerTransformPageEnter else slideInFromRight()
                },
                // 收起方向同理：新建页退出时用一段与共享元素同长的淡出占住转场，
                // 避免页面在整页收缩回按钮之前就被销毁、动画被截断；编辑既有连接组保持右滑出。
                popExitTransition = {
                    if (initialState.isNewProfile()) ContainerTransformPageExit else slideOutToRight()
                },
            ) { entry ->
                val profileId = entry.arguments?.getString(DshaRoutes.ARG_PROFILE_ID)
                SharedElementDestination(this) {
                    ContainerTransformTarget(
                        key = SharedElementKey.NewProfileContainer,
                        // 编辑既有连接组与该按钮无关，不参与共享元素，按普通转场进出
                        enabled = profileId.isNullOrBlank(),
                    ) {
                        ProfileEditScreen(
                            profileId = profileId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }

            composable(
                route = DshaRoutes.WEB_PATTERN,
                arguments = listOf(
                    navArgument(DshaRoutes.ARG_PROFILE_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                WebScreen(
                    profileId = entry.arguments?.getString(DshaRoutes.ARG_PROFILE_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(DshaRoutes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    // 快捷设置卡片：第一页是用户协议，同意前不可关闭，应用因此无法使用；
    // 同意后仍需点"完成"保存设置，之后启动不再出现。
    if (onboardingSettings != null) {
        OnboardingDialog(
            initialCredentialsEnabled = onboardingSettings.credentialsEnabled,
            initialAgreementAccepted = onboardingSettings.agreementAccepted,
            onDismiss = { onboardingDismissed = true },
            onAgreementAccepted = settingsViewModel::acceptAgreement,
            onConfirm = { credentialsEnabled ->
                onboardingDismissed = true
                settingsViewModel.completeOnboarding(credentialsEnabled)
            },
        )
    }
}

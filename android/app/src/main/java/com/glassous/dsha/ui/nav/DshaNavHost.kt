package com.glassous.dsha.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassous.dsha.ui.profile.ProfileEditScreen
import com.glassous.dsha.ui.profile.ProfileListScreen
import com.glassous.dsha.ui.web.WebScreen

/** 路由定义集中在这里，避免各界面硬编码字符串。 */
object DshaRoutes {

    const val ARG_PROFILE_ID = "profileId"

    const val PROFILES = "profiles"
    const val EDIT = "edit"

    /** 新建（profileId 缺省）与编辑共用一条路由。 */
    const val EDIT_PATTERN = "$EDIT?$ARG_PROFILE_ID={$ARG_PROFILE_ID}"

    const val WEB_PATTERN = "web/{$ARG_PROFILE_ID}"

    fun edit(profileId: String? = null): String =
        if (profileId.isNullOrBlank()) EDIT else "$EDIT?$ARG_PROFILE_ID=${Uri.encode(profileId)}"

    fun web(profileId: String): String = "web/${Uri.encode(profileId)}"
}

@Composable
fun DshaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = DshaRoutes.PROFILES,
    ) {
        composable(DshaRoutes.PROFILES) {
            ProfileListScreen(
                onConnect = { profileId -> navController.navigate(DshaRoutes.web(profileId)) },
                onEdit = { profileId -> navController.navigate(DshaRoutes.edit(profileId)) },
                onNew = { navController.navigate(DshaRoutes.edit()) },
            )
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
        ) { entry ->
            ProfileEditScreen(
                profileId = entry.arguments?.getString(DshaRoutes.ARG_PROFILE_ID),
                onBack = { navController.popBackStack() },
            )
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
    }
}

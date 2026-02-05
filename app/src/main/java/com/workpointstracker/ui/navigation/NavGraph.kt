package com.workpointstracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.workpointstracker.ui.about.AboutScreen
import com.workpointstracker.ui.history.HistoryScreen
import com.workpointstracker.ui.home.HomeScreen
import com.workpointstracker.ui.session.SessionDetailScreen
import com.workpointstracker.ui.wish.WishItemDetailScreen
import com.workpointstracker.ui.wish.WishScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }
        composable(Screen.Wish.route) {
            WishScreen(
                onItemClick = { itemId ->
                    navController.navigate(Screen.WishItemDetail.createRoute(itemId))
                }
            )
        }
        composable(
            route = Screen.WishItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            WishItemDetailScreen(
                itemId = itemId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            SessionDetailScreen(
                sessionId = sessionId,
                onBackClick = { navController.popBackStack() },
                onResumeSession = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Wish : Screen("wish", "Wish")
    object WishItemDetail : Screen("wish/{itemId}", "Wish Item") {
        fun createRoute(itemId: Long) = "wish/$itemId"
    }
    object SessionDetail : Screen("session/{sessionId}", "Session Detail") {
        fun createRoute(sessionId: Long) = "session/$sessionId"
    }
    object History : Screen("history", "History")
    object About : Screen("about", "About")
}

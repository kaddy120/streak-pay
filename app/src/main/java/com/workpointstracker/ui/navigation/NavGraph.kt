package com.workpointstracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.workpointstracker.ui.about.AboutScreen
import com.workpointstracker.ui.history.HistoryScreen
import com.workpointstracker.ui.home.HomeScreen
import com.workpointstracker.ui.wish.WishScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Wish.route) {
            WishScreen()
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
    object History : Screen("history", "History")
    object About : Screen("about", "About")
}

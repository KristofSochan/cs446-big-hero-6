package ca.uwaterloo.cs446.bighero6.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ca.uwaterloo.cs446.bighero6.ui.screens.HomeScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.SessionActiveScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.StationInfoScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.UserSetupScreen

sealed class Screen(val route: String) {
    object UserSetup : Screen("user_setup")
    object Home : Screen("home")
    data class StationInfo(val stationId: String) : Screen("station/{stationId}") {
        fun createRoute(stationId: String) = "station/$stationId"
    }
    data class SessionActive(val stationId: String) : Screen("session/{stationId}") {
        fun createRoute(stationId: String) = "session/$stationId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.UserSetup.route
    ) {
        composable(Screen.UserSetup.route) {
            UserSetupScreen(navController = navController)
        }
        
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        
        composable(Screen.StationInfo("").route) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId") ?: ""
            StationInfoScreen(
                stationId = stationId,
                navController = navController
            )
        }
        
        composable(Screen.SessionActive("").route) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId") ?: ""
            SessionActiveScreen(
                stationId = stationId,
                navController = navController
            )
        }
    }
}

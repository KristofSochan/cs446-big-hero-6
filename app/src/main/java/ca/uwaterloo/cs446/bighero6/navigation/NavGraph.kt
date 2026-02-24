package ca.uwaterloo.cs446.bighero6.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ca.uwaterloo.cs446.bighero6.ui.screens.MyWaitlistsScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.MyStationsScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.SessionActiveScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.StationInfoScreen
import ca.uwaterloo.cs446.bighero6.ui.screens.UserSetupScreen

sealed class Screen(val route: String) {
    object UserSetup : Screen("user_setup")
    object MyWaitlists : Screen("my_waitlists")
    object MyStations : Screen("my_stations")
    data class StationInfo(val stationId: String, val autoStart: Boolean = true) :
        Screen("station/{stationId}?autoStart={autoStart}") {
        fun createRoute(stationId: String, autoStart: Boolean = true) =
            "station/$stationId?autoStart=$autoStart"
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
        
        composable(Screen.MyWaitlists.route) {
            MyWaitlistsScreen(navController = navController)
        }

        composable(Screen.MyStations.route) {
            MyStationsScreen(navController = navController)
        }

        composable(Screen.StationInfo("", true).route) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId") ?: ""
            val autoStart = backStackEntry.arguments?.getString("autoStart")?.toBoolean() ?: true
            StationInfoScreen(
                stationId = stationId,
                navController = navController,
                autoStart = autoStart
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

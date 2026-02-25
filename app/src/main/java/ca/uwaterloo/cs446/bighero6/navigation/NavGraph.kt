package ca.uwaterloo.cs446.bighero6.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.ui.screens.*

sealed class Screen(val route: String) {
    object UserSetup : Screen("user_setup")
    object MyWaitlists : Screen("my_waitlists")
    object MyStations : Screen("my_stations")
    object SessionCreation : Screen("session_creation")
    object About : Screen("about")
    data class StationInfo(val stationId: String, val autoStart: Boolean = true) :
        Screen("station/{stationId}?autoStart={autoStart}") {
        fun createRoute(stationId: String, autoStart: Boolean = true) =
            "station/$stationId?autoStart=$autoStart"
    }
    data class SessionActive(val stationId: String) : Screen("session/{stationId}") {
        fun createRoute(stationId: String) = "session/$stationId"
    }
    data class SessionEditor(val stationId: String) : Screen("edit_session/{stationId}") {
        fun createRoute(stationId: String) = "edit_session/$stationId"
    }
    data class QueueManagement(val stationId: String) : Screen("manage_queue/{stationId}") {
        fun createRoute(stationId: String) = "manage_queue/$stationId"
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

        composable(Screen.SessionCreation.route) {
            SessionCreationScreen(navController = navController)
        }
        
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
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

        composable(Screen.SessionEditor("").route) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId") ?: ""
            SessionEditorScreen(
                stationId = stationId,
                navController = navController
            )
        }

        composable(Screen.QueueManagement("").route) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getString("stationId") ?: ""
            QueueManagementScreen(
                stationId = stationId,
                navController = navController
            )
        }
    }
}

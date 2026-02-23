package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.ui.UiState
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.viewmodel.StationViewModel

/**
 * Shows station info and handles joining waitlist / starting session
 */
@Composable
fun StationInfoScreen(
    stationId: String,
    navController: NavController,
    autoStart: Boolean = true,
    viewModel: StationViewModel = viewModel()
) {
    val context = LocalContext.current
    val stationState by viewModel.stationState.collectAsState()
    val joinState by viewModel.joinState.collectAsState()
    var hasAutoStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(stationId) {
        viewModel.loadStation(stationId)
        hasAutoStarted = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = stationState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Success -> {
                val station = state.data
                val userId = DeviceIdManager.getUserId(context)
                val isInWaitlist = station.attendees.any { it.userId == userId }
                val position = if (isInWaitlist) station.calculatePosition(userId) else 0
                val peopleInLine = station.attendees.count { it.status == "waiting" }
                val isMySessionActive = station.currentSession?.userId == userId
                val shouldAutoStart =
                    autoStart && isInWaitlist && position == 1 && !isMySessionActive && station.currentSession?.userId == null

                LaunchedEffect(isMySessionActive, autoStart) {
                    if (autoStart && isMySessionActive) {
                        navController.navigate(Screen.SessionActive("").createRoute(stationId))
                    }
                }

                LaunchedEffect(shouldAutoStart) {
                    if (shouldAutoStart && !hasAutoStarted) {
                        hasAutoStarted = true
                        viewModel.checkAndStartSession(stationId, context)
                    }
                }
                
                Text(station.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "$peopleInLine ${if (peopleInLine == 1) "person" else "people"} in line",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                when {
                    autoStart && isMySessionActive -> {
                        Text(
                            "Resuming your session...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        CircularProgressIndicator()
                    }
                    shouldAutoStart -> {
                        Text(
                            "Starting your session...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        CircularProgressIndicator()
                    }
                    isInWaitlist -> {
                        Text("You're #$position in line", modifier = Modifier.padding(bottom = 8.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Back") }
                    }
                    else -> {
                        Button(onClick = { viewModel.joinWaitlist(stationId, context) }) {
                            Text("Join Waitlist")
                        }
                    }
                }
                
                // Navigate on success
                LaunchedEffect(joinState) {
                    val currentJoinState = joinState
                    if (currentJoinState is UiState.Success) {
                        if (isInWaitlist && position == 1) {
                            navController.navigate(Screen.SessionActive("").createRoute(stationId))
                        } else {
                            navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } }
                        }
                    }
                }
                
                // Show errors
                val currentJoinState = joinState
                if (currentJoinState is UiState.Error) {
                    Text(currentJoinState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
            is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is UiState.Idle -> {}
        }
    }
}

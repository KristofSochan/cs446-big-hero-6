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
    var isLeaving by remember { mutableStateOf(false) }
    
    LaunchedEffect(stationId) {
        viewModel.loadStation(stationId)
        isLeaving = false
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
                val isInWaitlist = userId in station.attendees
                val position = if (isInWaitlist) station.calculatePosition(userId) else 0
                val peopleInLine = station.attendees.values.count { it.status == "waiting" }
                val hasActiveSession = station.currentSession != null
                val isMySessionActive = station.currentSession?.userId == userId
                val isFirstInLine = isInWaitlist && position == 1
                val isIdleStation = peopleInLine == 0 && !hasActiveSession

                LaunchedEffect(isMySessionActive, autoStart) {
                    // If user already has an active session and this was launched via NFC,
                    // go straight to the active session screen.
                    if (autoStart && isMySessionActive) {
                        navController.navigate(Screen.SessionActive("").createRoute(stationId))
                    }
                }

                LaunchedEffect(isIdleStation, autoStart) {
                    // NFC tap on an idle station: treat tap as "start now" and skip extra button.
                    if (autoStart && isIdleStation) {
                        navController.navigate(Screen.SessionActive("").createRoute(stationId))
                    }
                }

                LaunchedEffect(isFirstInLine, hasActiveSession, autoStart) {
                    // NFC tap when user is head of line and station is free: auto-start session.
                    if (autoStart && isFirstInLine && !hasActiveSession) {
                        navController.navigate(Screen.SessionActive("").createRoute(stationId))
                    }
                }

                // Top section: title + station name
                if (isIdleStation) {
                    Text(
                        "Machine Available",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        station.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        station.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val statusText = when {
                    isMySessionActive -> "You're currently using this station"
                    isIdleStation ->
                        "No one is waiting. Your session will begin immediately."
                    isFirstInLine && !hasActiveSession ->
                        "Your turn. Go to the machine and tap the NFC tag to start your session."
                    isFirstInLine && hasActiveSession ->
                        "You will be notified when the station is ready."
                    else -> null
                }

                statusText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFirstInLine && !hasActiveSession) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
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
                    isIdleStation -> {
                        // Empty queue, no active session: "Machine Available" flow
                        val durationMinutes = station.sessionDurationSeconds / 60
                        val sessionInfo = if (station.mode == "timed") {
                            "Session length: $durationMinutes minutes"
                        } else {
                            "Unlimited until someone joins the queue"
                        }

                        Text(
                            sessionInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                // App-initiated start on idle station: go straight to active session.
                                navController.navigate(Screen.SessionActive("").createRoute(stationId))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text("Start Session")
                        }

                        TextButton(
                            onClick = {
                                navController.navigate(Screen.MyWaitlists.route) {
                                    popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                                }
                            }
                        ) {
                            Text("Back to My Waitlists")
                        }
                    }
                    isInWaitlist -> {
                        Text("You're #$position in line", modifier = Modifier.padding(bottom = 8.dp))
                        
                        Button(
                            onClick = { 
                                isLeaving = true
                                viewModel.leaveWaitlist(stationId, context) 
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Leave Queue")
                        }

                        TextButton(
                            onClick = {
                                navController.navigate(Screen.MyWaitlists.route) {
                                    popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                                }
                            }
                        ) {
                            Text("Back to My Waitlists")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { 
                                isLeaving = false
                                viewModel.joinWaitlist(stationId, context) 
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("Join Waitlist")
                        }
                        TextButton(
                            onClick = {
                                navController.navigate(Screen.MyWaitlists.route) {
                                    popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                                }
                            }
                        ) {
                            Text("Back to My Waitlists")
                        }
                    }
                }
                
                // Navigate on success (join/leave waitlist only; session start is on SessionActiveScreen)
                LaunchedEffect(joinState) {
                    val currentJoinState = joinState
                    if (currentJoinState is UiState.Success) {
                        if (isLeaving) {
                            navController.navigate(Screen.MyWaitlists.route) {
                                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                            }
                        } else {
                            navController.navigate(Screen.MyWaitlists.route) {
                                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                            }
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

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
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.viewmodel.SessionViewModel
import java.util.concurrent.TimeUnit

/**
 * Active session screen. Timed mode: countdown + auto-end; manual mode: End Session only.
 */
@Composable
fun SessionActiveScreen(stationId: String, navController: NavController, viewModel: SessionViewModel = viewModel()) {
    val context = LocalContext.current
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val isExpired by viewModel.isExpired.collectAsState()
    val endSessionState by viewModel.endSessionState.collectAsState()
    var stationName by remember { mutableStateOf<String?>(null) }
    var isTimedMode by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    val repository = remember { FirestoreRepository() }

    LaunchedEffect(stationId) {
        val station = repository.getStation(stationId)
        stationName = station?.name
        isTimedMode = station?.mode == "timed"
        val userId = DeviceIdManager.getUserId(context)
        when {
            station == null -> startError = "Station not found"

            // Already in a session on this station: just show the timer.
            station.currentSession?.userId == userId -> {
                viewModel.startSessionTimer(stationId)
            }

            // Idle or head of queue: try to start session. One transaction wins; if we lose, join queue.
            station.currentSession == null && (station.attendees.isEmpty() || station.isAtPositionOne(userId)) -> {
                if (station.attendees.isEmpty()) {
                    repository.addToWaitlist(stationId, userId)
                }
                val startResult = repository.startSession(
                    stationId,
                    userId,
                    station.sessionDurationSeconds,
                    station.mode.ifEmpty { "manual" }
                )
                if (startResult.isSuccess) {
                    viewModel.startSessionTimer(stationId)
                } else {
                    ensureInQueueAndNavigateToStationInfo(
                        stationId, userId, station, repository, navController
                    )
                }
            }

            // Not eligible to start (not at front): ensure in queue and show station info.
            else -> ensureInQueueAndNavigateToStationInfo(
                stationId, userId, station, repository, navController
            )
        }
    }

    LaunchedEffect(isExpired) {
        if (isExpired) {
            kotlinx.coroutines.delay(2000)
            navController.navigate(Screen.MyWaitlists.route) {
                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
            }
        }
    }

    LaunchedEffect(endSessionState) {
        if (endSessionState is SessionViewModel.EndSessionState.Success) {
            navController.navigate(Screen.MyWaitlists.route) {
                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (startError != null) {
            Text(startError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
            TextButton(onClick = {
                navController.navigate(Screen.MyWaitlists.route) { popUpTo(Screen.MyWaitlists.route) { inclusive = false } }
            }) { Text("Back to My Waitlists") }
        } else if (isExpired) {
            Text("Session Ended", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text("Returning to My Waitlists...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Session Active", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))

            stationName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (isTimedMode) {
                val waitingForServer = timeRemaining == 0L && !isExpired
                if (waitingForServer) {
                    Text(
                        "Starting…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                    Text(
                        String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text(
                    "No time limit",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when (val state = endSessionState) {
                is SessionViewModel.EndSessionState.Error ->
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                else -> {}
            }

            Button(
                onClick = { viewModel.endSession(stationId) },
                enabled = endSessionState !is SessionViewModel.EndSessionState.Loading,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (endSessionState is SessionViewModel.EndSessionState.Loading) "Ending…" else "End Session")
            }

            TextButton(
                onClick = {
                    navController.navigate(Screen.MyWaitlists.route) {
                        popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
            Text("Back to My Waitlists")
            }
        }
    }
}

/**
 * Ensures user is in the station's waitlist (adds if not), then navigates to Station Info.
 * Used whenever we didn't start a session — either we weren't eligible or startSession failed.
 * One transaction wins; everyone else ends up in the queue.
 */
private suspend fun ensureInQueueAndNavigateToStationInfo(
    stationId: String,
    userId: String,
    station: Station,
    repository: FirestoreRepository,
    navController: NavController
) {
    if (userId !in station.attendees) {
        repository.addToWaitlist(stationId, userId)
    }
    navController.popBackStack()
    navController.navigate(Screen.StationInfo("").createRoute(stationId, autoStart = false))
}


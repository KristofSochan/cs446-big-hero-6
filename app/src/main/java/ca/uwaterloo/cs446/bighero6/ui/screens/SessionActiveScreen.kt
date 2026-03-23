package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.ui.components.NavigateToHomeButton
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
    var operatorManagesSessionsOnly by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    /** False until [LaunchedEffect] finishes deciding error vs session vs navigation. */
    var initialResolutionDone by remember(stationId) { mutableStateOf(false) }
    val repository = remember { FirestoreRepository() }

    LaunchedEffect(stationId) {
        val station = repository.getStation(stationId)
        stationName = station?.name
        isTimedMode = station?.mode == "timed"
        operatorManagesSessionsOnly = station?.operatorManagesSessionsOnly == true
        val userId = DeviceIdManager.getUserId(context)
        when {
            station == null -> {
                startError = "Station not found"
                initialResolutionDone = true
            }

            // Already in a session on this station: just show the timer.
            station.currentSession?.userId == userId -> {
                viewModel.startSessionTimer(stationId, operatorManagesSessionsOnly)
                initialResolutionDone = true
            }

            // Idle or head of queue: try to start session. One transaction wins; if we lose, join queue.
            station.currentSession == null && (station.attendees.isEmpty() || station.isAtPositionOne(userId)) -> {
                val isIdle = station.attendees.isEmpty()
                if (station.operatorManagesSessionsOnly) {
                    // Operator-managed sessions: guests cannot start sessions.
                    navController.popBackStack()
                    navController.navigate(
                        Screen.StationInfo("").createRoute(stationId, autoStart = false)
                    )
                    return@LaunchedEffect
                }
                if (isIdle && !station.autoJoinEnabled) {
                    // Auto-join is disabled: an NFC tap on an idle station should NOT
                    // immediately start a session. Show the info screen instead.
                    navController.popBackStack()
                    navController.navigate(
                        Screen.StationInfo("").createRoute(stationId, autoStart = false)
                    )
                    return@LaunchedEffect
                }

                if (isIdle) {
                    val joinResult = repository.addToWaitlist(stationId, userId)
                    if (joinResult.isFailure) {
                        startError = joinResult.exceptionOrNull()?.message
                            ?: "Could not join queue"
                        initialResolutionDone = true
                        return@LaunchedEffect
                    }
                }

                val startResult = repository.startSession(
                    stationId,
                    userId,
                    station.sessionDurationSeconds,
                    station.mode.ifEmpty { "manual" }
                )
                if (startResult.isSuccess) {
                    viewModel.startSessionTimer(stationId, operatorManagesSessionsOnly)
                    initialResolutionDone = true
                } else {
                    val msg = startResult.exceptionOrNull()?.message
                    if (msg == FirestoreRepository.SINGLE_STATION_WAITLIST_POLICY_MESSAGE) {
                        startError = msg
                        initialResolutionDone = true
                    } else {
                        // Lost race or other start failure: land in queue + station info.
                        ensureInQueueAndNavigateToStationInfo(
                            stationId = stationId,
                            userId = userId,
                            station = station,
                            repository = repository,
                            navController = navController
                        )
                    }
                }
            }

            // Not eligible to start (not at front): ensure in queue and show station info.
            else -> {
                // Important: NFC tap should NOT implicitly join the queue when a session
                // is active (or when the user isn't otherwise starting). Require explicit
                // "Join Waitlist" on StationInfoScreen.
                navController.popBackStack()
                navController.navigate(
                    Screen.StationInfo("").createRoute(stationId, autoStart = false)
                )
            }
        }
    }

    LaunchedEffect(isExpired) {
        if (isExpired) {
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
        if (!initialResolutionDone) {
            CircularProgressIndicator()
        } else if (startError != null) {
            Text(
                text = startError!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            NavigateToHomeButton(navController = navController)
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

            val configuration = LocalConfiguration.current
            val timerBoxHeight = max(
                96,
                (configuration.screenHeightDp * 0.14f).toInt()
            ).dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timerBoxHeight),
                contentAlignment = Alignment.Center
            ) {
                if (isTimedMode) {
                    val isZero = timeRemaining == 0L
                    val endingState =
                        isZero && (endSessionState is SessionViewModel.EndSessionState.Loading ||
                            endSessionState is SessionViewModel.EndSessionState.Success ||
                            isExpired)
                    val startingState =
                        isZero && !isExpired &&
                            endSessionState !is SessionViewModel.EndSessionState.Loading &&
                            endSessionState !is SessionViewModel.EndSessionState.Success
                    when {
                        endingState -> Text(
                            "Ending session…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        startingState -> Text(
                            "Starting…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            val minutes =
                                TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
                            val seconds =
                                TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                            Text(
                                String.format("%02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.displayMedium
                            )
                        }
                    }
                } else {
                    Text(
                        "No time limit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (val state = endSessionState) {
                is SessionViewModel.EndSessionState.Error ->
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                else -> {}
            }

            if (!operatorManagesSessionsOnly) {
                val isEnding =
                    endSessionState is SessionViewModel.EndSessionState.Loading ||
                        endSessionState is SessionViewModel.EndSessionState.Success ||
                        isExpired
                Button(
                    onClick = { viewModel.endSession(stationId) },
                    enabled = !isEnding,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (isEnding) "Ending…" else "End Session")
                }
            }

            NavigateToHomeButton(
                navController = navController,
                modifier = Modifier.padding(top = 16.dp)
            )
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


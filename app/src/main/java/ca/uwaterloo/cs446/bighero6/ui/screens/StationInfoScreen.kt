package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import androidx.activity.ComponentActivity
import ca.uwaterloo.cs446.bighero6.viewmodel.HomeViewModel
import java.util.concurrent.TimeUnit
import ca.uwaterloo.cs446.bighero6.ui.UiState
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.viewmodel.StationViewModel

/** Eligibility at first load — used for auto-navigate only, so we don't react to later changes. */
private data class InitialEligibility(
    val isIdle: Boolean,
    val isFirstInLineWithNoSession: Boolean,
    val isMySessionActive: Boolean,
    val autoJoinEnabled: Boolean
)

/**
 * Shows station info and handles joining waitlist / starting session
 */
@Composable
fun StationInfoScreen(
    stationId: String,
    navController: NavController,
    autoStart: Boolean = true,
    viewModel: StationViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(LocalContext.current as ComponentActivity)
) {
    val context = LocalContext.current
    val stationState by viewModel.stationState.collectAsState()
    val joinState by viewModel.joinState.collectAsState()
    val checkinRemainingByStation by homeViewModel.checkinRemainingByStation.collectAsState()
    var isLeaving by remember { mutableStateOf(false) }
    var initialEligibility by remember(stationId) { mutableStateOf<InitialEligibility?>(null) }
    var didAutoNavigate by remember(stationId) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        homeViewModel.subscribeToWaitlists(context)
    }
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
                val showIdleAutoJoinFlow =
                    autoStart &&
                        (initialEligibility?.isIdle == true) &&
                        (initialEligibility?.autoJoinEnabled == true) &&
                        isIdleStation

                // Capture eligibility only on first successful load (so we don't react to later updates).
                LaunchedEffect(stationId, stationState) {
                    if (initialEligibility != null) return@LaunchedEffect
                    val s = (stationState as? UiState.Success)?.data ?: return@LaunchedEffect
                    val uid = DeviceIdManager.getUserId(context)
                    initialEligibility = InitialEligibility(
                        isIdle = s.attendees.isEmpty() && s.currentSession == null,
                        isFirstInLineWithNoSession = s.currentSession == null &&
                            uid in s.attendees && s.calculatePosition(uid) == 1,
                        isMySessionActive = s.currentSession?.userId == uid,
                        autoJoinEnabled = s.autoJoinEnabled
                    )
                }

                // Auto-navigate only when first-tap state was eligible (not when station becomes free later).
                LaunchedEffect(stationId, initialEligibility, autoStart, didAutoNavigate) {
                    if (didAutoNavigate || initialEligibility == null || !autoStart) return@LaunchedEffect
                    val e = initialEligibility!!
                    val shouldAutoStart =
                        e.isMySessionActive ||
                            (e.isIdle && e.autoJoinEnabled) ||
                            e.isFirstInLineWithNoSession
                    if (shouldAutoStart) {
                        didAutoNavigate = true
                        navController.navigate(Screen.SessionActive("").createRoute(stationId))
                    }
                }

                // Top section: title + station name
                if (showIdleAutoJoinFlow) {
                    Text(
                        "Machine Available",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        station.name,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        station.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val statusText = when {
                    isMySessionActive -> "You're currently using this station"
                    showIdleAutoJoinFlow ->
                        "No one is waiting. Your session will begin immediately."
                    isFirstInLine && !hasActiveSession ->
                        "Your turn! Go to the machine and tap the NFC tag to start your session."
                    isFirstInLine && hasActiveSession ->
                        "You will be notified when the station is ready."
                    isIdleStation && !autoStart ->
                        "Station is available. Tap the NFC tag at the machine to start."
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
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                val checkinRemainingMs = checkinRemainingByStation[stationId]
                if (isFirstInLine && !hasActiveSession && checkinRemainingMs != null && checkinRemainingMs > 0) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(checkinRemainingMs)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(checkinRemainingMs) % 60
                    Text(
                        "Check in within ${String.format("%d:%02d", minutes, seconds)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "$peopleInLine ${if (peopleInLine == 1) "person" else "people"} in line",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                when {
                    autoStart && isMySessionActive -> {
                        Text(
                            "Resuming your session...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        CircularProgressIndicator()
                    }
                    showIdleAutoJoinFlow -> {
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
                            textAlign = TextAlign.Center,
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
                        Text(
                            "You're #$position in line",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
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
                    Text(
                        currentJoinState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is UiState.Idle -> {}
        }
    }
}

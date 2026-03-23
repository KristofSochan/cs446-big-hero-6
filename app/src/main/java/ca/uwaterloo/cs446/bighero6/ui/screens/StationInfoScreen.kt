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
import ca.uwaterloo.cs446.bighero6.ui.components.NavigateToHomeButton
import ca.uwaterloo.cs446.bighero6.ui.copy.GuestQueueCopy

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
    val waitlists by homeViewModel.waitlists.collectAsState()
    var isLeaving by remember { mutableStateOf(false) }
    var initialEligibility by remember(stationId) { mutableStateOf<InitialEligibility?>(null) }
    var didAutoNavigate by remember(stationId) { mutableStateOf(false) }
    var didNavigateToSession by remember(stationId) { mutableStateOf(false) }
    var showJoinDialog by remember(stationId) { mutableStateOf(false) }
    val joinFormValues = remember(stationId) { mutableStateMapOf<String, String>() }

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
                val hasReservationForMe = station.currentReservation?.userId == userId
                val isManualNotification = station.notificationMode == "manual"
                val showIdleAutoJoinFlow =
                    autoStart &&
                        (initialEligibility?.isIdle == true) &&
                        (initialEligibility?.autoJoinEnabled == true) &&
                        !station.operatorManagesSessionsOnly &&
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
                        autoJoinEnabled = s.autoJoinEnabled && !s.operatorManagesSessionsOnly
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

                // Reactive navigate: if the operator starts my session while I'm viewing
                // this station, jump to the active session screen immediately.
                LaunchedEffect(stationId, isMySessionActive, didNavigateToSession) {
                    if (didNavigateToSession) return@LaunchedEffect
                    if (isMySessionActive) {
                        didNavigateToSession = true
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

                val summary = waitlists.find { it.stationId == stationId }
                val statusInfo = if (isInWaitlist || isMySessionActive) {
                    GuestQueueCopy.getStatus(
                        position = station.calculatePosition(userId),
                        showPositionToGuests = station.showPositionToGuests,
                        hasReservation = hasReservationForMe,
                        hasActiveSession = hasActiveSession,
                        isInSession = isMySessionActive,
                        isManualNotification = isManualNotification,
                        operatorManagesSessionsOnly = station.operatorManagesSessionsOnly,
                        estimatedWaitTime = summary?.estimatedWaitTime ?: ""
                    )
                } else if (isIdleStation && station.autoJoinEnabled) {
                    val avail = GuestQueueCopy.stationAvailable(
                        autoJoinEnabled = station.autoJoinEnabled,
                        operatorManagesSessionsOnly = station.operatorManagesSessionsOnly,
                    )
                    if (avail != null) GuestQueueCopy.StatusInfo(avail) else null
                } else {
                    null
                }

                statusInfo?.let { info ->
                    Text(
                        info.primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (info.isPrimaryHighlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    info.secondaryText?.let { sec ->
                        Text(
                            sec,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
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
                
                // If they are in waitlist, the "getStatus" call already handles position/eta.
                // We only show the global "X people in line" if they ARE NOT in the waitlist.
                if (!isInWaitlist && station.showPositionToGuests && !isMySessionActive) {
                    Text(
                        "$peopleInLine ${if (peopleInLine == 1) "person" else "people"} in line",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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
                    isMySessionActive -> {
                        Button(
                            onClick = {
                                navController.navigate(
                                    Screen.SessionActive("").createRoute(stationId)
                                )
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("View Session")
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
                        Button(
                            onClick = { 
                                isLeaving = true
                                viewModel.leaveWaitlist(stationId, context) 
                            },
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
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
                                if (station.joinFormFields.isNotEmpty()) {
                                    station.joinFormFields.forEach { field ->
                                        if (!joinFormValues.containsKey(field.key)) {
                                            joinFormValues[field.key] = ""
                                        }
                                    }
                                    showJoinDialog = true
                                } else {
                                    viewModel.joinWaitlist(stationId, context)
                                }
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

                if (showJoinDialog) {
                    val canSubmit = station.joinFormFields.all { field ->
                        !field.required || (joinFormValues[field.key]?.isNotBlank() == true)
                    }
                    AlertDialog(
                        onDismissRequest = { showJoinDialog = false },
                        title = { Text("Join waitlist") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                station.joinFormFields.forEach { field ->
                                    OutlinedTextField(
                                        value = joinFormValues[field.key] ?: "",
                                        onValueChange = { joinFormValues[field.key] = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = {
                                            Text(
                                                if (field.required) {
                                                    "${field.label} *"
                                                } else {
                                                    field.label
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = canSubmit,
                                onClick = {
                                    val form = joinFormValues
                                        .toMap()
                                        .filterValues { it.isNotBlank() }
                                    viewModel.joinWaitlist(stationId, context, form)
                                    showJoinDialog = false
                                }
                            ) {
                                Text("Join")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showJoinDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
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
            is UiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    NavigateToHomeButton(
                        navController = navController,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            is UiState.Idle -> {}
        }
    }
}

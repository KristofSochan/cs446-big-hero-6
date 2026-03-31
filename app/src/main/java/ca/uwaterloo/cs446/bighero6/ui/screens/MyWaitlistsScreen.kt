package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.navigation.NavController
import java.util.concurrent.TimeUnit
import java.util.Locale
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.ui.components.TapListScaffold
import ca.uwaterloo.cs446.bighero6.ui.copy.GuestQueueCopy
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.viewmodel.HomeViewModel

// Dev-only controls for testing; flip to false when not needed
private const val SHOW_SIMULATE_NFC_BUTTON = true
private const val SHOW_RESET_USER_ID_BUTTON = false

/**
 * Shows all waitlists user is currently in
 */
@Composable
fun MyWaitlistsScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(LocalContext.current as ComponentActivity)
) {
    val context = LocalContext.current
    val waitlists by viewModel.waitlists.collectAsState()
    val checkinRemainingByStation by viewModel.checkinRemainingByStation.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.subscribeToWaitlists(context)
    }

    TapListScaffold(
        navController = navController,
        currentScreen = Screen.MyWaitlists
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text("My Waitlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            
            if (SHOW_SIMULATE_NFC_BUTTON) {
                val hardCodedTestStationId = "7e0fab7e-a834-4242-bd34-588224c7ec71"
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.StationInfo("").createRoute(
                                hardCodedTestStationId,
                                autoStart = true
                            )
                        )
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Test: Go to Station (Simulate NFC)")
                }
            }
            
//            if (SHOW_SIMULATE_NFC_BUTTON) {
//                val hardCodedTestStationId = "5d8ae7e1-1c9c-4bc2-9bce-f363dde21d0f"
//                Button(
//                    onClick = {
//                        navController.navigate(
//                            Screen.StationInfo("").createRoute(
//                                hardCodedTestStationId,
//                                autoStart = true
//                            )
//                        )
//                    },
//                    modifier = Modifier.padding(bottom = 8.dp)
//                ) {
//                    Text("Test: Go to Station (Simulate NFC)")
//                }
//            }

            if (SHOW_RESET_USER_ID_BUTTON) {
                Button(
                    onClick = {
                        DeviceIdManager.resetUserId(context)
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Test: Reset User ID")
                }
            }
            
            if (waitlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active waitlists")
                }
            } else {
                LazyColumn {
                    items(waitlists) { waitlist ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    if (waitlist.isInSession) {
                                        navController.navigate(
                                            Screen.SessionActive("").createRoute(waitlist.stationId)
                                        )
                                    } else {
                                        navController.navigate(
                                            Screen.StationInfo("").createRoute(
                                                waitlist.stationId,
                                                autoStart = false
                                            )
                                        )
                                    }
                                }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    waitlist.stationName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                val checkinRemainingMs =
                                    checkinRemainingByStation[waitlist.stationId]
                                
                                val statusInfo = GuestQueueCopy.getStatus(
                                    position = waitlist.position,
                                    showPositionToGuests = waitlist.showPositionToGuests,
                                    hasReservation = waitlist.hasReservation,
                                    hasActiveSession = waitlist.hasActiveSession,
                                    isInSession = waitlist.isInSession,
                                    isManualNotification = waitlist.isManualNotification,
                                    operatorManagesSessionsOnly = waitlist.operatorManagesSessionsOnly,
                                    estimatedWaitTime = waitlist.estimatedWaitTime,
                                    stationName = waitlist.stationName
                                )

                                Text(
                                    statusInfo.primaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (statusInfo.isPrimaryHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (statusInfo.isPrimaryHighlighted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                statusInfo.secondaryText?.let { sec ->
                                    Text(
                                        sec,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                if (waitlist.predictedWaitTimeSeconds != null) {
                                    val minutes = waitlist.predictedWaitTimeSeconds / 60
                                    val seconds = waitlist.predictedWaitTimeSeconds % 60
                                    val timeStr = if (minutes > 0) {
                                        String.format(Locale.getDefault(), "%d min %02d sec", minutes, seconds)
                                    } else {
                                        String.format(Locale.getDefault(), "%d sec", seconds)
                                    }
                                    Text(
                                        "Predicted wait: $timeStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                if (waitlist.hasReservation && !waitlist.hasActiveSession && 
                                    checkinRemainingMs != null && checkinRemainingMs > 0) {
                                    val minutes =
                                        TimeUnit.MILLISECONDS.toMinutes(checkinRemainingMs)
                                    val seconds =
                                        TimeUnit.MILLISECONDS.toSeconds(checkinRemainingMs) % 60
                                    Text(
                                        "Check in within ${String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

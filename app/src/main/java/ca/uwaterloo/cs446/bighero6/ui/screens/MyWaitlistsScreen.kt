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
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.ui.components.TapListScaffold
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
    var userId by remember { mutableStateOf(DeviceIdManager.getUserId(context)) }
    
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
                val HARD_CODED_TEST_STATION_ID = "91a7360c-1a1b-4948-af0d-e679ab41805b"
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.StationInfo("").createRoute(
                                HARD_CODED_TEST_STATION_ID,
                                autoStart = true
                            )
                        )
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Test: Go to Station (Simulate NFC)")
                }
            }

            if (SHOW_RESET_USER_ID_BUTTON) {
                Button(
                    onClick = {
                        userId = DeviceIdManager.resetUserId(context)
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

                                when {
                                    waitlist.isInSession -> {
                                        Text("You're currently using ${waitlist.stationName}")
                                    }

                                    waitlist.position == 1 && !waitlist.hasActiveSession -> {
                                        Text("Your turn", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Go to the station to start your session.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                        val checkinRemainingMs =
                                            checkinRemainingByStation[waitlist.stationId]
                                        if (checkinRemainingMs != null && checkinRemainingMs > 0) {
                                            val minutes =
                                                TimeUnit.MILLISECONDS.toMinutes(checkinRemainingMs)
                                            val seconds =
                                                TimeUnit.MILLISECONDS.toSeconds(checkinRemainingMs) % 60
                                            Text(
                                                "Check in within ${String.format("%d:%02d", minutes, seconds)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    waitlist.position == 1 && waitlist.hasActiveSession -> {
                                        Text("You're next in line")
                                        Text(
                                            "You'll be notified when the machine is ready.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }

                                    waitlist.position > 1 -> {
                                        Text("Position: ${waitlist.position}")
                                        if (waitlist.estimatedWaitTime.isNotEmpty()) {
                                            Text("Estimated wait: ${waitlist.estimatedWaitTime}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

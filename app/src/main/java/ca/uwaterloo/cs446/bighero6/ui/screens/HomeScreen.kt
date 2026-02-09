package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.viewmodel.HomeViewModel

/**
 * Shows all waitlists user is currently in
 */
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val waitlists by viewModel.waitlists.collectAsState()
    var userId by remember { mutableStateOf(DeviceIdManager.getUserId(context)) }
    
    LaunchedEffect(Unit) {
        viewModel.subscribeToWaitlists(context)
    }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Waitlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
        Text("User ID: $userId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        
        // Test button to simulate NFC scan
        Button(
            onClick = {
                navController.navigate(Screen.StationInfo("").createRoute("8dK92iAsn0ALwZ1R9iT7"))
            },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Test: Go to Station (Simulate NFC)")
        }
        
        // Test button to reset user ID (for testing with multiple emulators)
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
        
        if (waitlists.isEmpty()) {
            Text("No active waitlists")
        } else {
            LazyColumn {
                items(waitlists) { waitlist ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(waitlist.stationName, style = MaterialTheme.typography.titleLarge)
                            Text("Position: ${waitlist.position}")
                            Text("ETA: ${waitlist.estimatedWaitTime}")
                            if (waitlist.position == 1) {
                                Text(
                                    "Tap the NFC tag to check in and start your session",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

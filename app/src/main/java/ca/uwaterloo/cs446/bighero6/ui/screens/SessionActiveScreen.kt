package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.viewmodel.SessionViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Shows countdown timer for active session
 */
@Composable
fun SessionActiveScreen(stationId: String, navController: NavController, viewModel: SessionViewModel = viewModel()) {
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val isExpired by viewModel.isExpired.collectAsState()
    var stationName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(stationId) {
        viewModel.startSessionTimer(stationId)
        // Fetch station name
        scope.launch {
            val repository = FirestoreRepository()
            val station = repository.getStation(stationId)
            stationName = station?.name
        }
    }
    
    // Navigate home when session expires
    LaunchedEffect(isExpired) {
        if (isExpired) {
            kotlinx.coroutines.delay(2000) // Show "Session Ended" message for 2 seconds
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }
    
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isExpired) {
            Text("Session Ended", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text("Returning to home...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
            Text(String.format("%02d:%02d", minutes, seconds), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(16.dp))
            
            Button(onClick = { navController.navigate(Screen.Home.route) }) {
                Text("Back to Home")
            }
        }
    }
}

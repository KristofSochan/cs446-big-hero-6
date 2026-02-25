package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.components.TapListScaffold
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import kotlinx.coroutines.launch

@Composable
fun MyStationsScreen(navController: NavController) {
    val context = LocalContext.current
    val userId = remember { DeviceIdManager.getUserId(context) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    val repository = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    // Sort stations based on criteria:
    // 1. Active ones first
    // 2. Number of people in line (greatest to least)
    // 3. Creation time (newest to oldest)
    val sortedStations = remember(stations) {
        stations.sortedWith(
            compareByDescending<Station> { it.isActive }
                .thenByDescending { it.attendees.size }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )
    }

    @Composable
    fun CreateButton(modifier: Modifier = Modifier) {
        Button(
            onClick = {
                navController.navigate(Screen.SessionCreation.route)
            },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00796B) // A Teal/Blue-Green color
            )
        ) {
            Text("+ Create Station")
        }
    }

    LaunchedEffect(userId) {
        val listener = repository.subscribeToOwnedStations(userId) { updatedStations ->
            stations = updatedStations
        }
    }

    TapListScaffold(
        navController = navController,
        currentScreen = Screen.MyStations
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = if (stations.isEmpty()) "My Stations" else "My Stations (${stations.size})",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (stations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No stations found", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Create a station to start managing your queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CreateButton(modifier = Modifier.padding(top = 24.dp))
                    }
                }
            } else {
                CreateButton(modifier = Modifier.padding(bottom = 16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedStations) { station ->
                        var expanded by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .alpha(if (station.isActive) 1f else 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Status Badge
                                        val statusColor = if (station.isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                        Surface(
                                            color = statusColor,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = if (station.isActive) "ACTIVE" else "INACTIVE",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Box {
                                        IconButton(onClick = { expanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Station options"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Write to NFC tag",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                },
                                                onClick = { expanded = false }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("View queue") },
                                                onClick = { 
                                                    expanded = false
                                                    navController.navigate(
                                                        Screen.QueueManagement(station.id).createRoute(station.id)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Edit station") },
                                                onClick = {
                                                    expanded = false
                                                    navController.navigate(
                                                        Screen.SessionEditor(station.id).createRoute(station.id)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("View analytics") },
                                                onClick = { expanded = false }
                                            )
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        "Delete station", 
                                                        color = MaterialTheme.colorScheme.error
                                                    ) 
                                                },
                                                onClick = {
                                                    expanded = false
                                                    scope.launch {
                                                        repository.deleteStation(station.id)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val waitingCount = station.attendees.count { it.status == "waiting" }
                                Text(
                                    text = "$waitingCount ${if (waitingCount == 1) "person" else "people"} in queue",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                if (station.currentSession != null) {
                                    Text(
                                        text = "Session in progress",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
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

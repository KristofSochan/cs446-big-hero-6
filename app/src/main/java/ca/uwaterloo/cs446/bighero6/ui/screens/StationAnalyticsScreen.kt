package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.StationHistory
import ca.uwaterloo.cs446.bighero6.data.StationHistoryEvent
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.components.NavigateToHomeButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationAnalyticsScreen(
    stationId: String,
    navController: NavController
) {
    val repository = remember { FirestoreRepository() }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var dailyHistory by remember { mutableStateOf<StationHistory?>(null) }
    var stationHistory by remember { mutableStateOf<StationHistory?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(stationId, refreshKey) {
        isLoading = true
        error = null

        val dailyResult = repository.getStationAnalyticsDaily(stationId)
        val allHistoryResult = repository.getStationAnalytics(stationId)
        
        if (dailyResult.isSuccess) {
            dailyHistory = dailyResult.getOrThrow()
        }

        if (allHistoryResult.isSuccess) {
            stationHistory = allHistoryResult.getOrThrow()
        }

        isLoading = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Station Analytics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshKey++ },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh analytics"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    NavigateToHomeButton(
                        navController = navController,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // daily stats
                item {
                    AnalyticsStatsCard(
                        title = "Daily Stats",
                        stationHistory = dailyHistory
                    )
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }

                // All-time / Predicted Stats
                item {
                    AnalyticsStatsCard(
                        title = "All-time Stats",
                        stationHistory = stationHistory
                    )
                }
                
                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun AnalyticsStatsCard(
    title: String,
    stationHistory: StationHistory?
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val history = stationHistory?.history ?: emptyList()
            val joins = history.count { it.type == StationHistoryEvent.TYPE_JOIN }
            val leaves = history.count { it.type == StationHistoryEvent.TYPE_LEAVE }
            
            Text(
                text = "Total joins: $joins",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(8.dp))

            val predictedSeconds = stationHistory?.getPredictedSecondsPerPosition()
            Text(
                text = "Expected time per position: ${
                    if (predictedSeconds != null) formatSeconds(predictedSeconds.toLong()) else "N/A"
                }",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(8.dp))

            val leaveRate = if (joins > 0) {
                (leaves.toDouble() / joins.toDouble() * 100).roundToInt()
            } else 0

            Text(
                text = "Early leave rate: $leaveRate%",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "($leaves leaves out of $joins total joins)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSeconds(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) {
        "%d min %02d sec".format(m, s)
    } else {
        "%d sec".format(s)
    }
}

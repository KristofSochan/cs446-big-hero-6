package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.repository.StationAnalyticsDaily
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationAnalyticsScreen(
    stationId: String,
    navController: NavController
) {
    val repository = remember { FirestoreRepository() }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var daily by remember { mutableStateOf<List<StationAnalyticsDaily>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(stationId, refreshKey) {
        isLoading = true
        error = null
        val result = repository.getStationAnalyticsDaily(stationId, days = 30)
        if (result.isSuccess) {
            daily = result.getOrNull().orEmpty()
        } else {
            error = result.exceptionOrNull()?.message ?: "Failed to load analytics"
        }
        isLoading = false
    }

    val totals = remember(daily) {
        val sessions = daily.sumOf { it.totalSessions }
        val wait = daily.sumOf { it.totalWaitTimeSeconds }
        val noShows = daily.sumOf { it.totalNoShows }
        Triple(sessions, wait, noShows)
    }
    val totalSessions = totals.first
    val totalWaitSeconds = totals.second
    val totalNoShows = totals.third
    val avgWaitSeconds = if (totalSessions > 0) totalWaitSeconds / totalSessions else 0L
    val noShowRate = if (totalSessions + totalNoShows > 0) {
        (100.0 * totalNoShows.toDouble() / (totalSessions + totalNoShows).toDouble())
            .roundToLong()
    } else {
        0L
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Analytics") },
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
                        onClick = {
                            refreshKey++
                        },
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
            ) { Text(error!!, color = MaterialTheme.colorScheme.error) }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Last 30 days", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Total sessions: $totalSessions")
                        Text("Avg wait: ${formatSeconds(avgWaitSeconds)}")
                        Text("No-shows: $totalNoShows ($noShowRate%)")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Daily", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                if (daily.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No analytics yet")
                    }
                } else {
                    LazyColumn {
                        items(daily) { day ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(day.dayKey, fontWeight = FontWeight.SemiBold)
                                    Text("Sessions: ${day.totalSessions}")
                                    val avg = if (day.totalSessions > 0) {
                                        day.totalWaitTimeSeconds / day.totalSessions
                                    } else 0L
                                    Text("Avg wait: ${formatSeconds(avg)}")
                                    Text("No-shows: ${day.totalNoShows}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSeconds(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}


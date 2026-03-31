package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.StationHistory
import ca.uwaterloo.cs446.bighero6.data.StationHistoryEvent
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.components.NavigateToHomeButton
import java.text.SimpleDateFormat
import java.util.*
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
        } else {
            error = dailyResult.exceptionOrNull()?.message ?: "Failed to load daily stats"
        }

        if (allHistoryResult.isSuccess) {
            stationHistory = allHistoryResult.getOrThrow()
        } else if (error == null) {
            error = allHistoryResult.exceptionOrNull()?.message ?: "Failed to load all-time stats"
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
                        stationHistory = dailyHistory,
                        isDaily = true
                    )
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }

                // All-time / Predicted Stats
                item {
                    AnalyticsStatsCard(
                        title = "All-time Stats",
                        stationHistory = stationHistory,
                        isDaily = false
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
    stationHistory: StationHistory?,
    isDaily: Boolean
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
                text = "Expected wait time per position: ${
                    if (predictedSeconds != null) formatSeconds(predictedSeconds.toLong()) else "N/A"
                }",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(8.dp))

            val avgSessionSeconds = stationHistory?.getAverageSessionTimeSeconds()
            Text(
                text = "Average session time: ${
                    if (avgSessionSeconds != null) formatSeconds(avgSessionSeconds.toLong()) else "N/A"
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

            Spacer(Modifier.height(24.dp))
            
            if (isDaily) {
                HourlyHistogram(history)
            } else {
                DailyHistogram(history)
            }
        }
    }
}

@Composable
fun HourlyHistogram(history: List<StationHistoryEvent>) {
    Text(
        "Joins per Hour",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    
    val hourlyCounts = IntArray(24) { 0 }
    val calendar = Calendar.getInstance()
    
    history.filter { it.type == StationHistoryEvent.TYPE_JOIN }.forEach { event ->
        event.time?.let {
            calendar.time = it.toDate()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..23) hourlyCounts[hour]++
        }
    }
    
    val maxCount = hourlyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            hourlyCounts.forEachIndexed { index, count ->
                var showTooltip by remember { mutableStateOf(false) }
                val barHeightFraction = (count.toFloat() / maxCount.toFloat()).coerceAtLeast(0.02f)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(count) {
                            detectTapGestures(
                                onPress = {
                                    showTooltip = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        showTooltip = false
                                    }
                                }
                            )
                        }
                ) {
                    // Tooltip Area
                    Box(
                        modifier = Modifier.height(30.dp).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (showTooltip) {
                            Surface(
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Chart Area
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(barHeightFraction)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    // Label
                    if (index % 4 == 0) {
                        Text(
                            text = index.toString(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(16.dp)
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    Text(
        "Hour of Day (0-23)",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun DailyHistogram(history: List<StationHistoryEvent>) {
    Text(
        "Joins per Day (Last 7 Days)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val dayCounts = mutableMapOf<String, Int>()
    
    // Initialize last 7 days with 0
    val last7DaysLabels = mutableListOf<String>()
    for (i in 6 downTo 0) {
        val tempCal = Calendar.getInstance()
        tempCal.add(Calendar.DAY_OF_YEAR, -i)
        val label = dateFormat.format(tempCal.time)
        last7DaysLabels.add(label)
        dayCounts[label] = 0
    }
    
    history.filter { it.type == StationHistoryEvent.TYPE_JOIN }.forEach { event ->
        event.time?.let {
            val label = dateFormat.format(it.toDate())
            if (dayCounts.containsKey(label)) {
                dayCounts[label] = dayCounts[label]!! + 1
            }
        }
    }
    
    val maxCount = dayCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            last7DaysLabels.forEach { label ->
                var showTooltip by remember { mutableStateOf(false) }
                val count = dayCounts[label] ?: 0
                val barHeightFraction = (count.toFloat() / maxCount.toFloat()).coerceAtLeast(0.02f)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(count) {
                            detectTapGestures(
                                onPress = {
                                    showTooltip = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        showTooltip = false
                                    }
                                }
                            )
                        }
                ) {
                    // Tooltip Area
                    Box(
                        modifier = Modifier.height(30.dp).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (showTooltip) {
                            Surface(
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Chart Area
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(barHeightFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label.split(" ")[1], // Just the day number
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
        }
    }
    Text(
        "Day of Month",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

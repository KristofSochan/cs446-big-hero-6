package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import kotlinx.coroutines.launch

private enum class EditorStationMode { Manual, Timed }
private enum class EditorStationState { Active, Inactive }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(
    stationId: String,
    navController: NavController
) {
    var stationName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(EditorStationMode.Manual) }
    var state by remember { mutableStateOf(EditorStationState.Active) }
    var durationMinutes by remember { mutableStateOf("15") }
    var enforceCheckinLimit by remember { mutableStateOf(false) }
    var checkinWindowMinutes by remember { mutableStateOf("15") }

    var isLoading by remember { mutableStateOf(true) }
    val repository = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(stationId) {
        val station = repository.getStation(stationId)
        if (station != null) {
            stationName = station.name
            mode = if (station.mode == "timed") EditorStationMode.Timed else EditorStationMode.Manual
            state = if (station.isActive) EditorStationState.Active else EditorStationState.Inactive
            durationMinutes = (station.sessionDurationSeconds / 60).toString()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Station") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Station Name", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = stationName,
                        onValueChange = { stationName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Station Name") }
                    )

                    Text("Station Mode", style = MaterialTheme.typography.titleMedium)

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == EditorStationMode.Manual,
                            onClick = { mode = EditorStationMode.Manual },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                if (mode == EditorStationMode.Manual) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            label = { Text("Manual") }
                        )
                        SegmentedButton(
                            selected = mode == EditorStationMode.Timed,
                            onClick = { mode = EditorStationMode.Timed },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                if (mode == EditorStationMode.Timed) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            label = { Text("Timed") }
                        )
                    }

                    if (mode == EditorStationMode.Timed) {
                        Text("Duration (minutes)", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("15") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enforce check-in time limit", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Require guests to check in within a set\ntime when it is their turn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enforceCheckinLimit,
                            onCheckedChange = { enforceCheckinLimit = it }
                        )
                    }

                    if (enforceCheckinLimit) {
                        Text("Check-in window (minutes)", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = checkinWindowMinutes,
                            onValueChange = { checkinWindowMinutes = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("15") }
                        )
                    }

                    Text("Current State", style = MaterialTheme.typography.titleMedium)

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state == EditorStationState.Active,
                            onClick = { state = EditorStationState.Active },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                if (state == EditorStationState.Active) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            label = { Text("Active") }
                        )
                        SegmentedButton(
                            selected = state == EditorStationState.Inactive,
                            onClick = { state = EditorStationState.Inactive },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                if (state == EditorStationState.Inactive) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            label = { Text("Inactive") }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val currentStation = repository.getStation(stationId)
                            if (currentStation != null) {
                                val updatedStation = currentStation.copy(
                                    name = stationName,
                                    mode = mode.toString().lowercase().replace("editor", ""),
                                    isActive = state == EditorStationState.Active,
                                    sessionDurationSeconds = (durationMinutes.toIntOrNull() ?: 0) * 60
                                )
                                repository.setStation(updatedStation)
                            }
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = stationName.isNotBlank() &&
                            (mode != EditorStationMode.Timed || durationMinutes.toIntOrNull() != null)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes")
                }
            }
        }
    }
}

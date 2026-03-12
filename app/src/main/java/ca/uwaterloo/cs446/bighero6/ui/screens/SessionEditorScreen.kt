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
    var durationSeconds by remember { mutableStateOf("00") }
    var autoJoinEnabled by remember { mutableStateOf(true) }
    var enforceCheckinLimit by remember { mutableStateOf(false) }
    var checkinMinutes by remember { mutableStateOf("1") }
    var checkinSeconds by remember { mutableStateOf("00") }

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
            durationSeconds = (station.sessionDurationSeconds % 60).toString().padStart(2, '0')
            autoJoinEnabled = station.autoJoinEnabled
            enforceCheckinLimit = station.enforceCheckinLimit
            checkinMinutes = (station.checkinWindowSeconds / 60).toString()
            checkinSeconds = (station.checkinWindowSeconds % 60).toString().padStart(2, '0')
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
                        Text("Session length", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { durationMinutes = it.filter(Char::isDigit) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Min") },
                                placeholder = { Text("15") }
                            )
                            OutlinedTextField(
                                value = durationSeconds,
                                onValueChange = {
                                    val digits = it.filter(Char::isDigit).take(2)
                                    val sec = digits.toIntOrNull()
                                    durationSeconds = when {
                                        digits.isEmpty() -> ""
                                        sec == null -> ""
                                        sec > 59 -> "59"
                                        else -> digits.padStart(2, '0')
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Sec") },
                                placeholder = { Text("00") }
                            )
                        }
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
                        Text("Check-in window", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = checkinMinutes,
                                onValueChange = { checkinMinutes = it.filter(Char::isDigit) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Min") },
                                placeholder = { Text("1") }
                            )
                            OutlinedTextField(
                                value = checkinSeconds,
                                onValueChange = {
                                    val digits = it.filter(Char::isDigit).take(2)
                                    val sec = digits.toIntOrNull()
                                    checkinSeconds = when {
                                        digits.isEmpty() -> ""
                                        sec == null -> ""
                                        sec > 59 -> "59"
                                        else -> digits.padStart(2, '0')
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Sec") },
                                placeholder = { Text("00") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-start on NFC (idle only)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Allow guests to start immediately when the station is\n" +
                                    "idle (no active session and an empty queue).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoJoinEnabled,
                            onCheckedChange = { autoJoinEnabled = it }
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
                                val durationTotalSeconds =
                                    (durationMinutes.toIntOrNull() ?: 0) * 60 +
                                        (durationSeconds.toIntOrNull() ?: 0)
                                val checkinTotalSeconds =
                                    (checkinMinutes.toIntOrNull() ?: 0) * 60 +
                                        (checkinSeconds.toIntOrNull() ?: 0)
                                val updatedStation = currentStation.copy(
                                    name = stationName,
                                    mode = mode.toString().lowercase().replace("editor", ""),
                                    isActive = state == EditorStationState.Active,
                                    sessionDurationSeconds = durationTotalSeconds,
                                    autoJoinEnabled = autoJoinEnabled,
                                    enforceCheckinLimit = enforceCheckinLimit,
                                    checkinWindowSeconds = checkinTotalSeconds.coerceAtLeast(1)
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
                            (mode != EditorStationMode.Timed || run {
                                val total =
                                    (durationMinutes.toIntOrNull() ?: 0) * 60 +
                                        (durationSeconds.toIntOrNull() ?: 0)
                                total > 0
                            })
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes")
                }
            }
        }
    }
}

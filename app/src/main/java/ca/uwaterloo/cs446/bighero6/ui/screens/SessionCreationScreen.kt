package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.JoinFormField
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import kotlinx.coroutines.launch

private enum class StationMode { Manual, Timed }
private enum class StationState { Active, Inactive }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCreationScreen(
    navController: NavController,
) {
    var stationName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(StationMode.Manual) }
    var state by remember { mutableStateOf(StationState.Active) }
    var durationMinutes by remember { mutableStateOf("15") }
    var durationSeconds by remember { mutableStateOf("00") }
    var autoJoinEnabled by remember { mutableStateOf(true) }
    var operatorManagesSessionsOnly by remember { mutableStateOf(false) }
    var enforceCheckinLimit by remember { mutableStateOf(false) }
    var checkinMinutes by remember { mutableStateOf("1") }
    var checkinSeconds by remember { mutableStateOf("00") }
    val joinFormFields = remember { mutableStateListOf<JoinFormField>() }
    
    val repository = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userId = remember { DeviceIdManager.getUserId(context) }
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Create Station") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
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
                        selected = mode == StationMode.Manual,
                        onClick = { mode = StationMode.Manual },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            if (mode == StationMode.Manual) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        label = { Text("Manual") }
                    )
                    SegmentedButton(
                        selected = mode == StationMode.Timed,
                        onClick = { mode = StationMode.Timed },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            if (mode == StationMode.Timed) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        label = { Text("Timed") }
                    )
                }

                if (mode == StationMode.Timed) {
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
                        Text("Auto-start on NFC tap (idle only)", style = MaterialTheme.typography.titleSmall)
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
                        onCheckedChange = {
                            if (!operatorManagesSessionsOnly) autoJoinEnabled = it
                        },
                        enabled = !operatorManagesSessionsOnly
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Only operator can manages sessions", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Guests cannot start or end sessions. Operators seat guests from queue management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = operatorManagesSessionsOnly,
                        onCheckedChange = {
                            operatorManagesSessionsOnly = it
                            if (it) autoJoinEnabled = false
                        }
                    )
                }

                Text("Join form fields", style = MaterialTheme.typography.titleMedium)
                if (joinFormFields.isEmpty()) {
                    Text(
                        "No fields (guests can join without entering details).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    joinFormFields.forEachIndexed { index, field ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = field.label,
                                    onValueChange = { label ->
                                        joinFormFields[index] = field.copy(label = label)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Label") }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = field.required,
                                            onCheckedChange = { required ->
                                                joinFormFields[index] = field.copy(
                                                    required = required
                                                )
                                            }
                                        )
                                        Text("Required")
                                    }
                                    IconButton(onClick = { joinFormFields.removeAt(index) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove field"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        joinFormFields.add(
                            JoinFormField(
                                key = "",
                                label = "",
                                required = false
                            )
                        )
                    }
                ) {
                    Text("Add field")
                }

                Text("Starting State", style = MaterialTheme.typography.titleMedium)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state == StationState.Active,
                        onClick = { state = StationState.Active },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            if (state == StationState.Active) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        label = { Text("Active") }
                    )
                    SegmentedButton(
                        selected = state == StationState.Inactive,
                        onClick = { state = StationState.Inactive },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            if (state == StationState.Inactive) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        label = { Text("Inactive") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            DraftAndPublishButtons(
                enabled = stationName.isNotBlank() &&
                        (mode != StationMode.Timed || run {
                            val total =
                                (durationMinutes.toIntOrNull() ?: 0) * 60 +
                                    (durationSeconds.toIntOrNull() ?: 0)
                            total > 0
                        }) &&
                        (!enforceCheckinLimit || run {
                            val total =
                                (checkinMinutes.toIntOrNull() ?: 0) * 60 +
                                    (checkinSeconds.toIntOrNull() ?: 0)
                            total > 0
                        }),
                onSaveDraft = {
                    // TODO: save draft logic
                    navController.navigate(Screen.MyStations.route)
                },
                onPublish = {
                    scope.launch {
                        val durationTotalSeconds =
                            (durationMinutes.toIntOrNull() ?: 0) * 60 +
                                (durationSeconds.toIntOrNull() ?: 0)
                        val checkinTotalSeconds =
                            (checkinMinutes.toIntOrNull() ?: 0) * 60 +
                                (checkinSeconds.toIntOrNull() ?: 0)
                        val normalizedFields = normalizeJoinFormFields(joinFormFields.toList())
                        val newStation = Station(
                            name = stationName,
                            ownerId = userId,
                            mode = mode.toString().lowercase(),
                            isActive = state == StationState.Active,
                            sessionDurationSeconds = durationTotalSeconds,
                            autoJoinEnabled = autoJoinEnabled,
                            operatorManagesSessionsOnly = operatorManagesSessionsOnly,
                            joinFormFields = normalizedFields,
                            enforceCheckinLimit = enforceCheckinLimit,
                            checkinWindowSeconds = checkinTotalSeconds.coerceAtLeast(1)
                        )
                        repository.setStation(newStation)
                        navController.navigate(Screen.MyStations.route)
                    }
                }
            )
        }
    }
}

@Composable
private fun DraftAndPublishButtons(
    enabled: Boolean,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSaveDraft,
            enabled = enabled,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("Save as draft")
        }

        Spacer(Modifier.width(3.dp))

        Button(
            onClick = onPublish,
            enabled = enabled,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50), // Green
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Publish")
        }
    }
}

private fun normalizeJoinFormFields(
    fields: List<JoinFormField>
): List<JoinFormField> {
    val usedKeys = mutableSetOf<String>()
    return fields.mapIndexed { index, field ->
        val base = field.label.trim().lowercase()
        var key = base
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-z0-9_]".toRegex(), "")
        if (key.isBlank()) {
            key = "field${index + 1}"
        }
        var uniqueKey = key
        var suffix = 2
        while (usedKeys.contains(uniqueKey)) {
            uniqueKey = "${key}_$suffix"
            suffix++
        }
        usedKeys.add(uniqueKey)
        field.copy(key = uniqueKey)
    }
}

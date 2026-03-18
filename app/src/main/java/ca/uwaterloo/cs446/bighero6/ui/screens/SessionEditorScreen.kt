package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.JoinFormField
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
    var presetSelection by remember { mutableStateOf(StationPresetSelection.Custom) }
    var durationMinutes by remember { mutableStateOf("15") }
    var durationSeconds by remember { mutableStateOf("00") }
    var autoJoinEnabled by remember { mutableStateOf(true) }
    var operatorManagesSessionsOnly by remember { mutableStateOf(false) }
    var notificationMode by remember { mutableStateOf("auto") }
    var showPositionToGuests by remember { mutableStateOf(true) }
    var enforceCheckinLimit by remember { mutableStateOf(false) }
    var checkinMinutes by remember { mutableStateOf("1") }
    var checkinSeconds by remember { mutableStateOf("00") }
    val joinFormFields = remember { mutableStateListOf<JoinFormField>() }

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
            operatorManagesSessionsOnly = station.operatorManagesSessionsOnly
            notificationMode = station.notificationMode
            showPositionToGuests = station.showPositionToGuests
            enforceCheckinLimit = station.enforceCheckinLimit
            checkinMinutes = (station.checkinWindowSeconds / 60).toString()
            checkinSeconds = (station.checkinWindowSeconds % 60).toString().padStart(2, '0')
            joinFormFields.clear()
            joinFormFields.addAll(station.joinFormFields)
            // Derive initial preset selection from existing config; times and form fields don't matter.
            val isTimed = station.mode == "timed"
            val isSelfServeConfig =
                !station.operatorManagesSessionsOnly &&
                    station.notificationMode == "auto" &&
                    station.showPositionToGuests &&
                    station.enforceCheckinLimit &&
                    isTimed &&
                    station.autoJoinEnabled
            val isMannedConfig =
                station.operatorManagesSessionsOnly &&
                    station.notificationMode == "manual" &&
                    !station.showPositionToGuests &&
                    station.enforceCheckinLimit &&
                    !station.autoJoinEnabled &&
                    !isTimed
            presetSelection = when {
                isSelfServeConfig -> StationPresetSelection.SelfServe
                isMannedConfig -> StationPresetSelection.Manned
                else -> StationPresetSelection.Custom
            }
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
                    StationNameRow(
                        stationName = stationName,
                        onNameChange = { stationName = it }
                    )

                    StationPresetRow(
                        selection = presetSelection,
                        onSelfServe = {
                            applySelfServePreset(
                                setOperatorManaged = { operatorManagesSessionsOnly = it },
                                setNotificationMode = { notificationMode = it },
                                setShowPositionToGuests = { showPositionToGuests = it },
                                setEnforceCheckinLimit = { enforceCheckinLimit = it },
                                setIsTimed = { isTimed -> mode = if (isTimed) EditorStationMode.Timed else EditorStationMode.Manual },
                                setAutoJoinEnabled = { autoJoinEnabled = it }
                            )
                            presetSelection = StationPresetSelection.SelfServe
                        },
                        onManned = {
                            applyMannedPreset(
                                setOperatorManaged = { operatorManagesSessionsOnly = it },
                                setNotificationMode = { notificationMode = it },
                                setShowPositionToGuests = { showPositionToGuests = it },
                                setEnforceCheckinLimit = { enforceCheckinLimit = it },
                                setIsTimed = { isTimed -> mode = if (isTimed) EditorStationMode.Timed else EditorStationMode.Manual },
                                setAutoJoinEnabled = { autoJoinEnabled = it }
                            )
                            presetSelection = StationPresetSelection.Manned
                        }
                    )

                    SessionModeRow(
                        isTimed = (mode == EditorStationMode.Timed),
                        onModeChange = { isTimed ->
                            mode = if (isTimed) EditorStationMode.Timed else EditorStationMode.Manual
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    SessionLengthRow(
                        isTimed = (mode == EditorStationMode.Timed),
                        durationMinutes = durationMinutes,
                        durationSeconds = durationSeconds,
                        onMinutesChange = { durationMinutes = it },
                        onSecondsChange = { durationSeconds = it }
                    )

                    EnforceCheckinRow(
                        enforceCheckinLimit = enforceCheckinLimit,
                        onToggle = {
                            enforceCheckinLimit = it
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    CheckinWindowRow(
                        enforceCheckinLimit = enforceCheckinLimit,
                        checkinMinutes = checkinMinutes,
                        checkinSeconds = checkinSeconds,
                        onMinutesChange = { checkinMinutes = it },
                        onSecondsChange = { checkinSeconds = it }
                    )

                    AutoStartRow(
                        autoJoinEnabled = autoJoinEnabled,
                        operatorManagesSessionsOnly = operatorManagesSessionsOnly,
                        onToggle = {
                            autoJoinEnabled = it
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    OperatorManagedRow(
                        operatorManagesSessionsOnly = operatorManagesSessionsOnly,
                        onToggle = {
                            operatorManagesSessionsOnly = it
                            if (it) autoJoinEnabled = false
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    NotificationModeRow(
                        notificationMode = notificationMode,
                        onModeChange = {
                            notificationMode = it
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    ShowPositionRow(
                        showPositionToGuests = showPositionToGuests,
                        onToggle = {
                            showPositionToGuests = it
                            presetSelection = StationPresetSelection.Custom
                        }
                    )

                    JoinFormFieldsEditor(joinFormFields)

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
                                    operatorManagesSessionsOnly = operatorManagesSessionsOnly,
                                    notificationMode = notificationMode,
                                    showPositionToGuests = showPositionToGuests,
                                    joinFormFields = normalizeJoinFormFields(joinFormFields.toList()),
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

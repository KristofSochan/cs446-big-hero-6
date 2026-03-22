package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    var presetSelection by remember { mutableStateOf(StationPresetSelection.Custom) }
    var durationMinutes by remember { mutableStateOf("15") }
    var durationSeconds by remember { mutableStateOf("00") }
    var autoJoinEnabled by remember { mutableStateOf(true) }
    var operatorManagesSessionsOnly by remember { mutableStateOf(false) }
    var notificationMode by remember { mutableStateOf("auto") }
    var showPositionToGuests by remember { mutableStateOf(true) }
    var allowMultipleWaitlists by remember { mutableStateOf(true) }
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
                            setIsTimed = { isTimed -> mode = if (isTimed) StationMode.Timed else StationMode.Manual },
                            setAutoJoinEnabled = { autoJoinEnabled = it },
                            setAllowMultipleWaitlists = { allowMultipleWaitlists = it }
                        )
                        presetSelection = StationPresetSelection.SelfServe
                    },
                    onManned = {
                        applyMannedPreset(
                            setOperatorManaged = { operatorManagesSessionsOnly = it },
                            setNotificationMode = { notificationMode = it },
                            setShowPositionToGuests = { showPositionToGuests = it },
                            setEnforceCheckinLimit = { enforceCheckinLimit = it },
                            setIsTimed = { isTimed -> mode = if (isTimed) StationMode.Timed else StationMode.Manual },
                            setAutoJoinEnabled = { autoJoinEnabled = it },
                            setAllowMultipleWaitlists = { allowMultipleWaitlists = it }
                        )
                        presetSelection = StationPresetSelection.Manned
                    }
                )

                SessionModeRow(
                    isTimed = (mode == StationMode.Timed),
                    onModeChange = { isTimed ->
                        mode = if (isTimed) StationMode.Timed else StationMode.Manual
                        presetSelection = StationPresetSelection.Custom
                    }
                )

                SessionLengthRow(
                    isTimed = (mode == StationMode.Timed),
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

                AllowMultipleWaitlistsRow(
                    allowMultipleWaitlists = allowMultipleWaitlists,
                    onToggle = {
                        allowMultipleWaitlists = it
                        presetSelection = StationPresetSelection.Custom
                    }
                )

                JoinFormFieldsEditor(joinFormFields)

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
                            notificationMode = notificationMode,
                            showPositionToGuests = showPositionToGuests,
                            allowMultipleWaitlists = allowMultipleWaitlists,
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

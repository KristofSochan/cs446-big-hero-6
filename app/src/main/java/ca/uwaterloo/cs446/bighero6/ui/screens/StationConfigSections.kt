package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material3.Divider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import ca.uwaterloo.cs446.bighero6.data.JoinFormField

@Composable
fun StationPresetRow(
    selection: StationPresetSelection,
    onSelfServe: () -> Unit,
    onManned: () -> Unit
) {
    Text("Presets", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(2.dp))
    Text(
        "Choose a starting configuration. You can still fine-tune the options below.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selection == StationPresetSelection.SelfServe,
            onClick = onSelfServe,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            label = { Text("Self-serve") },
            icon = {}
        )
        SegmentedButton(
            selected = selection == StationPresetSelection.Manned,
            onClick = onManned,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            label = { Text("Manned") },
            icon = {}
        )
        SegmentedButton(
            selected = selection == StationPresetSelection.Custom,
            onClick = { /* Custom is a derived state; no-op on tap */ },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            label = { Text("Custom") },
            icon = {}
        )
    }
    Spacer(Modifier.height(8.dp))
    Divider()
}

enum class StationPresetSelection {
    SelfServe,
    Manned,
    Custom,
}

@Composable
fun StationNameRow(
    stationName: String,
    onNameChange: (String) -> Unit
) {
    Text("Station Name", style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
        value = stationName,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Station Name") }
    )
}

@Composable
fun SessionModeRow(
    isTimed: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Text("Station Mode", style = MaterialTheme.typography.titleMedium)

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isTimed,
            onClick = { onModeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                if (!isTimed) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            },
            label = { Text("Manual") }
        )
        SegmentedButton(
            selected = isTimed,
            onClick = { onModeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                if (isTimed) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            },
            label = { Text("Timed") }
        )
    }
}

@Composable
fun SessionLengthRow(
    isTimed: Boolean,
    durationMinutes: String,
    durationSeconds: String,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit
) {
    if (!isTimed) return
    Text("Session length", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = durationMinutes,
            onValueChange = { onMinutesChange(it.filter(Char::isDigit)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Minutes") },
            placeholder = { Text("15") }
        )
        OutlinedTextField(
            value = durationSeconds,
            onValueChange = {
                val digits = it.filter(Char::isDigit).take(2)
                val sec = digits.toIntOrNull()
                val normalized = when {
                    digits.isEmpty() -> ""
                    sec == null -> ""
                    sec > 59 -> "59"
                    else -> digits.padStart(2, '0')
                }
                onSecondsChange(normalized)
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Seconds") },
            placeholder = { Text("00") }
        )
    }
}

@Composable
fun NotificationModeRow(
    notificationMode: String,
    onModeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Notification mode", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Auto notifies when guests reach the front. Manual lets the operator choose when to notify.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = notificationMode == "auto",
                    onClick = { onModeChange("auto") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Auto") }
                )
                SegmentedButton(
                    selected = notificationMode == "manual",
                    onClick = { onModeChange("manual") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Manual") }
                )
            }
        }
    }
}

@Composable
fun ShowPositionRow(
    showPositionToGuests: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Show position to guests", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "If off, guests just see that they’re waiting and will be notified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = showPositionToGuests,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun JoinFormFieldsEditor(
    joinFormFields: SnapshotStateList<JoinFormField>
) {
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
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
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
                                    joinFormFields[index] = field.copy(required = required)
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
    TextButtonAddField(joinFormFields)
}

@Composable
private fun TextButtonAddField(joinFormFields: SnapshotStateList<JoinFormField>) {
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
}

@Composable
fun EnforceCheckinRow(
    enforceCheckinLimit: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enforce check-in time limit", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Require guests to check in within a set time when it is their turn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enforceCheckinLimit,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun CheckinWindowRow(
    enforceCheckinLimit: Boolean,
    checkinMinutes: String,
    checkinSeconds: String,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit
) {
    if (!enforceCheckinLimit) return
    Text("Check-in window", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = checkinMinutes,
            onValueChange = { onMinutesChange(it.filter(Char::isDigit)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Minutes") },
            placeholder = { Text("1") }
        )
        OutlinedTextField(
            value = checkinSeconds,
            onValueChange = {
                val digits = it.filter(Char::isDigit).take(2)
                val sec = digits.toIntOrNull()
                val normalized = when {
                    digits.isEmpty() -> ""
                    sec == null -> ""
                    sec > 59 -> "59"
                    else -> digits.padStart(2, '0')
                }
                onSecondsChange(normalized)
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Seconds") },
            placeholder = { Text("00") }
        )
    }
}

@Composable
fun OperatorManagedRow(
    operatorManagesSessionsOnly: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Only operator can manage sessions", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Guests cannot start or end sessions. Operators seat guests from queue management.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = operatorManagesSessionsOnly,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun AutoStartRow(
    autoJoinEnabled: Boolean,
    operatorManagesSessionsOnly: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Auto-start on NFC tap (idle only)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Allow guests to start immediately when the station is idle (no active session and an empty queue).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = autoJoinEnabled,
            onCheckedChange = {
                if (!operatorManagesSessionsOnly) onToggle(it)
            },
            enabled = !operatorManagesSessionsOnly
        )
    }
}

/**
 * Shared preset application logic so create/edit screens don't drift.
 * Self-serve: guest-managed sessions, auto notifications, positions shown,
 * check-in enforced, timed mode on, auto-start allowed.
 */
fun applySelfServePreset(
    setOperatorManaged: (Boolean) -> Unit,
    setNotificationMode: (String) -> Unit,
    setShowPositionToGuests: (Boolean) -> Unit,
    setEnforceCheckinLimit: (Boolean) -> Unit,
    setIsTimed: (Boolean) -> Unit,
    setAutoJoinEnabled: (Boolean) -> Unit,
) {
    setOperatorManaged(false)
    setNotificationMode("auto")
    setShowPositionToGuests(true)
    setEnforceCheckinLimit(true)
    setIsTimed(true)
    setAutoJoinEnabled(true)
}

/**
 * Manned preset: operator-managed sessions, manual notifications, positions
 * hidden, check-in enforced, auto-start disabled. Timed/manual mode is left
 * to the caller via setIsTimed to avoid surprising changes.
 */
fun applyMannedPreset(
    setOperatorManaged: (Boolean) -> Unit,
    setNotificationMode: (String) -> Unit,
    setShowPositionToGuests: (Boolean) -> Unit,
    setEnforceCheckinLimit: (Boolean) -> Unit,
    setIsTimed: (Boolean) -> Unit,
    setAutoJoinEnabled: (Boolean) -> Unit,
) {
    setOperatorManaged(true)
    setNotificationMode("manual")
    setShowPositionToGuests(false)
    setEnforceCheckinLimit(true)
    setIsTimed(false)
    setAutoJoinEnabled(false)
}

/**
 * Normalizes JoinFormField keys based on their labels.
 */
fun normalizeJoinFormFields(
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

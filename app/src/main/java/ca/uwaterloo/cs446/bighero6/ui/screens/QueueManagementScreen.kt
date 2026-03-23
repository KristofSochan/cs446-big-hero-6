package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.data.User
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.ui.components.NavigateToHomeButton
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagementScreen(
    stationId: String,
    navController: NavController
) {
    var station by remember { mutableStateOf<Station?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val repository = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userCache = remember { mutableStateMapOf<String, User>() }

    DisposableEffect(stationId) {
        val registration = repository.subscribeToStation(stationId) { updatedStation ->
            station = updatedStation
            isLoading = false
            
            // Fetch missing user names for attendees and current session
            updatedStation?.let { s ->
                val userIdsToFetch = (s.attendees.keys + (s.currentSession?.userId?.let { setOf(it) } ?: emptySet()))
                    .filter { it !in userCache }
                
                userIdsToFetch.forEach { uid ->
                    scope.launch {
                        try {
                            val user = repository.getOrCreateUser(uid)
                            userCache[uid] = user
                        } catch (e: Exception) {
                            // Silently fail or use placeholder
                        }
                    }
                }
            }
        }
        onDispose {
            registration.remove()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Queue") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View analytics") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(
                                        Screen.StationAnalytics(stationId)
                                            .createRoute(stationId)
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (station == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Station not found",
                        color = MaterialTheme.colorScheme.error
                    )
                    NavigateToHomeButton(
                        navController = navController,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else {
            val attendees = station!!.attendees.values.sortedBy { it.joinedAt }
            val currentSession = station!!.currentSession
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = station!!.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (attendees.isEmpty() && currentSession == null) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("The queue is currently empty")
                    }
                } else {
                    // Current Session Section
                    currentSession?.let { session ->
                        val startedAtDate = session.startedAt?.toDate()
                        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val userName = userCache[session.userId]?.name?.ifEmpty { null } ?: "${session.userId?.take(8)}..."
                        
                        Text(
                            text = "Now Serving",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (startedAtDate != null) {
                                        Text(
                                            text = "Started at ${timeFormatter.format(startedAtDate)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "IN USE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    
                                    var sessionMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { sessionMenuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = sessionMenuExpanded,
                                            onDismissRequest = { sessionMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("End session") },
                                                onClick = {
                                                    sessionMenuExpanded = false
                                                    scope.launch {
                                                        repository.endSession(stationId)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Queue Section
                    if (attendees.isNotEmpty()) {
                        Text(
                            text = "Waitlist (${attendees.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(attendees) { index, attendee ->
                                val joinedAtDate = attendee.joinedAt.toDate()
                                val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                                val attendeeName = userCache[attendee.userId]?.name?.ifEmpty { null } ?: "${attendee.userId.take(8)}..."

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. $attendeeName",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Joined at ${timeFormatter.format(joinedAtDate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val form = attendee.form
                                            if (form.isNotEmpty()) {
                                                Spacer(Modifier.height(4.dp))
                                                val labelByKey = station!!.joinFormFields.associateBy({ it.key }, { it.label })
                                                form.forEach { (key, value) ->
                                                    if (value.isNotBlank()) {
                                                        val label = labelByKey[key].takeUnless { it.isNullOrBlank() } ?: key
                                                        Text(
                                                            text = "$label: $value",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (attendee.status == "waiting") 
                                                    MaterialTheme.colorScheme.secondaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.extraSmall
                                            ) {
                                                Text(
                                                    text = attendee.status.uppercase(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }

                                            var attendeeMenuExpanded by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { attendeeMenuExpanded = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                                }
                                                DropdownMenu(
                                                    expanded = attendeeMenuExpanded,
                                                    onDismissRequest = { attendeeMenuExpanded = false }
                                                ) {
                                                    // Only show Notify if no one is currently in session
                                                    if (index == 0 && currentSession == null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Notify guest") },
                                                            onClick = {
                                                                attendeeMenuExpanded = false
                                                                scope.launch {
                                                                    repository.notifyHead(stationId)
                                                                }
                                                            }
                                                        )
                                                    }
                                                    if (currentSession == null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Start session") },
                                                            onClick = {
                                                                attendeeMenuExpanded = false
                                                                scope.launch {
                                                                    val result =
                                                                        repository.startSessionAsOperator(
                                                                            stationId,
                                                                            attendee.userId
                                                                        )
                                                                    if (result.isFailure) {
                                                                        val msg =
                                                                            result.exceptionOrNull()
                                                                                ?.message
                                                                                ?: "Could not start session"
                                                                        snackbarHostState.showSnackbar(
                                                                            msg
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text("Bring to front of queue") },
                                                        onClick = {
                                                            attendeeMenuExpanded = false
                                                            scope.launch {
                                                                repository.moveAttendeeToFront(stationId, attendee.userId)
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Bring to back of queue") },
                                                        onClick = {
                                                            attendeeMenuExpanded = false
                                                            scope.launch {
                                                                repository.moveAttendeeToBack(stationId, attendee.userId)
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { 
                                                            Text(
                                                                "Remove from queue", 
                                                                color = MaterialTheme.colorScheme.error
                                                            ) 
                                                        },
                                                        onClick = {
                                                            attendeeMenuExpanded = false
                                                            scope.launch {
                                                                repository.removeFromWaitlist(stationId, attendee.userId)
                                                            }
                                                        }
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
            }
        }
    }
}

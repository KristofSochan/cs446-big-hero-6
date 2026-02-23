package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.User
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import ca.uwaterloo.cs446.bighero6.ui.components.TapListScaffold
import kotlinx.coroutines.launch

/**
 * Screen for user settings.
 */
@Composable
fun UserSetupScreen(navController: NavController, initialName: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val userId = remember { DeviceIdManager.getUserId(context) }
    val repository = remember { FirestoreRepository() }

    var nameInput by remember { mutableStateOf("") }
    var user by remember { mutableStateOf<User?>(null) }

    // Load user data
    LaunchedEffect(userId) {
        isLoading = true
        try {
            // This ensures a user document exists and syncs the name if provided during signup
            val fcmToken = DeviceIdManager.getFcmToken(context)
            val existingUser = repository.getOrCreateUser(userId, initialName, fcmToken)
            user = existingUser
            nameInput = existingUser.name

            // If this was an initial setup triggered by signup, we navigate to waitlists
            if (initialName != null) {
                navController.navigate(Screen.MyWaitlists.route) {
                    popUpTo(Screen.UserSetup.route) { inclusive = true }
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    fun saveUser() {
        scope.launch {
            isSaving = true
            error = null
            try {
                repository.getOrCreateUser(userId, nameInput.trim())
                user = user?.copy(name = nameInput.trim())
            } catch (e: Exception) {
                error = e.message ?: "Failed to save user"
            } finally {
                isSaving = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    TapListScaffold(
        navController = navController,
        currentScreen = Screen.UserSetup
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "User ID: $userId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Button(
                            onClick = ::saveUser,
                            enabled = nameInput.isNotBlank() && nameInput != user?.name,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Update Profile")
                        }
                    }
                }
            }
        }
    }
}

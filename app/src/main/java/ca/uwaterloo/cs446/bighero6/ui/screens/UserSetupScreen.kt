package ca.uwaterloo.cs446.bighero6.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.UiState
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import kotlinx.coroutines.launch

/**
 * Initial screen - creates user document in Firestore
 */
@Composable
fun UserSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val userId = remember { DeviceIdManager.getUserId(context) }
    
    fun createUser() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val repository = FirestoreRepository()
                val fcmToken = DeviceIdManager.getFcmToken(context)
                repository.getOrCreateUser(userId, fcmToken)
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.UserSetup.route) { inclusive = true }
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to create user"
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to TapList", style = MaterialTheme.typography.headlineLarge)
        Text("Join waitlists by tapping NFC tags", modifier = Modifier.padding(vertical = 16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(onClick = { createUser() }) {
                Text(if (error != null) "Retry" else "Get Started")
            }
        }
    }
}

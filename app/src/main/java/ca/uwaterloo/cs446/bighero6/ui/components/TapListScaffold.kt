package ca.uwaterloo.cs446.bighero6.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapListScaffold(
    navController: NavController,
    currentScreen: Screen,
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val userId = remember { DeviceIdManager.getUserId(context) }
    val repository = remember { FirestoreRepository() }
    var user by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(userId) {
        repository.subscribeToUser(userId) { updatedUser ->
            user = updatedUser
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TapList")
                        Text(
                            text = user?.name?.ifEmpty { "User ID: $userId" } ?: "Loading...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "My Waitlists") },
                    label = { Text("Waitlists") },
                    selected = currentScreen == Screen.MyWaitlists,
                    onClick = {
                        if (currentScreen != Screen.MyWaitlists) {
                            navController.navigate(Screen.MyWaitlists.route) {
                                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "My Stations") },
                    label = { Text("Stations") },
                    selected = currentScreen == Screen.MyStations,
                    onClick = {
                        if (currentScreen != Screen.MyStations) {
                            navController.navigate(Screen.MyStations.route) {
                                popUpTo(Screen.MyWaitlists.route)
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "User Settings") },
                    label = { Text("Settings") },
                    selected = currentScreen == Screen.UserSetup,
                    onClick = {
                        if (currentScreen != Screen.UserSetup) {
                            navController.navigate(Screen.UserSetup.route)
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                    label = { Text("About") },
                    selected = currentScreen == Screen.About,
                    onClick = {
                        if (currentScreen != Screen.About) {
                            navController.navigate(Screen.About.route)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

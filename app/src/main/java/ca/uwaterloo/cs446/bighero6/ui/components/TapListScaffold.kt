package ca.uwaterloo.cs446.bighero6.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.data.User
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapListScaffold(
    navController: NavController,
    currentScreen: Screen,
    content: @Composable (PaddingValues) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val repository = remember { FirestoreRepository() }
    val userId = auth.currentUser?.uid
    var user by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            user = repository.getUser(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TapList")
                        if (user != null) {
                            Text(
                                text = user?.displayName ?: "No name set",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                popUpTo(Screen.MyWaitlists.route)
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentScreen == Screen.Settings,
                    onClick = {
                        if (currentScreen != Screen.Settings) {
                            navController.navigate(Screen.Settings.route)
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

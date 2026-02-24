package ca.uwaterloo.cs446.bighero6.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapListScaffold(
    navController: NavController,
    currentScreen: Screen,
    content: @Composable (PaddingValues) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val userId = remember { DeviceIdManager.getUserId(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TapList")
                        Text(
                            text = "User ID: $userId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = "My Waitlists",
                                        fontWeight = FontWeight.Bold
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    if (currentScreen != Screen.MyWaitlists) {
                                        navController.navigate(Screen.MyWaitlists.route)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = "My Stations",
                                        fontWeight = FontWeight.Bold
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    if (currentScreen != Screen.MyStations) {
                                        navController.navigate(Screen.MyStations.route)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("User Settings") },
                                onClick = { showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

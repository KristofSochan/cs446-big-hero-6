package ca.uwaterloo.cs446.bighero6.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthRouteGuard(
    navController: NavController,
    content: @Composable () -> Unit
) {
    var isCheckingAuth by remember { mutableStateOf(true) }
    val auth = FirebaseAuth.getInstance()

    // Check if user isn't signed in and redirect to login
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            navController.navigate(Screen.SignIn.route) {
                popUpTo(Screen.SignIn.route) { inclusive = true }
            }
        } else {
            isCheckingAuth = false
        }
    }

    if (isCheckingAuth) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        content()
    }
}
package ca.uwaterloo.cs446.bighero6.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ca.uwaterloo.cs446.bighero6.navigation.Screen

/**
 * Navigates to the main signed-in home (My Waitlists), clearing duplicate entries on the back stack.
 */
@Composable
fun NavigateToHomeButton(
    navController: NavController,
    modifier: Modifier = Modifier,
    label: String = "Back to My Waitlists"
) {
    TextButton(
        onClick = {
            navController.navigate(Screen.MyWaitlists.route) {
                popUpTo(Screen.MyWaitlists.route) { inclusive = false }
            }
        },
        modifier = modifier
    ) {
        Text(label)
    }
}

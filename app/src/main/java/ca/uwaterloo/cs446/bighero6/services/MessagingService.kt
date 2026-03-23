package ca.uwaterloo.cs446.bighero6.services

import android.util.Log
import androidx.compose.ui.platform.LocalContext
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService(){
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("MessagingService", "Remote Message Received")

        // Method 1: Handle notification payload
        remoteMessage.notification?.let {
            // Logic to display a notification
            val title = it.title
            val body = it.body

            Log.d("MessagingService", "Notification Title: $title, Notification Body: $body")
        }

        // Method 2: Handle data payload (custom key-value pairs)
        if (remoteMessage.data.isNotEmpty()) {
            remoteMessage.data.forEach { (key, value) ->
                Log.d("MessagingService", "Data Payload - Key: $key, Value: $value")
            }
        }
    }

    override fun onNewToken(token: String) {
        // Logic to send this token to your backend server
        // Update current user field with the new token available to the user
        sendRegistrationToServer(token)
        Log.d("MessagingService", "New FCM token generated")
    }

    private fun sendRegistrationToServer(token: String?){
        if (token != null){
            DeviceIdManager.saveFcmToken(this, token)
        }
    }
}
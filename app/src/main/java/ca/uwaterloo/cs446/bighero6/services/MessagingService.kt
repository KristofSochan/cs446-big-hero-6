package ca.uwaterloo.cs446.bighero6.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService(){
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Method 1: Handle notification payload
        remoteMessage.notification?.let {
            // Logic to display a notification
        }

        // Method 2: Handle data payload (custom key-value pairs)
        if (remoteMessage.data.isNotEmpty()) {
            // Logic to process raw data
        }
    }

    override fun onNewToken(token: String) {
        // Logic to send this token to your backend server
    }
}
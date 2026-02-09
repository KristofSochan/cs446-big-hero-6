package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class User(
    @DocumentId
    val userId: String = "",
    val deviceId: String = "",
    val fcmToken: String? = null,
    val currentWaitlists: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null  // Set by server on creation
)

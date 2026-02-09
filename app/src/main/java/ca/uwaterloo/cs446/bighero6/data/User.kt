package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    @DocumentId
    val userId: String = "",
    val deviceId: String = "",
    val fcmToken: String? = null,
    val currentWaitlists: List<String> = emptyList()
)

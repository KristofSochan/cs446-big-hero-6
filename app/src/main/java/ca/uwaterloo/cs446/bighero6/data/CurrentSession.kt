package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class CurrentSession(
    val userId: String? = null,
    val startedAt: Timestamp? = null, // Client-side timestamp
    val expiresAt: Timestamp? = null
)

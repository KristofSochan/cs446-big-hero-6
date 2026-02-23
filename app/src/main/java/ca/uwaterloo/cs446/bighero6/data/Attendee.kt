package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Attendee(
    val userId: String = "",
    val status: String = "waiting", // "waiting", "attending", "removed"
    val joinedAt: Timestamp = Timestamp.now() // Client-side timestamp
)

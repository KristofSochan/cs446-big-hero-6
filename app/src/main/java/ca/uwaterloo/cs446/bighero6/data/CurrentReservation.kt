package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class CurrentReservation(
    val userId: String = "",
    val expiresAt: Timestamp? = null
)

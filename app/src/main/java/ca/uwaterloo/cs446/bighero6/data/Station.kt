package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Station(
    @DocumentId
    val docId: String = "",
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    
    @get:PropertyName("isActive")
    @PropertyName("isActive")
    val isActive: Boolean = true,
    
    val sessionDurationSeconds: Int = 900,
    val mode: String = "manual",
    val attendees: List<Attendee> = emptyList(),
    val currentSession: CurrentSession? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun calculatePosition(userId: String): Int {
        val waitingAttendees = attendees
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.toDate().time }
        
        val index = waitingAttendees.indexOfFirst { it.userId == userId }
        return if (index >= 0) index + 1 else 0
    }
    
    fun isAtPositionOne(userId: String): Boolean {
        val waitingAttendees = attendees
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.toDate().time }
        
        return waitingAttendees.isNotEmpty() && waitingAttendees[0].userId == userId
    }
}

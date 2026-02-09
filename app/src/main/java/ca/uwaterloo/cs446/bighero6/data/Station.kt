package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Station(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val isActive: Boolean = true,
    val sessionDurationMinutes: Int = 15,
    // Waitlist data embedded
    val attendees: List<Attendee> = emptyList(),
    val currentSession: CurrentSession? = null
) {
    /**
     * Calculate position of a user in the waitlist.
     * Position is determined by sorting attendees by joinedAt timestamp.
     */
    fun calculatePosition(userId: String): Int {
        val waitingAttendees = attendees
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.seconds }
        
        val index = waitingAttendees.indexOfFirst { it.userId == userId }
        return if (index >= 0) index + 1 else 0
    }
    
    /**
     * Check if user is at position 1
     */
    fun isAtPositionOne(userId: String): Boolean {
        val waitingAttendees = attendees
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.seconds }
        
        return waitingAttendees.isNotEmpty() && waitingAttendees[0].userId == userId
    }
}

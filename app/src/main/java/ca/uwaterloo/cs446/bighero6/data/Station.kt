package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Station(
    @DocumentId
    val docId: String = "",
    val id: String = "", // same val as docId but is a standard field of the document
    val name: String = "",
    val isActive: Boolean = true,
    val sessionDurationSeconds: Int = 900,
    val mode: String = "manual", // "manual" or "timed"
    // Waitlist data embedded
    val attendees: List<Attendee> = emptyList(),
    val currentSession: CurrentSession? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null  // Set by server on creation
) {
    /**
     * Calculate position of a user in the waitlist.
     * Position is determined by sorting attendees by joinedAt timestamp.
     */
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

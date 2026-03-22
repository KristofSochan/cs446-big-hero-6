package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class JoinFormField(
    val key: String = "",
    val label: String = "",
    val required: Boolean = false
)

@IgnoreExtraProperties
data class Station(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    
    @get:PropertyName("isActive")
    @PropertyName("isActive")
    val isActive: Boolean = true,
    
    val sessionDurationSeconds: Int = 900,
    val mode: String = "manual",
    /**
     * If enabled, an NFC tap can immediately start a session when the station is idle
     * (no active session and no waiting attendees).
     */
    val autoJoinEnabled: Boolean = true,
    /**
     * If enabled, guests cannot start or end sessions; operators manage seating.
     * (Used for manned waitlists like restaurants.)
     */
    val operatorManagesSessionsOnly: Boolean = false,
    /** How guests are notified when it's their turn: "auto" or "manual". */
    val notificationMode: String = "auto",
    /** Whether guests see explicit position/ETA in the queue UI. */
    val showPositionToGuests: Boolean = true,
    /**
     * When false, guests cannot join if they are already waiting or in session
     * at another station. When true (default), multiple waitlists are allowed.
     */
    val allowMultipleWaitlists: Boolean = true,
    /** Dynamic fields required/optional when joining the waitlist. */
    val joinFormFields: List<JoinFormField> = emptyList(),
    val enforceCheckinLimit: Boolean = false,
    /** Check-in window in seconds (how long the head of queue has to start). */
    val checkinWindowSeconds: Int = 60,
    val attendees: Map<String, Attendee> = emptyMap(),
    val currentSession: CurrentSession? = null,
    val currentReservation: CurrentReservation? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun calculatePosition(userId: String): Int {
        val waitingAttendees = attendees.values
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.toDate().time }
        val index = waitingAttendees.indexOfFirst { it.userId == userId }
        return if (index >= 0) index + 1 else 0
    }

    fun isAtPositionOne(userId: String): Boolean {
        val waitingAttendees = attendees.values
            .filter { it.status == "waiting" }
            .sortedBy { it.joinedAt.toDate().time }
        return waitingAttendees.isNotEmpty() && waitingAttendees[0].userId == userId
    }
}

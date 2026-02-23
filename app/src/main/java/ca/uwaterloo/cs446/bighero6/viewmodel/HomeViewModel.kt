package ca.uwaterloo.cs446.bighero6.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Summary of a waitlist for display on home screen
 */
data class WaitlistSummary(
    val stationId: String,
    val stationName: String,
    val position: Int,
    val estimatedWaitTime: String,
    val isInSession: Boolean,
    val hasActiveSession: Boolean,
    val waitingCount: Int
)

/**
 * Manages home screen state - subscribes to user's waitlists and calculates positions
 */
class HomeViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val listeners = mutableListOf<ListenerRegistration>()
    
    val waitlists = MutableStateFlow<List<WaitlistSummary>>(emptyList())
    
    fun subscribeToWaitlists(context: android.content.Context) {
        viewModelScope.launch {
            val userId = DeviceIdManager.getUserId(context)
            val user = repository.getOrCreateUser(userId)
            
            listeners.forEach { it.remove() }
            listeners.clear()
            
            // Subscribe to real-time updates for each waitlist
            user.currentWaitlists.forEach { stationId ->
                val listener = repository.subscribeToStation(stationId) { station ->
                    station?.let { updateWaitlistSummary(it, userId) }
                }
                listeners.add(listener)
            }
        }
    }
    
    private fun updateWaitlistSummary(station: Station, userId: String) {
        val isInSession = station.currentSession?.userId == userId
        val hasActiveSession = station.currentSession != null
        val waitingCount = station.attendees.count { it.status == "waiting" }
        val position = if (isInSession) 0 else station.calculatePosition(userId)
        val isTimedMode = station.mode == "timed"

        val eta = when {
            !isTimedMode -> ""
            isInSession -> "In session"
            position <= 0 -> ""
            else -> {
                val perSessionMinutes = TimeUnit.SECONDS.toMinutes(
                    station.sessionDurationSeconds.toLong()
                ).toInt().coerceAtLeast(1)

                // Time remaining in the current session, if any
                val remainingMinutes = station.currentSession?.expiresAt?.let { expiresAt ->
                    val nowMillis = System.currentTimeMillis()
                    val remainingMillis = expiresAt.toDate().time - nowMillis
                    if (remainingMillis > 0) {
                        TimeUnit.MILLISECONDS.toMinutes(remainingMillis).toInt().coerceAtLeast(0)
                    } else {
                        0
                    }
                } ?: 0

                val peopleAhead = (position - 1).coerceAtLeast(0)
                val queueMinutes = peopleAhead * perSessionMinutes
                val totalMinutes = remainingMinutes + queueMinutes

                if (totalMinutes <= 0) {
                    "0 min"
                } else {
                    "$totalMinutes min"
                }
            }
        }
        
        val summary = WaitlistSummary(
            station.id,
            station.name,
            position,
            eta,
            isInSession,
            hasActiveSession,
            waitingCount
        )
        waitlists.value = waitlists.value.filter { it.stationId != station.id } + summary
    }
    
    override fun onCleared() {
        listeners.forEach { it.remove() }
    }
}

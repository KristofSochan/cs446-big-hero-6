package ca.uwaterloo.cs446.bighero6.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var stationListeners = mutableMapOf<String, ListenerRegistration>()
    private var userListener: ListenerRegistration? = null
    
    val waitlists = MutableStateFlow<List<WaitlistSummary>>(emptyList())
    
    fun subscribeToWaitlists(context: android.content.Context) {
        viewModelScope.launch {
            val userId = DeviceIdManager.getUserId(context)
            
            userListener?.remove()
            userListener = repository.subscribeToUser(userId) { user ->
                if (user == null) {
                    waitlists.value = emptyList()
                    return@subscribeToUser
                }

                val currentStationIds = user.currentWaitlists.toSet()
                
                // Remove listeners for stations we are no longer in
                val stationsToRemove = stationListeners.keys - currentStationIds
                stationsToRemove.forEach { stationId ->
                    stationListeners[stationId]?.remove()
                    stationListeners.remove(stationId)
                }
                
                // Remove summaries for stations we are no longer in
                waitlists.value = waitlists.value.filter { it.stationId in currentStationIds }

                // Add listeners for new stations
                currentStationIds.forEach { stationId ->
                    if (!stationListeners.containsKey(stationId)) {
                        val listener = repository.subscribeToStation(stationId) { station ->
                            if (station == null || userId !in station.attendees && station.currentSession?.userId != userId) {
                                // If station doesn't exist or we're not in it anymore, remove it
                                waitlists.value = waitlists.value.filter { it.stationId != stationId }
                            } else {
                                updateWaitlistSummary(station, userId)
                            }
                        }
                        stationListeners[stationId] = listener
                    }
                }
            }
        }
    }
    
    private fun updateWaitlistSummary(station: Station, userId: String) {
        val isInSession = station.currentSession?.userId == userId
        val hasActiveSession = station.currentSession != null
        val waitingCount = station.attendees.values.count { it.status == "waiting" }
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
        userListener?.remove()
        stationListeners.values.forEach { it.remove() }
    }
}

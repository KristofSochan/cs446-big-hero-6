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

/**
 * Summary of a waitlist for display on home screen
 */
data class WaitlistSummary(
    val stationId: String,
    val stationName: String,
    val position: Int,
    val estimatedWaitTime: String,
    val isInSession: Boolean
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
        val position = if (isInSession) 0 else station.calculatePosition(userId)
        val eta = when {
            isInSession -> "In session"
            position > 0 -> "${(position - 1) * 3} min"
            else -> "N/A"
        }
        
        val summary = WaitlistSummary(station.id, station.name, position, eta, isInSession)
        waitlists.value = waitlists.value.filter { it.stationId != station.id } + summary
    }
    
    override fun onCleared() {
        listeners.forEach { it.remove() }
    }
}

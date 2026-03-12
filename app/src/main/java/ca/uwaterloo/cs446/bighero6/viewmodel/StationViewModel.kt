package ca.uwaterloo.cs446.bighero6.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.UiState
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Manages station screen state - loading station, joining waitlist, starting session.
 * Subscribes to the station so name, queue size, position, and "Your turn" update in real time.
 */
class StationViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private var stationListener: ListenerRegistration? = null

    val stationState = MutableStateFlow<UiState<Station>>(UiState.Loading)
    val joinState = MutableStateFlow<UiState<Unit>>(UiState.Idle)

    /**
     * Subscribes to the station so the screen stays reactive to queue/session changes.
     */
    fun loadStation(stationId: String) {
        stationListener?.remove()
        stationListener = null
        stationState.value = UiState.Loading

        stationListener = repository.subscribeToStation(stationId) { station ->
            stationState.value = if (station != null) {
                UiState.Success(station)
            } else {
                UiState.Error("Station not found")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stationListener?.remove()
        stationListener = null
    }
    
    fun joinWaitlist(stationId: String, context: android.content.Context) {
        viewModelScope.launch {
            joinState.value = UiState.Loading
            val userId = DeviceIdManager.getUserId(context)
            val result = repository.addToWaitlist(stationId, userId)
            joinState.value = if (result.isSuccess) {
                UiState.Success(Unit)
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Failed to join")
            }
        }
    }

    fun leaveWaitlist(stationId: String, context: android.content.Context) {
        viewModelScope.launch {
            joinState.value = UiState.Loading
            val userId = DeviceIdManager.getUserId(context)
            val result = repository.removeFromWaitlist(stationId, userId)
            joinState.value = if (result.isSuccess) {
                UiState.Success(Unit)
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Failed to leave queue")
            }
        }
    }
    
    fun checkAndStartSession(stationId: String, context: android.content.Context) {
        viewModelScope.launch {
            val userId = DeviceIdManager.getUserId(context)
            val station = repository.getStation(stationId)
            
            if (station != null && station.isAtPositionOne(userId)) {
                val mode = station.mode.ifEmpty { "manual" }
                val result = repository.startSession(stationId, userId, station.sessionDurationSeconds, mode)
                joinState.value = if (result.isSuccess) {
                    UiState.Success(Unit)
                } else {
                    UiState.Error(result.exceptionOrNull()?.message ?: "Failed to start session")
                }
            } else {
                joinState.value = UiState.Error("You're not at position 1")
            }
        }
    }
}

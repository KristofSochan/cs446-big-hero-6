package ca.uwaterloo.cs446.bighero6.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.UiState
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages station screen state - loading station, joining waitlist, starting session
 */
class StationViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    
    val stationState = MutableStateFlow<UiState<Station>>(UiState.Loading)
    val joinState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    
    fun loadStation(stationId: String) {
        viewModelScope.launch {
            stationState.value = UiState.Loading
            val station = repository.getStation(stationId)
            stationState.value = if (station != null) {
                UiState.Success(station)
            } else {
                UiState.Error("Station not found")
            }
        }
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

package ca.uwaterloo.cs446.bighero6.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages session timer - counts down from expiresAt time
 */
class SessionViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private var listener: ListenerRegistration? = null
    
    val timeRemaining = MutableStateFlow(0L)
    val isExpired = MutableStateFlow(false)
    
    fun startSessionTimer(stationId: String) {
        listener = repository.subscribeToStation(stationId) { station ->
            station?.currentSession?.expiresAt?.let { expiresAt ->
                viewModelScope.launch {
                    while (true) {
                        val remaining = expiresAt.seconds * 1000 - System.currentTimeMillis()
                        if (remaining <= 0) {
                            timeRemaining.value = 0
                            // End session when timer expires
                            repository.endSession(stationId)
                            isExpired.value = true
                            break
                        }
                        timeRemaining.value = remaining
                        delay(1000)
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        listener?.remove()
    }
}

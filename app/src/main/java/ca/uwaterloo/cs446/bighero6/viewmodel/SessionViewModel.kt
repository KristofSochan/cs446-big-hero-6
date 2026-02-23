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
 * Manages session state: countdown for timed mode, end-session for manual mode.
 */
class SessionViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private var listener: ListenerRegistration? = null

    val timeRemaining = MutableStateFlow(0L)
    val isExpired = MutableStateFlow(false)
    val endSessionState = MutableStateFlow<EndSessionState>(EndSessionState.Idle)

    sealed class EndSessionState {
        data object Idle : EndSessionState()
        data object Loading : EndSessionState()
        data object Success : EndSessionState()
        data class Error(val message: String) : EndSessionState()
    }

    /**
     * Subscribes to station; for timed mode only, runs countdown and auto-ends at expiresAt.
     * Manual mode has no timer — user ends via End Session button.
     */
    fun startSessionTimer(stationId: String) {
        endSessionState.value = EndSessionState.Idle
        listener = repository.subscribeToStation(stationId) { station ->
            station?.currentSession?.expiresAt?.let { expiresAt ->
                viewModelScope.launch {
                    while (true) {
                        val remaining = expiresAt.seconds * 1000 - System.currentTimeMillis()
                        if (remaining <= 0) {
                            timeRemaining.value = 0
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

    /**
     * End session (manual or early exit in timed). Clears currentSession so next person can start.
     */
    fun endSession(stationId: String) {
        viewModelScope.launch {
            endSessionState.value = EndSessionState.Loading
            val result = repository.endSession(stationId)
            endSessionState.value = if (result.isSuccess) {
                EndSessionState.Success
            } else {
                EndSessionState.Error(result.exceptionOrNull()?.message ?: "Failed to end session")
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}

package ca.uwaterloo.cs446.bighero6.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Manages session state: countdown for timed mode, end-session for manual mode.
 * Uses elapsed-only countdown: get initialRemainingMs from server once, then
 * displayRemaining = initialRemainingMs - elapsedSinceLoad (no client clock).
 */
class SessionViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private var listener: ListenerRegistration? = null
    private var countdownJob: Job? = null

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
     * Subscribes to station. When timed session has expiresAt, fetches initial remaining
     * from server (getSessionTime callable) then counts down by elapsed time only.
     */
    fun startSessionTimer(stationId: String) {
        endSessionState.value = EndSessionState.Idle
        isExpired.value = false
        timeRemaining.value = 0
        countdownJob?.cancel()

        listener = repository.subscribeToStation(stationId) { station ->
            if (station?.currentSession == null) {
                countdownJob?.cancel()
                countdownJob = null
                isExpired.value = true
                return@subscribeToStation
            }
            // Timed mode: once we have a session, fetch server time and start elapsed-only countdown
            if (station.currentSession.expiresAt != null && countdownJob?.isActive != true) {
                viewModelScope.launch {
                    val result = repository.getSessionTime(stationId)
                    val initialMs = result.initialRemainingMs ?: return@launch
                    val elapsedAtLoad = SystemClock.elapsedRealtime()
                    countdownJob?.cancel()
                    countdownJob = viewModelScope.launch {
                        while (true) {
                            delay(100)
                            val elapsed = SystemClock.elapsedRealtime() - elapsedAtLoad
                            val remaining = (initialMs - elapsed).coerceAtLeast(0L)
                            timeRemaining.value = remaining
                            if (remaining <= 0) {
                                repository.endSession(stationId)
                                isExpired.value = true
                                break
                            }
                        }
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
        countdownJob?.cancel()
        listener?.remove()
    }
}

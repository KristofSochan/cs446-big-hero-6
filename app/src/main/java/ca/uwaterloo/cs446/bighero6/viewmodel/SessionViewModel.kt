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
     * Subscribes to station. For timed mode: start a provisional countdown from
     * sessionDurationSeconds immediately (so user sees 15, 14, 13... right away),
     * then switch to server-derived countdown when getSessionTime returns.
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
                // Only treat this as an expiration if we were actively counting down.
                // On initial load before session starts, timeRemaining will be 0.
                if (timeRemaining.value > 0L) {
                    isExpired.value = true
                }
                return@subscribeToStation
            }
            val isTimed = station.mode == "timed"
            if (!isTimed) return@subscribeToStation

            val durationMs = (station.sessionDurationSeconds * 1000L).coerceAtLeast(1000L)
            if (countdownJob?.isActive == true) {
                viewModelScope.launch {
                    val result = repository.getSessionTime(stationId)
                    val serverInitialMs = result.initialRemainingMs ?: return@launch
                    switchToServerCountdown(stationId, serverInitialMs, durationMs)
                }
                return@subscribeToStation
            }

            startCountdown(stationId, durationMs)
        }
    }

    /** Starts the countdown. Timer begins at full duration. */
    private fun startCountdown(stationId: String, durationMs: Long) {
        timeRemaining.value = durationMs
        val provisionalStartRealtime = SystemClock.elapsedRealtime()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = SystemClock.elapsedRealtime() - provisionalStartRealtime
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                timeRemaining.value = remaining
                if (remaining <= 0) {
                    endSessionState.value = EndSessionState.Loading
                    repository.endSession(stationId)
                    endSessionState.value = EndSessionState.Success
                    isExpired.value = true
                    break
                }
            }
        }

        viewModelScope.launch {
            var retries = 0
            while (retries < 20) {
                delay(500)
                val result = repository.getSessionTime(stationId)
                val serverInitialMs = result.initialRemainingMs
                if (serverInitialMs != null) {
                    switchToServerCountdown(stationId, serverInitialMs, durationMs)
                    return@launch
                }
                retries++
            }
        }
    }

    /** Switches to server-based elapsed countdown. Caps at durationMs so we never show more than session length. */
    private fun switchToServerCountdown(stationId: String, initialRemainingMs: Long, durationMs: Long) {
        val cappedInitial = initialRemainingMs.coerceAtMost(durationMs)
        if (cappedInitial <= 0) return
        countdownJob?.cancel()
        val elapsedAtLoad = SystemClock.elapsedRealtime()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = SystemClock.elapsedRealtime() - elapsedAtLoad
                val remaining = (cappedInitial - elapsed).coerceAtLeast(0L)
                timeRemaining.value = remaining
                if (remaining <= 0) {
                    endSessionState.value = EndSessionState.Loading
                    repository.endSession(stationId)
                    endSessionState.value = EndSessionState.Success
                    isExpired.value = true
                    break
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

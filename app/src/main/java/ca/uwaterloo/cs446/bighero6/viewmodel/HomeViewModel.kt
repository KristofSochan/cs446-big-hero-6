package ca.uwaterloo.cs446.bighero6.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.repository.FirestoreRepository
import ca.uwaterloo.cs446.bighero6.ui.copy.GuestQueueCopy
import ca.uwaterloo.cs446.bighero6.util.DeviceIdManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
/**
 * Summary of a waitlist for display on home screen
 */
data class WaitlistSummary(
    val stationId: String,
    val stationName: String,
    /** Queue position (1-based when waiting; 0 when the user is in-session / not in the queue). */
    val position: Int,
    val showPositionToGuests: Boolean,
    val estimatedWaitTime: String,
    val isInSession: Boolean,
    val hasActiveSession: Boolean,
    val waitingCount: Int,
    /** True when this user currently has a reservation (has been notified). */
    val hasReservation: Boolean,
    /** True when "notifyHead" is operator-driven (manual notification mode). */
    val isManualNotification: Boolean,
    /** True when an operator manages seating (guests cannot start/end themselves). */
    val operatorManagesSessionsOnly: Boolean,
)

/**
 * Manages home screen state - subscribes to user's waitlists and calculates positions
 */
class HomeViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private var stationListeners = mutableMapOf<String, ListenerRegistration>()
    private var userListener: ListenerRegistration? = null
    private val checkinCountdownJobs = mutableMapOf<String, Job>()

    val waitlists = MutableStateFlow<List<WaitlistSummary>>(emptyList())
    /** Check-in countdown remaining ms by stationId (only when user is head and has reservation). */
    val checkinRemainingByStation = MutableStateFlow<Map<String, Long>>(emptyMap())
    
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
                    checkinCountdownJobs[stationId]?.cancel()
                    checkinCountdownJobs.remove(stationId)
                    checkinRemainingByStation.value = checkinRemainingByStation.value - stationId
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
        val showPosition = station.showPositionToGuests
        val position = if (isInSession) 0 else station.calculatePosition(userId)
        val isTimedMode = station.mode == "timed"
        val hasReservationForMe = station.currentReservation?.userId == userId
        val isManualNotification = station.notificationMode == "manual"
        val operatorManagesSessionsOnly = station.operatorManagesSessionsOnly

        val eta = when {
            !isTimedMode -> ""
            isInSession -> "In session"
            position <= 0 -> ""
            else -> GuestQueueCopy.estimatedWait(
                position = position,
                sessionDurationSeconds = station.sessionDurationSeconds,
                currentSessionExpiresAtMillis =
                    station.currentSession?.expiresAt?.toDate()?.time,
            )
        }
        
        val summary = WaitlistSummary(
            stationId = station.id,
            stationName = station.name,
            position = position,
            showPositionToGuests = showPosition,
            estimatedWaitTime = eta,
            isInSession = isInSession,
            hasActiveSession = hasActiveSession,
            waitingCount = waitingCount,
            hasReservation = hasReservationForMe,
            isManualNotification = isManualNotification,
            operatorManagesSessionsOnly = operatorManagesSessionsOnly,
        )
        waitlists.value = waitlists.value.filter { it.stationId != station.id } + summary

        // Check-in countdown: same pattern as session timer (server initial remaining, then elapsed).
        val isHeadOfQueue = station.calculatePosition(userId) == 1
        val shouldShowCountdown = station.enforceCheckinLimit &&
            hasReservationForMe &&
            isHeadOfQueue &&
            !hasActiveSession
        if (shouldShowCountdown) {
            if (checkinCountdownJobs[station.id]?.isActive != true) {
                checkinCountdownJobs[station.id]?.cancel()
                startCheckinCountdown(station.id, station.checkinWindowSeconds)
            }
        } else {
            checkinCountdownJobs[station.id]?.cancel()
            checkinCountdownJobs.remove(station.id)
            checkinRemainingByStation.value = checkinRemainingByStation.value - station.id
        }
    }

    private fun startCheckinCountdown(stationId: String, checkinWindowSeconds: Int) {
        val durationMs = (checkinWindowSeconds * 1000L).coerceAtLeast(1000L)

        // Provisional: show countdown from full window right away
        val provisionalJob = viewModelScope.launch {
            val startRealtime = SystemClock.elapsedRealtime()
            while (true) {
                delay(100)
                val elapsed = SystemClock.elapsedRealtime() - startRealtime
                val remaining = (durationMs - elapsed).coerceAtLeast(0L)
                checkinRemainingByStation.value =
                    checkinRemainingByStation.value + (stationId to remaining)
                if (remaining <= 0) {
                    checkinRemainingByStation.value =
                        checkinRemainingByStation.value - stationId
                    checkinCountdownJobs.remove(stationId)
                    break
                }
            }
        }
        checkinCountdownJobs[stationId] = provisionalJob

        // Fetch server time and switch to server-based countdown
        viewModelScope.launch {
            var retries = 0
            while (retries < 20) {
                delay(500)
                val result = repository.getReservationTime(stationId)
                val serverInitialMs = result.initialRemainingMs
                if (serverInitialMs != null) {
                    provisionalJob.cancel()
                    val cappedInitial = serverInitialMs.coerceAtMost(durationMs)
                    if (cappedInitial > 0) {
                        val serverJob = viewModelScope.launch {
                            val startRealtime = SystemClock.elapsedRealtime()
                            while (true) {
                                delay(100)
                                val elapsed =
                                    SystemClock.elapsedRealtime() - startRealtime
                                val remaining =
                                    (cappedInitial - elapsed).coerceAtLeast(0L)
                                checkinRemainingByStation.value =
                                    checkinRemainingByStation.value +
                                        (stationId to remaining)
                                if (remaining <= 0) {
                                    checkinRemainingByStation.value =
                                        checkinRemainingByStation.value - stationId
                                    checkinCountdownJobs.remove(stationId)
                                    break
                                }
                            }
                        }
                        checkinCountdownJobs[stationId] = serverJob
                    } else {
                        checkinRemainingByStation.value =
                            checkinRemainingByStation.value - stationId
                        checkinCountdownJobs.remove(stationId)
                    }
                    return@launch
                }
                retries++
            }
        }
    }

    override fun onCleared() {
        userListener?.remove()
        stationListeners.values.forEach { it.remove() }
        checkinCountdownJobs.values.forEach { it.cancel() }
        checkinCountdownJobs.clear()
    }
}

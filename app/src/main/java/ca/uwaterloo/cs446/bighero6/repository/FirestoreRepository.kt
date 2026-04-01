package ca.uwaterloo.cs446.bighero6.repository

import ca.uwaterloo.cs446.bighero6.Constants
import ca.uwaterloo.cs446.bighero6.data.Attendee
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.data.StationHistory
import ca.uwaterloo.cs446.bighero6.data.StationHistoryEvent
import ca.uwaterloo.cs446.bighero6.data.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Handles all Firestore database operations
 * Simple wrapper around Firebase SDK - easy to extend with new queries
 */

/**
 * Result of getSessionTime callable: initial remaining ms for elapsed-only countdown, or null.
 */
data class SessionTimeResult(val initialRemainingMs: Long?)

/**
 * Result of getReservationTime callable: initial remaining ms for check-in countdown, or null.
 */
data class ReservationTimeResult(val initialRemainingMs: Long?)

class FirestoreRepository {
    companion object {
        /**
         * Join or session start failed because [Station.allowMultipleWaitlists] is false
         * and the user is active on another station.
         */
        const val SINGLE_STATION_WAITLIST_POLICY_MESSAGE =
            "This station allows only one active waitlist per guest. " +
                "Please leave your other queues before joining."
    }

    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance(Constants.FUNCTIONS_REGION)

    /**
     * Create or update a station
     */
    suspend fun setStation(station: Station): Result<String> {
        return try {
            val stationId = if (station.id.isEmpty()) {
                UUID.randomUUID().toString()
            } else {
                station.id
            }

            // Populate createdAt if it's null (new station)
            val stationToSave = station.copy(
                id = stationId,
                createdAt = station.createdAt ?: Timestamp.now()
            )

            db.collection("stations")
                .document(stationId)
                .set(stationToSave)
                .await()

            // Initialize empty StationHistory if it doesn't exist
            val historyRef = db.collection("stationAnalytics").document(stationId)
            val historyDoc = historyRef.get().await()
            if (!historyDoc.exists()) {
                val initialHistory = StationHistory(
                    stationID = stationId,
                    ownerID = station.ownerId,
                    history = emptyList()
                )
                historyRef.set(initialHistory).await()
            }

            Result.success(stationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStation(stationId: String): Result<Unit> {
        return try {
            functions
                .getHttpsCallable("deleteStation")
                .call(hashMapOf("stationId" to stationId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get station (includes waitlist data)
     */
    suspend fun getStation(stationId: String): Station? {
        return try {
            val doc = db.collection("stations")
                .document(stationId)
                .get()
                .await()
            
            val station = doc.toObject(Station::class.java)
            // Ensure id field is set from document ID if empty
            station?.let { it.copy(id = it.id.ifEmpty { doc.id }) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Subscribe to real-time station/waitlist updates
     */
    fun subscribeToStation(
        stationId: String,
        onUpdate: (Station?) -> Unit
    ): ListenerRegistration {
        return db.collection("stations")
            .document(stationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                
                val station = snapshot?.toObject(Station::class.java)
                // Ensure id field is set from snapshot ID if empty
                val stationWithId = station?.let { it.copy(id = it.id.ifEmpty { snapshot.id }) }
                onUpdate(stationWithId)
            }
    }

    /**
     * Subscribe to stations owned by a specific user
     */
    fun subscribeToOwnedStations(
        ownerId: String,
        onUpdate: (List<Station>) -> Unit
    ): ListenerRegistration {

        return db.collection("stations")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val stations = snapshot?.documents?.mapNotNull { doc ->
                    val station = doc.toObject(Station::class.java)
                    station?.let { it.copy(id = it.id.ifEmpty { doc.id }) }
                } ?: emptyList()
                onUpdate(stations)
            }
    }

    /**
     * Fetch analytics history for a station.
     * @param days Limit number of days to fetch. If null, fetch all.
     */
    suspend fun getStationAnalytics(
        stationId: String,
        days: Int? = null
    ): Result<StationHistory> {
        return try {
            val doc = db.collection("stationAnalytics")
                .document(stationId)
                .get()
                .await()
            
            val fullHistory = doc.toObject(StationHistory::class.java) 
                ?: return Result.failure(Exception("Analytics history not found"))
            
            val cutoff = if (days != null) {
                Timestamp(Date(System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)))
            } else {
                null
            }
            
            val filteredAndSorted = fullHistory.history.filter { event ->
                val eventTime = event.time
                // Exclude events with null timestamps
                eventTime != null && (cutoff == null || eventTime >= cutoff)
            }.sortedByDescending { event ->
                event.time?.seconds ?: 0L
            }
            
            Result.success(fullHistory.copy(history = filteredAndSorted))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch event history for the current calendar day (since midnight local time).
     */
    suspend fun getStationAnalyticsDaily(
        stationId: String
    ): Result<StationHistory> {
        return try {
            val doc = db.collection("stationAnalytics")
                .document(stationId)
                .get()
                .await()
            
            val fullHistory = doc.toObject(StationHistory::class.java) 
                ?: return Result.failure(Exception("Analytics history not found"))
            
            // Calculate midnight today in local time
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val cutoff = Timestamp(calendar.time)
            
            val filteredAndSorted = fullHistory.history.filter { event ->
                val eventTime = event.time
                eventTime != null && eventTime >= cutoff
            }.sortedByDescending { event ->
                event.time?.seconds ?: 0L
            }
            
            Result.success(fullHistory.copy(history = filteredAndSorted))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add user to waitlist (transaction-safe).
     * Attendees stored as map keyed by userId; joinedAt uses server timestamp.
     */
    suspend fun addToWaitlist(
        stationId: String,
        userId: String,
        form: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val station = transaction.get(stationRef).toObject(Station::class.java)
                    ?: throw IllegalStateException("Station not found")
                val userRef = db.collection("users").document(userId)
                if (!station.allowMultipleWaitlists) {
                    val user = transaction.get(userRef).toObject(User::class.java)
                    val elsewhere = (user?.currentWaitlists ?: emptyList())
                        .filter { it != stationId }
                    if (elsewhere.isNotEmpty()) {
                        throw IllegalStateException(SINGLE_STATION_WAITLIST_POLICY_MESSAGE)
                    }
                }
                val now = Timestamp.now()
                val newAttendee = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "status" to "waiting",
                    "joinedAt" to now
                )
                if (form.isNotEmpty()) {
                    newAttendee["form"] = form
                }
                transaction.update(stationRef, "attendees.$userId", newAttendee)
                transaction.update(userRef, "currentWaitlists", FieldValue.arrayUnion(stationId))

                // Update analytics history log
                val historyRef = db.collection("stationAnalytics").document(stationId)
                val historyEvent = mapOf(
                    "time" to now,
                    "type" to StationHistoryEvent.TYPE_JOIN
                )
                transaction.update(historyRef, "history", FieldValue.arrayUnion(historyEvent))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFromWaitlist(stationId: String, userId: String): Result<Unit> {
        return try {
            // Log LEAVE event BEFORE calling the function to ensure it's captured
            val now = Timestamp.now()
            val historyRef = db.collection("stationAnalytics").document(stationId)
            val historyEvent = mapOf(
                "time" to now,
                "type" to StationHistoryEvent.TYPE_LEAVE
            )
            historyRef.update("history", FieldValue.arrayUnion(historyEvent)).await()

            functions
                .getHttpsCallable("removeFromWaitlist")
                .call(
                    hashMapOf(
                        "stationId" to stationId,
                        "userId" to userId
                    )
                )
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Move attendee to the front of the queue by updating their joinedAt timestamp
     */
    suspend fun moveAttendeeToFront(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val station = transaction.get(stationRef).toObject(Station::class.java)
                    ?: throw IllegalStateException("Station not found")
                station.attendees[userId] ?: throw IllegalStateException("User not in waitlist")
                val firstAttendee = station.attendees.values
                    .filter { it.status == "waiting" }
                    .minByOrNull { it.joinedAt }
                val newTimestamp = if (firstAttendee != null) {
                    Timestamp(Date(firstAttendee.joinedAt.toDate().time - 1000))
                } else {
                    Timestamp.now()
                }
                transaction.update(stationRef, "attendees.$userId.joinedAt", newTimestamp)
                val reservationUserId = station.currentReservation?.userId
                if (reservationUserId != null) {
                    // Determine the new head of the queue after the reorder.
                    val updatedAttendees = station.attendees.toMutableMap()
                    val current = updatedAttendees[userId]
                    if (current != null) {
                        updatedAttendees[userId] = current.copy(joinedAt = newTimestamp)
                    }
                    val newHeadUserId = updatedAttendees.values
                        .filter { it.status == "waiting" }
                        .minByOrNull { it.joinedAt }?.userId

                    // Only clear reservation if the reserved user is no longer head.
                    if (newHeadUserId != reservationUserId) {
                        transaction.update(stationRef, "currentReservation", FieldValue.delete())
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Move attendee to the back of the queue by updating their joinedAt timestamp
     */
    suspend fun moveAttendeeToBack(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val station = transaction.get(stationRef).toObject(Station::class.java)
                    ?: throw IllegalStateException("Station not found")
                station.attendees[userId] ?: throw IllegalStateException("User not in waitlist")
                val lastAttendee = station.attendees.values
                    .filter { it.status == "waiting" }
                    .maxByOrNull { it.joinedAt }
                val newTimestamp = if (lastAttendee != null) {
                    Timestamp(Date(lastAttendee.joinedAt.toDate().time + 1000))
                } else {
                    Timestamp.now()
                }
                transaction.update(stationRef, "attendees.$userId.joinedAt", newTimestamp)
                val reservationUserId = station.currentReservation?.userId
                if (reservationUserId != null) {
                    // Determine the new head of the queue after the reorder.
                    val updatedAttendees = station.attendees.toMutableMap()
                    val current = updatedAttendees[userId]
                    if (current != null) {
                        updatedAttendees[userId] = current.copy(joinedAt = newTimestamp)
                    }
                    val newHeadUserId = updatedAttendees.values
                        .filter { it.status == "waiting" }
                        .minByOrNull { it.joinedAt }?.userId

                    // Only clear reservation if the reserved user is no longer head.
                    if (newHeadUserId != reservationUserId) {
                        transaction.update(stationRef, "currentReservation", FieldValue.delete())
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Notify the current head of the queue and, if check-in enforcement is
     * enabled, start a check-in window for them.
     */
    suspend fun notifyHead(stationId: String): Result<Unit> {
        return try {
            functions
                .getHttpsCallable("notifyHead")
                .call(hashMapOf("stationId" to stationId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user is already in waitlist
     */
    suspend fun isUserInWaitlist(stationId: String, userId: String): Boolean {
        return try {
            val station = getStation(stationId)
            station?.attendees?.containsKey(userId) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Start session for user at position 1
     * - timed mode: Cloud Function sets expiresAt from server time and schedules expiration task
     * - manual mode: session ends only when user taps End Session (no expiresAt)
     */
    suspend fun startSession(
        stationId: String,
        userId: String,
        sessionDurationSeconds: Int = 900,
        mode: String = "manual"
    ): Result<Unit> {
        return try {
            applyStartSessionTx(stationId = stationId, userIdToSeat = userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Operator-driven session start (seating). Uses the same transaction as guest start,
     * but is exposed separately so UI/policy can differ.
     */
    suspend fun startSessionAsOperator(stationId: String, userIdToSeat: String): Result<Unit> {
        return try {
            applyStartSessionTx(stationId = stationId, userIdToSeat = userIdToSeat)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Shared transaction for starting a session for a specific user.
     * - Clears an expired timed session, if present
     * - Throws if a non-expired session is currently active
     * - Sets currentSession.userId; Cloud Function sets startedAt/expiresAt
     * - Removes the user from attendees
     * - Consumes ANY currentReservation (clears it unconditionally)
     */
    private suspend fun applyStartSessionTx(stationId: String, userIdToSeat: String) {
        db.runTransaction { transaction ->
            val stationRef = db.collection("stations").document(stationId)
            val currentStation = transaction.get(stationRef).toObject(Station::class.java)
                ?: throw IllegalStateException("Station not found")

            val userRef = db.collection("users").document(userIdToSeat)
            if (!currentStation.allowMultipleWaitlists) {
                val user = transaction.get(userRef).toObject(User::class.java)
                val elsewhere = (user?.currentWaitlists ?: emptyList())
                    .filter { it != stationId }
                if (elsewhere.isNotEmpty()) {
                    throw IllegalStateException(SINGLE_STATION_WAITLIST_POLICY_MESSAGE)
                }
            }

            var activeSession = currentStation.currentSession
            val now = Timestamp.now()
            if (activeSession?.expiresAt != null &&
                activeSession.expiresAt.seconds < now.seconds
            ) {
                transaction.update(stationRef, "currentSession", null)
                activeSession = null
            } else if (activeSession != null) {
                throw IllegalStateException("Station is currently in use")
            }

            transaction.update(stationRef, "currentSession", mapOf("userId" to userIdToSeat))
            transaction.update(stationRef, "attendees.$userIdToSeat", FieldValue.delete())

            // Unconditionally clear currentReservation when ANY session starts.
            if (currentStation.currentReservation != null) {
                transaction.update(stationRef, "currentReservation", FieldValue.delete())
            }

            // Update analytics history log
            val historyRef = db.collection("stationAnalytics").document(stationId)
            val historyEvent = mapOf(
                "time" to now,
                "type" to StationHistoryEvent.TYPE_START
            )
            transaction.update(historyRef, "history", FieldValue.arrayUnion(historyEvent))
        }.await()
    }
    
    /**
     * Get or create user document
     */
    suspend fun getOrCreateUser(userId: String, name: String? = null, fcmToken: String? = null): User {
        return try {
            val userDoc = db.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (userDoc.exists()) {
                var existingUser = userDoc.toObject(User::class.java)
                // user exists but fcmToken should be updated on firebase
                if (existingUser != null && fcmToken != null &&
                   (existingUser.fcmToken == null || existingUser.fcmToken != fcmToken)){
                    db.collection("users").document(userId).update("fcmToken", fcmToken).await()
                    existingUser = existingUser.copy(fcmToken = fcmToken)
                }

                if (existingUser != null && name != null && existingUser.name != name) {
                    // Update name if provided and different
                    db.collection("users").document(userId).update("name", name).await()
                    existingUser = existingUser.copy(name = name)
                }

                // if existing user does not exist, make one
                existingUser ?: createUserDocument(userId, name, fcmToken)
            } else {
                createUserDocument(userId, name, fcmToken)
            }
        } catch (e: Exception) {
            // Fallback: create user document
            createUserDocument(userId, name, fcmToken)
        }
    }

    /**
     * Subscribe to user document updates
     */
    fun subscribeToUser(userId: String, onUpdate: (User?) -> Unit): ListenerRegistration {
        return db.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.toObject(User::class.java))
            }
    }

    private suspend fun createUserDocument(userId: String, name: String? = null, fcmToken: String? = null): User {
        val user = User(
            id = userId,  // Stored field "id" - mirrors document ID
            fcmToken = fcmToken,
            name = name ?: "",
            currentWaitlists = emptyList()
            // docId will be auto-mapped from document ID when reading
            // createdAt will be set by server via @ServerTimestamp
        )
        
        db.collection("users")
            .document(userId)
            .set(user)
            .await()
        
        // Fetch the document back to get server-set createdAt and auto-mapped docId
        return db.collection("users")
            .document(userId)
            .get()
            .await()
            .toObject(User::class.java) ?: user
    }
    
    /**
     * Update user's FCM token
     */
    suspend fun updateFcmToken(userId: String, fcmToken: String): Result<Unit> {
        return try {
            db.collection("users")
                .document(userId)
                .update("fcmToken", fcmToken)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get session time from server for elapsed-only countdown (avoids client clock skew).
     * Returns initialRemainingMs or null if no timed session.
     */
    suspend fun getSessionTime(stationId: String): SessionTimeResult {
        return try {
            val result = functions
                .getHttpsCallable("getSessionTime")
                .call(hashMapOf("stationId" to stationId))
                .await()
            val data = result.data as? Map<String, Any?> ?: return SessionTimeResult(null)
            val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong()
            val serverTimeMillis = (data["serverTimeMillis"] as? Number)?.toLong()
            if (expiresAtMillis == null || serverTimeMillis == null) {
                return SessionTimeResult(null)
            }
            val initialRemainingMs = (expiresAtMillis - serverTimeMillis).coerceAtLeast(0L)
            SessionTimeResult(initialRemainingMs)
        } catch (e: Exception) {
            SessionTimeResult(null)
        }
    }

    /**
     * Get reservation time from server for check-in countdown (elapsed-only, same as session).
     */
    suspend fun getReservationTime(stationId: String): ReservationTimeResult {
        return try {
            val result = functions
                .getHttpsCallable("getReservationTime")
                .call(hashMapOf("stationId" to stationId))
                .await()
            val data = result.data as? Map<String, Any?> ?: return ReservationTimeResult(null)
            val expiresAtMillis = (data["reservationExpiresAtMillis"] as? Number)?.toLong()
            val serverTimeMillis = (data["serverTimeMillis"] as? Number)?.toLong()
            if (expiresAtMillis == null || serverTimeMillis == null) {
                return ReservationTimeResult(null)
            }
            val initialRemainingMs = (expiresAtMillis - serverTimeMillis).coerceAtLeast(0L)
            ReservationTimeResult(initialRemainingMs)
        } catch (e: Exception) {
            ReservationTimeResult(null)
        }
    }

    suspend fun endSession(stationId: String): Result<Unit> {
        return try {
            functions
                .getHttpsCallable("endSession")
                .call(hashMapOf("stationId" to stationId))
                .await()
            
            // After successful Cloud Function call, log the END event
            val now = Timestamp.now()
            val historyRef = db.collection("stationAnalytics").document(stationId)
            val historyEvent = mapOf(
                "time" to now,
                "type" to StationHistoryEvent.TYPE_END
            )
            historyRef.update("history", FieldValue.arrayUnion(historyEvent)).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

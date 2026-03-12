package ca.uwaterloo.cs446.bighero6.repository

import ca.uwaterloo.cs446.bighero6.data.Attendee
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.data.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Handles all Firestore database operations
 * Simple wrapper around Firebase SDK - easy to extend with new queries
 */
/** Region where Cloud Functions are deployed (must match functions/src). */
private const val FUNCTIONS_REGION = "us-east4"

/**
 * Result of getSessionTime callable: initial remaining ms for elapsed-only countdown, or null.
 */
data class SessionTimeResult(val initialRemainingMs: Long?)

/**
 * Result of getReservationTime callable: initial remaining ms for check-in countdown, or null.
 */
data class ReservationTimeResult(val initialRemainingMs: Long?)

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)

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
            
            Result.success(stationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a station
     */
    suspend fun deleteStation(stationId: String): Result<Unit> {
        return try {
            db.collection("stations")
                .document(stationId)
                .delete()
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
     * Add user to waitlist (transaction-safe).
     * Attendees stored as map keyed by userId; joinedAt uses server timestamp.
     */
    suspend fun addToWaitlist(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val newAttendee = mapOf(
                    "userId" to userId,
                    "status" to "waiting",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
                transaction.update(stationRef, "attendees.$userId", newAttendee)
                val userRef = db.collection("users").document(userId)
                transaction.update(userRef, "currentWaitlists", FieldValue.arrayUnion(stationId))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove user from waitlist (transaction-safe)
     */
    suspend fun removeFromWaitlist(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val stationDoc = transaction.get(stationRef)
                val station = stationDoc.toObject(Station::class.java)
                if (station?.attendees?.containsKey(userId) == true) {
                    transaction.update(stationRef, "attendees.$userId", FieldValue.delete())
                    val userRef = db.collection("users").document(userId)
                    transaction.update(userRef, "currentWaitlists", FieldValue.arrayRemove(stationId))
                }
            }.await()
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
                val firstAttendee = station.attendees.values.minByOrNull { it.joinedAt }
                val newTimestamp = if (firstAttendee != null) {
                    Timestamp(Date(firstAttendee.joinedAt.toDate().time - 1000))
                } else {
                    Timestamp.now()
                }
                transaction.update(stationRef, "attendees.$userId.joinedAt", newTimestamp)
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
                val lastAttendee = station.attendees.values.maxByOrNull { it.joinedAt }
                val newTimestamp = if (lastAttendee != null) {
                    Timestamp(Date(lastAttendee.joinedAt.toDate().time + 1000))
                } else {
                    Timestamp.now()
                }
                transaction.update(stationRef, "attendees.$userId.joinedAt", newTimestamp)
            }.await()
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
            // Get current station to find user entry
            val station = getStation(stationId) ?: return Result.failure(
                IllegalStateException("Station not found")
            )

            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val currentStation = transaction.get(stationRef).toObject(Station::class.java)

                // Check if current session exists and is expired (timed only)
                val currentSession = currentStation?.currentSession
                val now = Timestamp.now()
                if (currentSession?.expiresAt != null &&
                    currentSession.expiresAt.seconds * 1000 < now.seconds * 1000) {
                    transaction.update(stationRef, "currentSession", null)
                } else if (currentSession != null) {
                    throw IllegalStateException("Station is currently in use")
                }

                // Cloud Function sets startedAt and expiresAt from server time
                transaction.update(stationRef, "currentSession", mapOf("userId" to userId))
                transaction.update(stationRef, "attendees.$userId", FieldValue.delete())
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get or create user document
     */
    suspend fun getOrCreateUser(userId: String): User {
        return try {
            val userDoc = db.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (userDoc.exists()) {
                userDoc.toObject(User::class.java) ?: createUserDocument(userId)
            } else {
                createUserDocument(userId)
            }
        } catch (e: Exception) {
            // Fallback: create user document
            createUserDocument(userId)
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
    
    private suspend fun createUserDocument(userId: String): User {
        val user = User(
            id = userId,  // Stored field "id" - mirrors document ID
            fcmToken = null,
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
            @Suppress("UNCHECKED_CAST")
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
            @Suppress("UNCHECKED_CAST")
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

    /**
     * End session - clears currentSession so next person can start,
     * and removes the station from the user's currentWaitlists.
     * If the session was already cleared (e.g. by the server expiration task),
     * returns success so the client doesn't show an error.
     */
    suspend fun endSession(stationId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val currentStation = transaction.get(stationRef).toObject(Station::class.java)
                val userId = currentStation?.currentSession?.userId
                    ?: throw IllegalStateException("No active session to end")

                transaction.update(stationRef, "currentSession", null)

                val userRef = db.collection("users").document(userId)
                transaction.update(userRef, "currentWaitlists", FieldValue.arrayRemove(stationId))
            }.await()

            Result.success(Unit)
        } catch (e: IllegalStateException) {
            if (e.message == "No active session to end") {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

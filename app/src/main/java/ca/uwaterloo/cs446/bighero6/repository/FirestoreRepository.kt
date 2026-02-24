package ca.uwaterloo.cs446.bighero6.repository

import ca.uwaterloo.cs446.bighero6.data.Attendee
import ca.uwaterloo.cs446.bighero6.data.Station
import ca.uwaterloo.cs446.bighero6.data.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Handles all Firestore database operations
 * Simple wrapper around Firebase SDK - easy to extend with new queries
 */
class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    
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
            // Ensure id field is set (if not already stored, use docId which is auto-populated from document ID)
            station?.let { it.copy(id = it.id.ifEmpty { it.docId }) }
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
                // Ensure id field is set (if not already stored, use docId which is auto-populated from document ID)
                val stationWithId = station?.let { it.copy(id = it.id.ifEmpty { it.docId }) }
                onUpdate(stationWithId)
            }
    }

    /**
     * Subscribe to stations owned by a specific user
     */
    fun subscribeToOwnedStations(
        creatorId: String,
        onUpdate: (List<Station>) -> Unit
    ): ListenerRegistration {

        return db.collection("stations")
            .whereEqualTo("creatorId", creatorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val stations = snapshot?.documents?.mapNotNull { doc ->
                    val station = doc.toObject(Station::class.java)
                    station?.let { it.copy(id = it.id.ifEmpty { it.docId }) }
                } ?: emptyList()
                onUpdate(stations)
            }
    }
    
    /**
     * Add user to waitlist (transaction-safe)
     */
    suspend fun addToWaitlist(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                
                val newAttendee = Attendee(
                    userId = userId,
                    status = "waiting",
                    joinedAt = Timestamp.now()
                )
                
                // Add to attendees array
                transaction.update(stationRef, "attendees", FieldValue.arrayUnion(newAttendee))
                
                // Update user's waitlists
                val userRef = db.collection("users").document(userId)
                transaction.update(userRef, "currentWaitlists", FieldValue.arrayUnion(stationId))
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
            station?.attendees?.any { it.userId == userId } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Start session for user at position 1
     * - timed mode: session auto-expires after sessionDurationSeconds (Cloud Task scheduled)
     * - manual mode: session ends only when user taps End Session (no expiresAt)
     */
    suspend fun startSession(
        stationId: String,
        userId: String,
        sessionDurationSeconds: Int = 900,
        mode: String = "manual"
    ): Result<Unit> {
        return try {
            val isTimed = mode == "timed"
            val expiresAt = if (isTimed) {
                Timestamp(Date(System.currentTimeMillis() + sessionDurationSeconds * 1000L))
            } else {
                null
            }

            // Get current station to find user entry
            val station = getStation(stationId) ?: return Result.failure(
                IllegalStateException("Station not found")
            )

            val userEntry = station.attendees.find { it.userId == userId }
                ?: return Result.failure(IllegalStateException("User not in waitlist"))

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

                val sessionMap = mutableMapOf<String, Any?>(
                    "userId" to userId,
                    "startedAt" to Timestamp.now()
                )
                sessionMap["expiresAt"] = expiresAt
                transaction.update(stationRef, "currentSession", sessionMap)

                transaction.update(stationRef, "attendees", FieldValue.arrayRemove(userEntry))
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
     * End session - clears currentSession so next person can start,
     * and removes the station from the user's currentWaitlists.
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

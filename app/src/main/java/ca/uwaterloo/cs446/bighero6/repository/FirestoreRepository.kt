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
import java.util.UUID

/**
 * Handles all Firestore database operations
 * Simple wrapper around Firebase SDK - easy to extend with new queries
 */
class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

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
     * Remove user from waitlist (transaction-safe)
     */
    suspend fun removeFromWaitlist(stationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val stationRef = db.collection("stations").document(stationId)
                val stationDoc = transaction.get(stationRef)
                val station = stationDoc.toObject(Station::class.java)
                
                val attendeeToRemove = station?.attendees?.find { it.userId == userId }
                
                if (attendeeToRemove != null) {
                    // Remove from attendees array
                    transaction.update(stationRef, "attendees", FieldValue.arrayRemove(attendeeToRemove))
                    
                    // Update user's waitlists
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
                
                val attendees = station.attendees.toMutableList()
                val attendeeIndex = attendees.indexOfFirst { it.userId == userId }
                if (attendeeIndex == -1) throw IllegalStateException("User not in waitlist")
                
                val firstAttendee = attendees.minByOrNull { it.joinedAt }
                val newTimestamp = if (firstAttendee != null) {
                    Timestamp(Date(firstAttendee.joinedAt.toDate().time - 1000))
                } else {
                    Timestamp.now()
                }
                
                val updatedAttendee = attendees[attendeeIndex].copy(joinedAt = newTimestamp)
                attendees[attendeeIndex] = updatedAttendee
                
                transaction.update(stationRef, "attendees", attendees)
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
                
                val attendees = station.attendees.toMutableList()
                val attendeeIndex = attendees.indexOfFirst { it.userId == userId }
                if (attendeeIndex == -1) throw IllegalStateException("User not in waitlist")
                
                val lastAttendee = attendees.maxByOrNull { it.joinedAt }
                val newTimestamp = if (lastAttendee != null) {
                    Timestamp(Date(lastAttendee.joinedAt.toDate().time + 1000))
                } else {
                    Timestamp.now()
                }
                
                val updatedAttendee = attendees[attendeeIndex].copy(joinedAt = newTimestamp)
                attendees[attendeeIndex] = updatedAttendee
                
                transaction.update(stationRef, "attendees", attendees)
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
    suspend fun getOrCreateUser(userId: String, displayName: String = ""): User {
        return try {
            val userDoc = db.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (userDoc.exists()) {
                val existingUser = userDoc.toObject(User::class.java)
                if (existingUser != null) {
                    existingUser
                } else {
                    createUserDocument(userId, displayName)
                }
            } else {
                createUserDocument(userId, displayName)
            }
        } catch (e: Exception) {
            // Fallback: create user document
            createUserDocument(userId, displayName)
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

    /**
     * Get a user by ID
     */
    suspend fun getUser(userId: String): User? {
        return try {
            db.collection("users")
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get multiple users by ID
     */
    suspend fun getUsers(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            // Firestore 'in' query has a limit of 10 items. For simplicity, we assume small batches or just fetch individually if needed.
            // For a production app, we would chunk this.
            db.collection("users")
                .whereIn("id", userIds.take(10))
                .get()
                .await()
                .toObjectList(User::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun createUserDocument(userId: String, displayName: String = ""): User {
        val user = User(
            id = userId,  // Stored field "id" - mirrors document ID
            displayName = displayName,
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

    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> {
        return try {
            db.collection("users")
                .document(userId)
                .update("displayName", displayName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

// Helper extension for list conversion
fun <T> com.google.firebase.firestore.QuerySnapshot.toObjectList(clazz: Class<T>): List<T> {
    return documents.mapNotNull { it.toObject(clazz) }
}

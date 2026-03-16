package ca.uwaterloo.cs446.bighero6.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object DeviceIdManager {
    private const val PREFS_NAME = "taplist_prefs"
    private const val KEY_USER_ID = "user_id"

    /**
     * Get the current user ID.
     * Prioritizes Firebase Auth UID if signed in.
     * Falls back to a locally stored UUID for guest access or legacy support.
     */
    fun getUserId(context: Context): String {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            return firebaseUser.uid
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUserId = prefs.getString(KEY_USER_ID, null)

        if (storedUserId != null) return storedUserId

        // ONLY use UUID. Never touch ANDROID_ID.
        val newUserId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newUserId).apply()
        return newUserId
    }

    /**
     * Reset user ID - generates a new UUID (for testing with multiple emulators)
     */
    fun resetUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newUserId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newUserId).apply()
        return newUserId
    }
}

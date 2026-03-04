package ca.uwaterloo.cs446.bighero6.util

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdManager {
    private const val PREFS_NAME = "taplist_prefs"
    private const val KEY_USER_ID = "user_id"

    // COMMENTED OUT BECAUSE MAY BE UNSAFE
//    /**
//     * Get or create a unique user ID for this device.
//     *
//     * For emulators: Always uses UUID (each emulator instance gets a unique UUID)
//     * For real devices: Uses Android ID if available, otherwise UUID
//     *
//     * The ID is stored in SharedPreferences and persists across app restarts.
//     * Each device/emulator instance will have a different ID by default.
//     */
//    fun getUserId(context: Context): String {
//        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//
//        // Check if we already have a stored user ID
//        val storedUserId = prefs.getString(KEY_USER_ID, null)
//        if (storedUserId != null) {
//            return storedUserId
//        }
//
//        // Generate new user ID
//        val androidId = Settings.Secure.getString(
//            context.contentResolver,
//            Settings.Secure.ANDROID_ID
//        )
//
//        // For emulators (which often share the same Android ID), always use UUID
//        // This ensures each emulator instance gets a unique ID
//        val userId = if (androidId != null && androidId != "9774d56d682e549c") {
//            // Real device with unique Android ID
//            androidId
//        } else {
//            // Emulator or device without Android ID - use UUID
//            // Each emulator instance will generate a different UUID
//            UUID.randomUUID().toString()
//        }
//
//        // Store for future use
//        prefs.edit().putString(KEY_USER_ID, userId).apply()
//
//        return userId
//    }

    // Checks for existing user id, else creates a new one
    // If android id already exists from a prior app, it does NOT use it
    fun getUserId(context: Context): String {
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

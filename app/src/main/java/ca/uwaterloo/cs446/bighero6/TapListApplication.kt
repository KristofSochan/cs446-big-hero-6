package ca.uwaterloo.cs446.bighero6

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class TapListApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Android Emulator: host machine’s localhost is 10.0.2.2.
            // Physical device: use your machine’s LAN IP and the same ports.
            val host = "10.0.2.2"
            FirebaseFirestore.getInstance().useEmulator(host, 8080)
            FirebaseFunctions.getInstance(Constants.FUNCTIONS_REGION).useEmulator(host, 5001)
        }
    }
}

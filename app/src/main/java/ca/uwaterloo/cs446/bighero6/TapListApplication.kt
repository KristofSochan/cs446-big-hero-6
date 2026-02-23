package ca.uwaterloo.cs446.bighero6

import android.app.Application
import com.google.firebase.FirebaseApp

class TapListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Firebase is automatically initialized when google-services.json is present
        // But we can verify it's initialized
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}

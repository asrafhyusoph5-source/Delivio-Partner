package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.osmdroid.config.Configuration

class DelivioApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure osmdroid user agent for OpenStreetMap tile fetching
        try {
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            Log.w("DelivioApp", "Failed to configure osmdroid userAgent", e)
        }

        // Initialize FirebaseApp safely in environments without pre-generated google-services.json
        if (FirebaseApp.getApps(this).isEmpty()) {
            try {
                FirebaseApp.initializeApp(this)
            } catch (e: Exception) {
                Log.w("DelivioApp", "FirebaseApp default initialization with resources not available: ${e.message}")
            }

            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:102938475610:android:a1b2c3d4e5f6g7h8i9j0")
                        .setProjectId("delivio-partner")
                        .setApiKey("AIzaSyDummyKeyForPreviewAndLocalTesting00")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.i("DelivioApp", "FirebaseApp successfully initialized with fallback options for preview")
                } catch (e2: Exception) {
                    Log.e("DelivioApp", "Failed to initialize fallback FirebaseApp", e2)
                }
            }
        }
    }
}

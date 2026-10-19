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
                        .setApplicationId("1:490983557590:android:a1f1b3c89045b584467c77")
                        .setProjectId("delivio-371d3")
                        .setApiKey("AIzaSyB4ztWjU56WNWX9UKoKC1Icrm5eXxdMxjU")
                        .setStorageBucket("delivio-371d3.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.i("DelivioApp", "FirebaseApp initialized with project delivio-371d3 options")
                } catch (e2: Exception) {
                    Log.e("DelivioApp", "Failed to initialize FirebaseApp with options", e2)
                }
            }
        }
    }
}

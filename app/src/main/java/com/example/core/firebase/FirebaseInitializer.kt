package com.example.core.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    const val DEFAULT_WEB_CLIENT_ID = "1054603569158-q8iq16i14e9ct76gn8esco8k2u1uqos5.apps.googleusercontent.com"
    private const val PROJECT_ID = "gen-lang-client-0425978486"
    private const val APPLICATION_ID = "1:1054603569158:android:ce8ae743f2d796bff93b87"
    private const val API_KEY = "AIzaSyAiaWmxB4ZiEF8V-nkFKfuUCCXdqgZSPAE"
    private const val GCM_SENDER_ID = "1054603569158"
    private const val STORAGE_BUCKET = "gen-lang-client-0425978486.firebasestorage.app"

    fun ensureInitialized(context: Context): FirebaseApp? {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return FirebaseApp.getInstance()
        }
        return try {
            FirebaseApp.initializeApp(appContext) ?: initializeWithOptions(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Standard FirebaseApp.initializeApp failed, falling back to manual options: ${e.message}")
            initializeWithOptions(appContext)
        }
    }

    private fun initializeWithOptions(context: Context): FirebaseApp? {
        return try {
            val options = FirebaseOptions.Builder()
                .setApplicationId(APPLICATION_ID)
                .setApiKey(API_KEY)
                .setProjectId(PROJECT_ID)
                .setGcmSenderId(GCM_SENDER_ID)
                .setStorageBucket(STORAGE_BUCKET)
                .build()
            FirebaseApp.initializeApp(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseApp with manual options", e)
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                null
            }
        }
    }

    fun getWebClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val str = context.getString(resId)
                if (str.isNotBlank()) str else DEFAULT_WEB_CLIENT_ID
            } else {
                DEFAULT_WEB_CLIENT_ID
            }
        } catch (_: Exception) {
            DEFAULT_WEB_CLIENT_ID
        }
    }

    fun getAuth(context: Context): FirebaseAuth? {
        ensureInitialized(context)
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FirebaseAuth instance", e)
            null
        }
    }

    fun getFirestore(context: Context, databaseId: String? = null): FirebaseFirestore? {
        val app = ensureInitialized(context) ?: return null
        return try {
            if (!databaseId.isNullOrBlank()) {
                FirebaseFirestore.getInstance(app, databaseId)
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to get FirebaseFirestore instance", e2)
                null
            }
        }
    }

    fun configureAppCheck(context: Context, intent: android.content.Intent? = null) {
        try {
            ensureInitialized(context)
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            val debugFactory = com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            appCheck.installAppCheckProviderFactory(debugFactory)
        } catch (e: Exception) {
            Log.d(TAG, "AppCheck configuration note: ${e.message}")
        }
    }
}

package com.example.auth

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Configures Firebase App Check before any Firestore or Auth calls run.
 *
 * - Debug builds: Installs [DebugAppCheckProviderFactory] and optionally injects a deterministic
 *   debug token from the launch intent (`FIREBASE_APPCHECK_DEBUG_TOKEN`) or `BuildConfig`
 *   using synchronous `.commit()` so the provider reads it immediately.
 * - Release builds: Installs [PlayIntegrityAppCheckProviderFactory] for Play Integrity attestation.
 *
 * Note: Remember to enable App Check enforcement for Cloud Firestore in the Firebase Console
 * once your production SHA-256 fingerprint is registered.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    if (!GoogleSignInManager.isFirebaseConfigured(context)) {
        // Skip App Check installation until a real google-services.json is placed in app/
        return
    }

    runCatching {
        val firebaseApp = FirebaseApp.getInstance()
        if (BuildConfig.DEBUG) {
            val intentSecret = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")?.trim()
            val buildConfigSecret = runCatching {
                BuildConfig::class.java.getDeclaredField("FIREBASE_APPCHECK_DEBUG_TOKEN")
                    .get(null) as? String
            }.getOrNull()?.trim()

            val debugSecret = when {
                !intentSecret.isNullOrEmpty() -> intentSecret
                !buildConfigSecret.isNullOrEmpty() && buildConfigSecret != "YOUR_APPCHECK_DEBUG_TOKEN" -> buildConfigSecret
                else -> null
            }

            if (!debugSecret.isNullOrEmpty()) {
                val persistenceKey = firebaseApp.persistenceKey
                val prefsName = "com.google.firebase.appcheck.debug.store.$persistenceKey"
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugSecret)
                    .commit() // Synchronous commit required before installing DebugAppCheckProviderFactory
            }

            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}

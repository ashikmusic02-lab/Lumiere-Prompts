package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleSignInManager {
    private const val TAG = "Auth"

    fun isFirebaseConfigured(context: Context): Boolean {
        return runCatching {
            val apps = FirebaseApp.getApps(context.applicationContext)
            if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext) != null
            } else {
                true
            }
        }.getOrDefault(false)
    }

    fun isWebClientIdConfigured(context: Context): Boolean {
        return runCatching {
            val clientId = context.getString(R.string.default_web_client_id)
            clientId.isNotBlank() && !clientId.startsWith("YOUR_GOOGLE_WEB_CLIENT_ID")
        }.getOrDefault(false)
    }

    /**
     * Returns true only when the user is signed in with a real non-anonymous provider (Google).
     * Likes, comments, and comment reports require a real authenticated account.
     */
    fun isRealAuthenticatedUser(user: FirebaseUser?): Boolean {
        return user != null && !user.isAnonymous
    }

    /**
     * Ensures that a Firebase Auth session (anonymous guest or existing user) is active before
     * any Firestore work runs. If `Firebase.auth.currentUser` is null, calls `signInAnonymously()`.
     */
    suspend fun ensureAnonymousSignInIfNeeded(context: Context): FirebaseUser? {
        if (!isFirebaseConfigured(context)) return null
        val existing = Firebase.auth.currentUser
        if (existing != null) return existing
        return try {
            Firebase.auth.signInAnonymously().await().user
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous sign-in failed (ensure Anonymous provider is enabled in Firebase Console): ${e.message}")
            null
        }
    }

    /**
     * Links an existing anonymous Firebase session to the newly acquired Google credential
     * via `linkWithCredential(credential)` so guest favorites and prompt unlocks are preserved.
     * Gracefully handles credential-already-in-use collisions by signing in with the credential.
     */
    private suspend fun signInOrLinkCredential(credential: AuthCredential) {
        val current = Firebase.auth.currentUser
        if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(credential).await()
                return
            } catch (_: FirebaseAuthUserCollisionException) {
                Log.i(TAG, "Google credential already linked to an existing account; switching to existing account.")
            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "linkWithCredential error (${e.errorCode}); falling back to signInWithCredential.")
            } catch (e: Exception) {
                Log.w(TAG, "linkWithCredential fallback to signInWithCredential: ${e.message}")
            }
        }
        Firebase.auth.signInWithCredential(credential).await()
    }

    fun authStateFlow(context: Context): Flow<FirebaseUser?> = callbackFlow {
        if (!isFirebaseConfigured(context)) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val auth = Firebase.auth
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // 1. Automatic Anonymous Sign-In + Silent Google Auto-Sign-In on Startup
    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (!isFirebaseConfigured(context)) {
            onUnauthenticated()
            return
        }
        scope.launch {
            // Ensure an anonymous session exists immediately if currentUser is null
            ensureAnonymousSignInIfNeeded(context)

            if (isRealAuthenticatedUser(Firebase.auth.currentUser)) {
                onAuthSuccess()
                return@launch
            }
            if (!isWebClientIdConfigured(context)) {
                onUnauthenticated()
                return@launch
            }
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                onUnauthenticated()
                return@launch
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    signInOrLinkCredential(GoogleAuthProvider.getCredential(token, null))
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    // 2. Interactive Google Sign-In (Exactly ONE option: GetSignInWithGoogleOption)
    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        if (!isFirebaseConfigured(context) || !isWebClientIdConfigured(context)) {
            onAuthError(
                "Google Sign-In awaits your google-services.json file in app/. You can continue browsing as a guest meanwhile."
            )
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val activity = context as? Activity
                    ?: throw IllegalStateException("Activity context required for interactive Google Sign-In")
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    signInOrLinkCredential(GoogleAuthProvider.getCredential(token, null))
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned from Google Sign-In")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    // 3. Safe Sign-Out Flow inside NonCancellable (re-establishes anonymous guest session after sign-out)
    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    if (isFirebaseConfigured(context)) {
                        runCatching { Firebase.auth.signOut() }
                        runCatching { Firebase.auth.signInAnonymously().await() }
                    }
                    onSignOutComplete()
                }
            }
        }
    }
}

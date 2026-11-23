package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object GoogleSignInManager {
    private const val TAG = "Auth"
    private const val ANONYMOUS_AUTH_TIMEOUT_MS = 6_000L

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

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
     * Silently restores an existing Firebase Auth session (Google or anonymous) or establishes
     * an anonymous guest session in the background with a timeout so it never blocks the UI.
     * NEVER triggers any Credential Manager / Google One Tap / account chooser popup.
     */
    suspend fun ensureAnonymousSignInIfNeeded(context: Context): FirebaseUser? = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured(context)) return@withContext null
        val existing = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (existing != null) return@withContext existing
        return@withContext try {
            withTimeoutOrNull(ANONYMOUS_AUTH_TIMEOUT_MS) {
                Firebase.auth.signInAnonymously().await().user
            }
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous sign-in skipped: ${e.message}")
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

    /**
     * Interactive Google Sign-In invoked ONLY when the user explicitly taps Like, Comment,
     * or the Sign in button in Profile.
     * If the user cancels or dismisses the sheet, returns silently with no error message.
     */
    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        if (!isFirebaseConfigured(context) || !isWebClientIdConfigured(context)) {
            onAuthError(context.getString(R.string.prompt_generic_error))
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError(context.getString(R.string.prompt_generic_error))
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val activity = findActivity(context)
                    ?: throw IllegalStateException("Activity context required for interactive Google Sign-In")
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    signInOrLinkCredential(GoogleAuthProvider.getCredential(token, null))
                    onAuthSuccess()
                } else {
                    onAuthCancelled()
                }
            } catch (_: GetCredentialCancellationException) {
                // User cancelled the sign-in sheet: return silently without any error message
                onAuthCancelled()
            } catch (_: GetCredentialInterruptedException) {
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google account available on device: ${e.message}")
                onAuthError(context.getString(R.string.sign_in_required_body))
            } catch (e: Exception) {
                val msg = e.message.orEmpty().lowercase()
                if (msg.contains("cancel") || msg.contains("dismiss") || msg.contains("user_canceled")) {
                    onAuthCancelled()
                } else {
                    Log.e(TAG, "Google Sign-In failed", e)
                    val isNetworkError = generateSequence<Throwable>(e) { it.cause }.any { cause ->
                        cause is FirebaseNetworkException || cause is IOException
                    } || msg.contains("network") || msg.contains("timeout") || msg.contains("unavailable")
                    val friendlyMsg = if (isNetworkError) {
                        context.getString(R.string.turn_on_internet_message)
                    } else {
                        context.getString(R.string.prompt_generic_error)
                    }
                    onAuthError(friendlyMsg)
                }
            }
        }
    }

    // Safe Sign-Out Flow inside NonCancellable (re-establishes anonymous guest session after sign-out)
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
                        runCatching {
                            withTimeoutOrNull(ANONYMOUS_AUTH_TIMEOUT_MS) {
                                Firebase.auth.signInAnonymously().await()
                            }
                        }
                    }
                    onSignOutComplete()
                }
            }
        }
    }
}

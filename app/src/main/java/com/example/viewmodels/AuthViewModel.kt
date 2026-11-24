package com.example.viewmodels

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.AccountType
import com.example.data.util.PendingRegistrationDraft
import com.example.data.util.PreferencesManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class AuthMode {
    LOGIN,
    REGISTRATION
}

sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data class Authenticated(val user: FirebaseUser) : AuthSessionState
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val fullName: String = "",
    val phone: String = "",
    val city: String = "",
    val accountType: AccountType = AccountType.BOTH,
    val isPrivacyDialogVisible: Boolean = false,
    val isAccountRecoveryDialogVisible: Boolean = false,
    val isEmailPasswordSupported: Boolean = false,
    val currentUserId: String? = null,
    val currentUserEmail: String? = null,
    val currentUserName: String? = null,
    val isEmailVerified: Boolean = false
)

class AuthViewModel(
    private val prefs: PreferencesManager,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = _currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser != null)

    private val _sessionState = MutableStateFlow(
        auth.currentUser?.let { AuthSessionState.Authenticated(it) } ?: AuthSessionState.Unauthenticated
    )
    val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _uiState = MutableStateFlow(
        AuthUiState(
            currentUserId = auth.currentUser?.uid,
            currentUserEmail = auth.currentUser?.email,
            currentUserName = auth.currentUser?.displayName,
            isEmailVerified = auth.currentUser?.isEmailVerified ?: false
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var autoSignInAttempted = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUser.value = user
        _sessionState.value = if (user != null) {
            AuthSessionState.Authenticated(user)
        } else {
            AuthSessionState.Unauthenticated
        }
        _isLoading.value = false
        _uiState.update {
            it.copy(
                currentUserId = user?.uid,
                currentUserEmail = user?.email,
                currentUserName = user?.displayName,
                isEmailVerified = user?.isEmailVerified ?: false,
                isLoading = false
            )
        }
        if (user == null) {
            autoSignInAttempted = false
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    private fun setLoading(loading: Boolean) {
        _isLoading.value = loading
        _uiState.update { it.copy(isLoading = loading) }
    }

    private fun setError(message: String?) {
        _errorMessage.value = message
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun navigateToRegister() {
        setError(null)
        _uiState.update { it.copy(mode = AuthMode.REGISTRATION, statusMessage = null) }
    }

    fun navigateToLogin() {
        setError(null)
        _uiState.update { it.copy(mode = AuthMode.LOGIN, statusMessage = null) }
    }

    fun setPrivacyDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isPrivacyDialogVisible = visible) }
    }

    fun setAccountRecoveryDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isAccountRecoveryDialogVisible = visible) }
    }

    fun clearError() {
        setError(null)
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun updateFullName(value: String) {
        _uiState.update { it.copy(fullName = value) }
    }

    fun updatePhone(value: String) {
        _uiState.update { it.copy(phone = value) }
    }

    fun updateCity(value: String) {
        _uiState.update { it.copy(city = value) }
    }

    fun updateAccountType(type: AccountType) {
        _uiState.update { it.copy(accountType = type) }
    }

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || !email.contains("@")) {
            setError("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            setError("Password must be at least 6 characters.")
            return
        }
        setLoading(false)
        setError(
            "Email/password sign-in is not enabled on this backend. Please use Google Sign-In or enable the Email/Password provider in the Firebase Console."
        )
    }

    fun registerWithEmail(email: String, password: String, fullName: String = "") {
        if (email.isBlank() || !email.contains("@")) {
            setError("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            setError("Password must be at least 6 characters.")
            return
        }
        if (fullName.isNotBlank()) {
            _uiState.update { it.copy(fullName = fullName.trim()) }
        }
        setLoading(false)
        setError(
            "Email/password registration is not enabled on this backend. Please continue with Google Sign-In or enable the Email/Password provider in the Firebase Console."
        )
    }

    fun openGoogleAccountRecovery(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://accounts.google.com/signin/recovery")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(intent)
        }.isSuccess
        if (launched) {
            _uiState.update {
                it.copy(
                    isAccountRecoveryDialogVisible = false,
                    statusMessage = "Opened Google Account Recovery in browser."
                )
            }
        } else {
            setError("No browser found to open Google Account Recovery.")
        }
    }

    fun attemptAutoSignIn(context: Context) {
        if (autoSignInAttempted || auth.currentUser != null) return
        autoSignInAttempted = true

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent auto sign-in fallback keeps interactive LoginScreen visible
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        launchGoogleSignIn(context = context, saveRegistrationDraft = false)
    }

    fun registerWithGoogle(context: Context) {
        launchGoogleSignIn(context = context, saveRegistrationDraft = true)
    }

    private fun launchGoogleSignIn(context: Context, saveRegistrationDraft: Boolean) {
        if (_isLoading.value) return

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            setLoading(false)
            setError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }

        val activity = context as? Activity
        if (activity == null) {
            setLoading(false)
            setError("Unable to launch sign-in without an active Activity.")
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        setError(null)
        _uiState.update { it.copy(statusMessage = null) }
        setLoading(true)

        viewModelScope.launch {
            try {
                if (saveRegistrationDraft) {
                    val current = _uiState.value
                    prefs.savePendingRegistration(
                        PendingRegistrationDraft(
                            fullName = current.fullName,
                            phone = current.phone,
                            city = current.city,
                            accountType = current.accountType
                        )
                    )
                }

                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    setLoading(false)
                    setError(null)
                } else {
                    setLoading(false)
                    setError("Unexpected credential type returned.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                setLoading(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                setLoading(false)
                setError(e.localizedMessage ?: "Sign in failed. Please try again.")
            }
        }
    }

    fun signOut(context: Context, onSignOutComplete: () -> Unit = {}) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    _isLoading.value = false
                    _errorMessage.value = null
                    _uiState.update { AuthUiState(mode = AuthMode.LOGIN) }
                    onSignOutComplete()
                }
            }
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}

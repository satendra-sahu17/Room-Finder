package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodels.AuthMode
import com.example.viewmodels.AuthViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.mode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "auth_flow_transition"
    ) { target ->
        when (target) {
            AuthMode.LOGIN -> LoginScreen(
                uiState = uiState,
                onAttemptAutoSignIn = { authViewModel.attemptAutoSignIn(context) },
                onGoogleSignInClick = { authViewModel.signInWithGoogle(context) },
                onNavigateToRegister = { authViewModel.navigateToRegister() },
                onShowPrivacyDialog = { authViewModel.setPrivacyDialogVisible(it) },
                onShowRecoveryDialog = { authViewModel.setAccountRecoveryDialogVisible(it) },
                onOpenGoogleRecovery = { authViewModel.openGoogleAccountRecovery(context) }
            )
            AuthMode.REGISTRATION -> RegistrationScreen(
                uiState = uiState,
                onFullNameChange = { authViewModel.updateFullName(it) },
                onPhoneChange = { authViewModel.updatePhone(it) },
                onCityChange = { authViewModel.updateCity(it) },
                onAccountTypeChange = { authViewModel.updateAccountType(it) },
                onRegisterClick = { authViewModel.registerWithGoogle(context) },
                onNavigateToLogin = { authViewModel.navigateToLogin() }
            )
        }
    }
}

package com.firkat.fillin.feature.auth.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    state: AuthUiState,
    viewModel: AuthViewModel,
    googleSignIn: GoogleSignInLauncher,
    snackbarHostState: SnackbarHostState,
    onContinueAsGuest: () -> Unit,
    onContinueWithAccount: () -> Unit,
) {
    GoogleSignInEffect(viewModel, googleSignIn)
    val scope = rememberCoroutineScope()
    val emailUnavailable = stringResource(R.string.auth_email_unavailable)
    AuthContent(
        modifier = Modifier.testTag("auth_screen"),
        snackBarHostState = snackbarHostState,
        isLoading = state.busy || state.session == AuthSession.Loading,
        googleEnabled = state.session != AuthSession.Unavailable,
        onGoogleClick = if (state.session is AuthSession.Authenticated) onContinueWithAccount else viewModel::signIn,
        onEmailClick = {
            scope.launch {
                snackbarHostState.showSaldomiSnackbar(emailUnavailable)
            }
        },
        onGuestClick = onContinueAsGuest,
    )
}

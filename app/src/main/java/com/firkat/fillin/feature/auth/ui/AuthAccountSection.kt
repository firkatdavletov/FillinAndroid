package com.firkat.fillin.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerProfileState
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthAccountSection(
    viewModel: AuthViewModel = koinViewModel(),
    googleSignIn: GoogleSignInLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelCredentialRequest() }
    }
    LaunchedEffect(viewModel, googleSignIn, context) {
        viewModel.googleSignInRequests.collect { request ->
            if (!viewModel.isCredentialRequestActive(request.id)) return@collect
            try {
                viewModel.credentialReceived(request.id, googleSignIn.launch(context))
            } catch (_: GetCredentialCancellationException) {
                viewModel.credentialCancelled(request.id)
            } catch (cancelled: CancellationException) {
                // Leaving this screen or rotating cancels the picker, without replaying it.
                viewModel.credentialCancelled(request.id)
                throw cancelled
            } catch (_: NoCredentialException) {
                viewModel.credentialFailed(request.id, AuthError.NoGoogleAccount)
            } catch (failure: AuthFailure) {
                viewModel.credentialFailed(request.id, failure.error)
            } catch (_: Exception) {
                viewModel.credentialFailed(request.id, AuthError.SignInFailed)
            }
        }
    }
    AuthAccountContent(state, viewModel::signIn, viewModel::signOut, viewModel::retryServerAuthentication)
}

@Composable
fun AuthAccountContent(
    state: AuthUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetryServer: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.auth_account_title), style = MaterialTheme.typography.titleMedium)
            when (val session = state.session) {
                AuthSession.Loading -> CircularProgressIndicator()
                AuthSession.Unavailable, AuthSession.Anonymous -> {
                    Text(stringResource(R.string.auth_local_mode))
                    Text(stringResource(R.string.auth_local_hint))
                    Button(onClick = onSignIn, enabled = !state.busy && session != AuthSession.Unavailable) {
                        Text(stringResource(R.string.auth_google_sign_in))
                    }
                    if (session == AuthSession.Unavailable) {
                        Text(stringResource(R.string.auth_not_configured))
                    }
                }
                is AuthSession.Authenticated -> {
                    Text(session.user.displayName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.auth_google_account))
                    session.user.email?.takeIf(String::isNotBlank)?.let { Text(it) }
                    Text(stringResource(R.string.auth_budget_hint))
                    ServerProfileContent(state.serverProfile, onRetryServer)
                    OutlinedButton(onClick = onSignOut, enabled = !state.busy) {
                        Text(stringResource(R.string.auth_sign_out))
                    }
                }
            }
            if (state.busy) {
                CircularProgressIndicator()
                Text(stringResource(R.string.auth_busy))
            }
            state.error?.let { reason ->
                Text(stringResource(reason.messageResource()), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ServerProfileContent(state: ServerProfileState, onRetry: () -> Unit) {
    when (state) {
        ServerProfileState.Idle -> Unit
        ServerProfileState.Loading -> Text(stringResource(R.string.auth_server_loading))
        is ServerProfileState.Ready -> Text(stringResource(R.string.auth_server_ready))
        is ServerProfileState.Failed -> {
            val message = when (state.reason) {
                ServerAuthError.NotConfigured -> R.string.auth_server_not_configured
                ServerAuthError.Network -> R.string.auth_server_network_error
                ServerAuthError.Unavailable -> R.string.auth_server_unavailable
                ServerAuthError.Unauthorized -> R.string.auth_server_unauthorized
                ServerAuthError.SessionChanged -> R.string.auth_server_session_changed
            }
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            if (state.reason != ServerAuthError.NotConfigured) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.auth_server_retry)) }
            }
        }
    }
}

private fun AuthError.messageResource(): Int = when (this) {
    AuthError.NotConfigured -> R.string.auth_not_configured
    AuthError.NoGoogleAccount -> R.string.auth_no_google_account
    AuthError.Network -> R.string.auth_network_error
    AuthError.InvalidCredential -> R.string.auth_invalid_credential
    AuthError.SignInUnavailable -> R.string.auth_sign_in_unavailable
    AuthError.SignInFailed -> R.string.auth_sign_in_failed
    AuthError.SignOutFailed -> R.string.auth_sign_out_failed
    AuthError.CredentialResetFailed -> R.string.auth_credential_reset_failed
}

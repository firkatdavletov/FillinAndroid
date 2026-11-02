package com.firkat.fillin.feature.auth.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.navigation.AppNavigationViewModel
import com.firkat.fillin.ui.navigation.AppRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    onContinueAsGuest: () -> Unit,
    onContinueWithAccount: () -> Unit,
) {
    // Use the same Activity owner as FillinNavHost; leaving this entry must not cancel Firebase exchange.
    val owner = checkNotNull(LocalActivity.current as? ComponentActivity)
    val viewModel: AuthViewModel = koinViewModel(viewModelStoreOwner = owner)
    val navigation: AppNavigationViewModel = koinViewModel(viewModelStoreOwner = owner)
    val googleSignIn: GoogleSignInLauncher = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolvingBudget by navigation.resolvingBudget.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val authVisible = navigation.backStack.lastOrNull() == AppRoute.Auth
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val continueAsGuest by rememberUpdatedState(onContinueAsGuest)
    val continueWithAccount by rememberUpdatedState(onContinueWithAccount)

    LaunchedEffect(viewModel, googleSignIn, lifecycleOwner, context, resources, authVisible) {
        if (!authVisible) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                launch {
                    viewModel.state.map { it.errorId to it.error }.distinctUntilChanged().collectLatest { (id, error) ->
                        if (error != null) {
                            snackbar.showSaldomiSnackbar(resources.getString(error.messageRes))
                            viewModel.onAction(AuthAction.ErrorShown(id))
                        }
                    }
                }
                // SharedFlow has replay=0. Subscribe before requesting any pending picker effect.
                viewModel.effects.onSubscription { viewModel.onAction(AuthAction.UiReady) }.collect { effect ->
                    when (effect) {
                        is AuthEffect.LaunchGooglePicker -> {
                            val current = viewModel.state.value
                            if (current.operationId == effect.requestId && current.operation == AuthOperation.AwaitingPicker) {
                                viewModel.onAction(AuthAction.PickerStarted(effect.requestId))
                                launch { receiveGoogleCredential(viewModel, googleSignIn, context, effect.requestId) }
                            }
                        }
                        AuthEffect.EmailUnavailable -> launch {
                            snackbar.showSaldomiSnackbar(resources.getString(R.string.auth_email_unavailable))
                        }
                        AuthEffect.ContinueAsGuest -> continueAsGuest()
                        AuthEffect.ContinueWithAccount -> continueWithAccount()
                    }
                }
            } finally {
                // Cancels only a pending picker, never the already accepted Firebase exchange.
                viewModel.onAction(AuthAction.UiDetached)
            }
        }
    }

    AuthContent(
        modifier = Modifier.testTag("auth_screen"),
        snackBarHostState = snackbar,
        isLoading = state.busy || state.session == AuthSession.Loading || resolvingBudget,
        loadingButton = state.selectedButton,
        googleEnabled = state.session != AuthSession.Unavailable,
        onGoogleClick = { viewModel.onAction(AuthAction.GoogleClicked) },
        onEmailClick = { viewModel.onAction(AuthAction.EmailClicked) },
        onGuestClick = { viewModel.onAction(AuthAction.GuestClicked) },
    )
}

private suspend fun receiveGoogleCredential(
    viewModel: AuthViewModel,
    googleSignIn: GoogleSignInLauncher,
    context: Context,
    requestId: Long,
) {
    try {
        viewModel.onAction(AuthAction.CredentialReceived(requestId, googleSignIn.launch(context)))
    } catch (_: GetCredentialCancellationException) {
        viewModel.onAction(AuthAction.CredentialCancelled(requestId))
    } catch (cancelled: CancellationException) {
        viewModel.onAction(AuthAction.CredentialCancelled(requestId))
        throw cancelled
    } catch (_: NoCredentialException) {
        viewModel.onAction(AuthAction.CredentialFailed(requestId, AuthError.NoGoogleAccount))
    } catch (failure: AuthFailure) {
        viewModel.onAction(AuthAction.CredentialFailed(requestId, failure.error))
    } catch (_: Exception) {
        viewModel.onAction(AuthAction.CredentialFailed(requestId, AuthError.SignInFailed))
    }
}

package com.firkat.fillin.feature.auth.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.lifecycle.LifecycleEventObserver
import com.firkat.fillin.feature.auth.domain.userOrNull
import com.firkat.fillin.feature.auth.ui.interactor.EmailPage
import com.firkat.fillin.feature.auth.ui.interactor.EmailTask
import com.firkat.fillin.feature.auth.ui.components.EmailAuthContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
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
import com.firkat.fillin.feature.auth.ui.components.AuthContent
import com.firkat.fillin.feature.auth.ui.components.messageRes
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.interactor.AuthEffect
import com.firkat.fillin.feature.auth.ui.interactor.AuthOperation
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
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
    methods: Boolean = false,
    onBack: () -> Unit = {},
) {
    // Use the same Activity owner as FillinNavHost; leaving this entry must not cancel Firebase exchange.
    val owner = checkNotNull(LocalActivity.current as? ComponentActivity)
    val viewModel: AuthViewModel = koinViewModel(viewModelStoreOwner = owner)
    val navigation: AppNavigationViewModel = koinViewModel(viewModelStoreOwner = owner)
    val googleSignIn: GoogleSignInLauncher = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolvingBudget by navigation.resolvingBudget.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val authVisible = navigation.backStack.lastOrNull() in listOf(AppRoute.Auth, AppRoute.AccountMethods)
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
                        if (error != null && viewModel.state.value.page == EmailPage.Welcome) {
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

    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.resendAt) {
        now = System.currentTimeMillis()
        if (state.resendAt > now) { delay(state.resendAt - now); now = System.currentTimeMillis() }
    }
    LaunchedEffect(methods) {
        if (methods) viewModel.onAction(AuthAction.Page(if (state.session is AuthSession.EmailVerificationRequired) EmailPage.Verify else EmailPage.Methods))
    }
    DisposableEffect(viewModel, lifecycleOwner, state.page) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.state.value.page == EmailPage.Verify && !viewModel.state.value.busy) viewModel.onAction(AuthAction.SubmitEmail(EmailTask.Verify))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val emailBack = {
        if (methods && state.page in setOf(EmailPage.Methods, EmailPage.Verify)) onBack()
        else viewModel.onAction(AuthAction.Page(if (methods) EmailPage.Methods else if (state.page in setOf(EmailPage.Register, EmailPage.Reset)) EmailPage.Login else EmailPage.Welcome))
    }
    BackHandler(enabled = state.page != EmailPage.Welcome) { if (!state.busy) emailBack() }
    if (state.page != EmailPage.Welcome) {
        EmailAuthContent(state.page, state.email, state.password, state.confirmation, state.busy, state.error,
            state.notice, state.session.userOrNull, now >= state.resendAt,
            { viewModel.onAction(AuthAction.EmailChanged(it)) }, { viewModel.onAction(AuthAction.PasswordChanged(it)) },
            { viewModel.onAction(AuthAction.ConfirmationChanged(it)) }, { viewModel.onAction(AuthAction.Page(it)) },
            { viewModel.onAction(AuthAction.SubmitEmail(it)) },
            { reauthenticate -> scope.launch {
                if (viewModel.state.value.busy) return@launch
                viewModel.onAction(AuthAction.GoogleLinkPicking(true))
                try {
                    val token = googleSignIn.launch(context)
                    viewModel.onAction(AuthAction.GoogleLinkPicking(false))
                    viewModel.onAction(AuthAction.SubmitEmail(if (reauthenticate) EmailTask.ReauthenticateGoogle else EmailTask.LinkGoogle, token))
                } catch (_: GetCredentialCancellationException) { viewModel.onAction(AuthAction.GoogleLinkPicking(false)) }
                catch (e: CancellationException) { viewModel.onAction(AuthAction.GoogleLinkPicking(false)); throw e }
                catch (_: NoCredentialException) { viewModel.onAction(AuthAction.EmailFailed(AuthError.NoGoogleAccount)) }
                catch (e: AuthFailure) { viewModel.onAction(AuthAction.EmailFailed(e.error)) }
                catch (_: Exception) { viewModel.onAction(AuthAction.EmailFailed(AuthError.SignInFailed)) }
            } },
            { viewModel.onAction(AuthAction.Page(EmailPage.Welcome)); continueAsGuest() },
            { viewModel.onAction(AuthAction.SignOut) }, emailBack)
        return
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

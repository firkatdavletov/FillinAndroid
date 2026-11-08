package com.firkat.fillin.feature.auth.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.firkat.fillin.core.mvi.BaseViewModel
import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.core.mvi.Reducer
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.userOrNull
import com.firkat.fillin.feature.auth.ui.interactor.EmailTask
import com.firkat.fillin.feature.auth.domain.ServerProfileState
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.interactor.AuthCommand
import com.firkat.fillin.feature.auth.ui.interactor.AuthEffect
import com.firkat.fillin.feature.auth.ui.interactor.AuthEvent
import com.firkat.fillin.feature.auth.ui.interactor.AuthOperation
import com.firkat.fillin.feature.auth.ui.interactor.AuthState
import com.firkat.fillin.feature.auth.ui.interactor.uid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AuthViewModel(
    initialState: AuthState,
    reducer: Reducer<AuthState, AuthAction, AuthEvent>,
    interactor: Interactor<AuthCommand, AuthEvent>,
) : BaseViewModel<AuthState, AuthAction, AuthCommand, AuthEvent, AuthEffect>(initialState, reducer, interactor) {
    private var pendingLink: AuthCommand.Email? = null
    private var retriedLink = false
    private val sessionJob: Job
    private var operationJob: Job? = null
    private var startedOperationId: Long? = null
    private var profileJob: Job? = null
    private var profileRequestId: Long? = null
    private var uiReady = false
    private var pickerEffectJob: Job? = null
    private var pickerEffectId: Long? = null

    init {
        // Remains active outside AuthScreen, including restored sessions at cold launch.
        sessionJob = execute(AuthCommand.ObserveSession)
    }

    override fun handleAction(action: AuthAction) {
        val current = state.value // BaseViewModel has already reduced the action.
        when (action) {
            is AuthAction.SubmitEmail -> if (current.emailBusy && operationJob?.isActive != true) {
                val command = AuthCommand.Email(action.task, current.email, current.password, action.token, current.session.userOrNull?.uid)
                if (action.task in setOf(EmailTask.LinkPassword, EmailTask.LinkGoogle)) { pendingLink = command; retriedLink = false }
                operationJob = execute(command)
            }
            is AuthAction.Page -> if (!current.busy) { pendingLink = null }
            AuthAction.SignOut -> if (current.operation == AuthOperation.SigningOut && startedOperationId != current.operationId) {
                pendingLink = null
                executeOperation(current.operationId, AuthCommand.SignOut(current.operationId))
            }
            AuthAction.UiReady -> uiReady = true
            AuthAction.UiDetached -> {
                uiReady = false
                pickerEffectJob?.cancel()
                pickerEffectId = null
            }
            AuthAction.GoogleClicked -> if (current.buttonsEnabled && current.session is AuthSession.Authenticated) {
                emitEffect(AuthEffect.ContinueWithAccount)
            }
            AuthAction.EmailClicked -> Unit
            AuthAction.GuestClicked -> if (current.buttonsEnabled) emitEffect(AuthEffect.ContinueAsGuest)
            is AuthAction.CredentialReceived -> if (
                current.operation == AuthOperation.SigningIn && current.operationId == action.requestId &&
                startedOperationId != current.operationId
            ) {
                executeOperation(current.operationId, AuthCommand.SignInWithGoogle(current.operationId, action.idToken))
            }
            else -> Unit
        }
        launchPendingPicker()
        coordinateProfile()
    }

    override fun handleEvent(event: AuthEvent) {
        if (event is AuthEvent.SessionChanged && pendingLink?.expectedUid != event.session.userOrNull?.uid) pendingLink = null
        if (event is AuthEvent.EmailFinished) {
            val reauth = event.task in setOf(EmailTask.ReauthenticatePassword, EmailTask.ReauthenticateGoogle)
            if (reauth && event.error == null && pendingLink == null) {
                onAction(AuthAction.Page(com.firkat.fillin.feature.auth.ui.interactor.EmailPage.Methods))
                onAction(AuthAction.RetryServerAuthentication)
            } else if (reauth && event.error == null && !retriedLink) {
                pendingLink?.let { command ->
                    retriedLink = true
                    // Resume the original link once; credentials live only in this ViewModel.
                    onAction(AuthAction.PasswordChanged(command.password))
                    onAction(AuthAction.ConfirmationChanged(command.password))
                    operationJob = null
                    onAction(AuthAction.SubmitEmail(command.task, command.token))
                    retriedLink = true
                }
            } else if (event.error != com.firkat.fillin.feature.auth.domain.AuthError.RecentLoginRequired || retriedLink) pendingLink = null
        }
        coordinateProfile()
    }

    private fun executeOperation(operationId: Long, command: AuthCommand) {
        startedOperationId = operationId
        operationJob = execute(command).also { job ->
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    // Reduce on Main only while the ViewModel is alive; cancellation is not a UI error.
                    viewModelScope.launch { onAction(AuthAction.OperationCancelled(operationId)) }
                }
            }
        }
    }

    private fun launchPendingPicker() {
        val current = state.value
        if (uiReady && current.operation == AuthOperation.AwaitingPicker && pickerEffectId != current.operationId) {
            pickerEffectId = current.operationId
            pickerEffectJob = emitEffect(AuthEffect.LaunchGooglePicker(current.operationId))
        }
    }

    private fun coordinateProfile() {
        val current = state.value
        if (profileRequestId == current.profileRequestId) return
        // Only profile work is replaced on account change/retry. Firebase exchange and session observation continue.
        profileJob?.cancel()
        profileRequestId = current.profileRequestId
        val uid = current.session.uid ?: return
        if (current.serverProfile == ServerProfileState.Loading) {
            profileJob = execute(AuthCommand.LoadServerProfile(current.profileRequestId, uid))
        }
    }
}

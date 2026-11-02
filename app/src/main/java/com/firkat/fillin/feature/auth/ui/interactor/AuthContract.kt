package com.firkat.fillin.feature.auth.ui.interactor

import com.firkat.fillin.core.mvi.Action
import com.firkat.fillin.core.mvi.Command
import com.firkat.fillin.core.mvi.Effect
import com.firkat.fillin.core.mvi.Event
import com.firkat.fillin.core.mvi.State
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerProfileState

internal val AuthSession.uid: String?
    get() = (this as? AuthSession.Authenticated)?.user?.uid

enum class AuthButton { Google, Email, Guest }
enum class AuthOperation { AwaitingPicker, Picking, SigningIn, SigningOut }

data class AuthState(
    val session: AuthSession = AuthSession.Loading,
    val operation: AuthOperation? = null,
    val operationId: Long = 0,
    val selectedButton: AuthButton = AuthButton.Google,
    val error: AuthError? = null,
    val errorId: Long = 0,
    val serverProfile: ServerProfileState = ServerProfileState.Idle,
    val profileRequestId: Long = 0,
    val profileAvailable: Boolean = false,
) : State {
    val busy: Boolean get() = operation != null
    val buttonsEnabled: Boolean get() = !busy && session != AuthSession.Loading
}

sealed interface AuthAction : Action {
    data object GoogleClicked : AuthAction
    data object EmailClicked : AuthAction
    data object GuestClicked : AuthAction
    data object SignOut : AuthAction
    data object RetryServerAuthentication : AuthAction
    data object UiReady : AuthAction
    data object UiDetached : AuthAction
    data class PickerStarted(val requestId: Long) : AuthAction
    data class CredentialReceived(val requestId: Long, val idToken: String) : AuthAction
    data class CredentialCancelled(val requestId: Long) : AuthAction
    data class CredentialFailed(val requestId: Long, val error: AuthError) : AuthAction
    data class OperationCancelled(val operationId: Long) : AuthAction
    data class ErrorShown(val errorId: Long) : AuthAction
}

sealed interface AuthCommand : Command {
    data object ObserveSession : AuthCommand
    data class SignInWithGoogle(val operationId: Long, val idToken: String) : AuthCommand
    data class SignOut(val operationId: Long) : AuthCommand
    data class LoadServerProfile(val requestId: Long, val uid: String) : AuthCommand
}

sealed interface AuthEvent : Event {
    data class SessionChanged(val session: AuthSession, val profileAvailable: Boolean) : AuthEvent
    data class OperationFinished(val operationId: Long, val error: AuthError? = null) : AuthEvent
    data class ProfileLoaded(val requestId: Long, val uid: String, val profile: ServerProfileState) : AuthEvent
}

sealed interface AuthEffect : Effect {
    data class LaunchGooglePicker(val requestId: Long) : AuthEffect
    data object EmailUnavailable : AuthEffect
    data object ContinueAsGuest : AuthEffect
    data object ContinueWithAccount : AuthEffect
}

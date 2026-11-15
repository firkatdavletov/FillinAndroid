package com.firkat.fillin.feature.auth.ui.interactor

import com.firkat.fillin.core.mvi.Reducer
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerProfileState
import com.firkat.fillin.feature.auth.domain.userOrNull

class AuthReducer : Reducer<AuthState, AuthAction, AuthEvent> {
    override fun reduceAction(
        state: AuthState,
        action: AuthAction
    ): AuthState = with(state) {
        when (action) {
            is AuthAction.EmailFailed -> copy(emailBusy = false).withError(action.error)
            is AuthAction.GoogleLinkPicking -> copy(emailBusy = action.picking)
            is AuthAction.Page -> if (!busy) {
                copy(
                    page = action.page,
                    password = "",
                    confirmation = "",
                    error = null,
                    notice = null
                )
            } else {
                this
            }
            is AuthAction.EmailChanged -> if (!busy) copy(email = action.value, error = null) else this
            is AuthAction.PasswordChanged -> if (!busy) copy(password = action.value, error = null) else this
            is AuthAction.ConfirmationChanged -> if (!busy) copy(confirmation = action.value, error = null) else this
            is AuthAction.SubmitEmail -> if (busy) {
                this
            } else {
                when {
                    action.task == EmailTask.Resend && action.nowMillis < resendAt -> this
                    action.task in setOf(EmailTask.Login, EmailTask.Register, EmailTask.Reset) &&
                        !Regex(
                            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                        ).matches(email.trim()) -> withError(AuthError.InvalidEmail)
                    action.task in setOf(EmailTask.Register, EmailTask.LinkPassword) &&
                        (password.length < 12 || password != confirmation) -> withError(AuthError.WeakPassword)
                    else -> copy(emailBusy = true, error = null, notice = null)
                }
            }

            AuthAction.GoogleClicked -> when {
                !buttonsEnabled -> this
                session is AuthSession.Authenticated -> copy(selectedButton = AuthButton.Google)
                session == AuthSession.Unavailable -> withError(AuthError.NotConfigured)
                else -> copy(
                    operation = AuthOperation.AwaitingPicker,
                    operationId = operationId + 1,
                    selectedButton = AuthButton.Google,
                    error = null
                )
            }
            AuthAction.EmailClicked -> if (buttonsEnabled) {
                copy(
                    selectedButton = AuthButton.Email,
                    page = EmailPage.Login
                )
            } else {
                this
            }
            AuthAction.GuestClicked -> if (buttonsEnabled) copy(selectedButton = AuthButton.Guest) else this
            AuthAction.SignOut -> if (!busy &&
                session != AuthSession.Anonymous &&
                session != AuthSession.Unavailable &&
                session != AuthSession.Loading
            ) {
                copy(operation = AuthOperation.SigningOut, operationId = operationId + 1, error = null)
            } else {
                this
            }
            AuthAction.RetryServerAuthentication -> if (
                profileAvailable && session.uid != null && serverProfile != ServerProfileState.Loading
            ) {
                copy(serverProfile = ServerProfileState.Loading, profileRequestId = profileRequestId + 1)
            } else {
                this
            }
            is AuthAction.PickerStarted -> if (operationId == action.requestId &&
                operation == AuthOperation.AwaitingPicker
            ) {
                copy(operation = AuthOperation.Picking)
            } else {
                this
            }
            is AuthAction.CredentialReceived -> if (acceptsCredential(action.requestId)) {
                copy(operation = AuthOperation.SigningIn)
            } else {
                this
            }
            is AuthAction.CredentialCancelled -> if (acceptsCredential(
                    action.requestId
                )
            ) {
                copy(operation = null)
            } else {
                this
            }
            is AuthAction.CredentialFailed -> if (acceptsCredential(action.requestId)) {
                copy(operation = null).withError(action.error)
            } else {
                this
            }
            is AuthAction.OperationCancelled -> if (
                operationId == action.operationId &&
                (operation == AuthOperation.SigningIn || operation == AuthOperation.SigningOut)
            ) {
                copy(operation = null)
            } else {
                this
            }
            is AuthAction.ErrorShown -> if (errorId == action.errorId) copy(error = null) else this
            AuthAction.UiDetached -> if (operation == AuthOperation.AwaitingPicker ||
                operation == AuthOperation.Picking
            ) {
                copy(operation = null)
            } else {
                this
            }
            AuthAction.UiReady -> this
        }
    }

    override fun reduceEvent(
        state: AuthState,
        event: AuthEvent
    ): AuthState = with(state) {
        when (event) {
            is AuthEvent.EmailFinished -> copy(
                emailBusy = false,
                password = "",
                confirmation = "",
                page = when {
                    event.error == AuthError.RecentLoginRequired -> EmailPage.Reauthenticate
                    event.task == EmailTask.Register && session is AuthSession.EmailVerificationRequired -> EmailPage.Verify
                    event.error == null && event.task == EmailTask.LinkPassword -> EmailPage.Methods
                    else -> page
                },
                resendAt = if (event.error == null &&
                    event.task in setOf(EmailTask.Register, EmailTask.Resend)
                ) {
                    event.now + 60_000
                } else {
                    resendAt
                },
                notice = if (event.error == null &&
                    event.task == EmailTask.Reset
                ) {
                    "reset"
                } else if (event.error == null &&
                    event.task in setOf(EmailTask.Register, EmailTask.Resend)
                ) {
                    "sent"
                } else if (event.error == null &&
                    event.task == EmailTask.Verify &&
                    session is AuthSession.EmailVerificationRequired
                ) {
                    "unverified"
                } else {
                    null
                }
            ).let { if (event.error != null) it.withError(event.error) else it }

            is AuthEvent.SessionChanged -> {
                val reload = session.uid != event.session.uid || profileRequestId == 0L
                copy(
                    session = event.session,
                    page = when {
                        event.session == AuthSession.Anonymous && session.userOrNull != null -> EmailPage.Welcome
                        event.session is AuthSession.EmailVerificationRequired && page != EmailPage.Methods -> EmailPage.Verify
                        else -> page
                    },
                    profileAvailable = event.profileAvailable,
                    profileRequestId = if (reload) profileRequestId + 1 else profileRequestId,
                    serverProfile = if (reload) {
                        if (event.session.uid != null && event.profileAvailable) {
                            ServerProfileState.Loading
                        } else {
                            ServerProfileState.Idle
                        }
                    } else {
                        serverProfile
                    }
                )
            }
            is AuthEvent.OperationFinished -> if (
                operationId == event.operationId &&
                (operation == AuthOperation.SigningIn || operation == AuthOperation.SigningOut)
            ) {
                copy(operation = null).let { if (event.error != null) it.withError(event.error) else it }
            } else {
                this
            }
            is AuthEvent.ProfileLoaded -> if (
                session.uid == event.uid &&
                profileRequestId == event.requestId &&
                serverProfile == ServerProfileState.Loading
            ) {
                copy(serverProfile = event.profile)
            } else {
                this
            }
        }
    }

    private fun AuthState.acceptsCredential(
        id: Long
    ) = operationId == id &&
        (operation == AuthOperation.AwaitingPicker || operation == AuthOperation.Picking)

    private fun AuthState.withError(
        error: AuthError
    ) = copy(error = error, errorId = errorId + 1)
}

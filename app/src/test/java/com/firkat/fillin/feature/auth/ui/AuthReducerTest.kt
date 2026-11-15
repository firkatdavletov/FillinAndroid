package com.firkat.fillin.feature.auth.ui

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.interactor.AuthButton
import com.firkat.fillin.feature.auth.ui.interactor.AuthEvent
import com.firkat.fillin.feature.auth.ui.interactor.AuthOperation
import com.firkat.fillin.feature.auth.ui.interactor.AuthReducer
import com.firkat.fillin.feature.auth.ui.interactor.AuthState
import org.junit.Assert.*
import org.junit.Test

class AuthReducerTest {
    private val reducer = AuthReducer()
    private val anonymous = AuthState(session = AuthSession.Anonymous)
    private val account = AuthSession.Authenticated(AuthUser("a", "User", null))

    @Test fun `accepted action locks immediately and duplicate actions leave state unchanged`() {
        val initial = anonymous.copy(error = AuthError.Network)
        val waiting = reducer.reduceAction(initial, AuthAction.GoogleClicked)
        assertEquals(AuthOperation.AwaitingPicker, waiting.operation)
        assertTrue(waiting.busy)
        assertNull(waiting.error)
        assertEquals(AuthError.Network, initial.error)
        assertFalse(initial.busy)
        listOf(AuthAction.GoogleClicked, AuthAction.EmailClicked, AuthAction.GuestClicked, AuthAction.SignOut).forEach {
            assertEquals(waiting, reducer.reduceAction(waiting, it))
        }
        assertEquals(AuthState(), reducer.reduceAction(AuthState(), AuthAction.GoogleClicked))
    }

    @Test fun `picker identity and phase accept a credential only once`() {
        val waiting = reducer.reduceAction(anonymous, AuthAction.GoogleClicked)
        val picking = reducer.reduceAction(waiting, AuthAction.PickerStarted(waiting.operationId))
        assertEquals(AuthOperation.Picking, picking.operation)
        assertEquals(picking, reducer.reduceAction(picking, AuthAction.CredentialCancelled(0)))
        val signingIn = reducer.reduceAction(picking, AuthAction.CredentialReceived(picking.operationId, "synthetic"))
        assertEquals(AuthOperation.SigningIn, signingIn.operation)
        assertEquals(signingIn, reducer.reduceAction(signingIn, AuthAction.CredentialCancelled(picking.operationId)))
        assertEquals(signingIn, reducer.reduceAction(signingIn, AuthAction.UiDetached))
        assertEquals(
            anonymous.copy(operationId = waiting.operationId),
            reducer.reduceAction(picking, AuthAction.UiDetached)
        )
    }

    @Test fun `stale operation results and old acknowledgements cannot change current operation or error`() {
        val waiting = reducer.reduceAction(anonymous, AuthAction.GoogleClicked)
        val failed = reducer.reduceAction(waiting, AuthAction.CredentialFailed(waiting.operationId, AuthError.Network))
        val retry = reducer.reduceAction(failed, AuthAction.GoogleClicked)
        val signingIn = reducer.reduceAction(retry, AuthAction.CredentialReceived(retry.operationId, "synthetic"))
        assertEquals(
            signingIn,
            reducer.reduceEvent(signingIn, AuthEvent.OperationFinished(waiting.operationId, AuthError.SignInFailed))
        )
        val failedAgain = reducer.reduceEvent(
            signingIn,
            AuthEvent.OperationFinished(signingIn.operationId, AuthError.Network)
        )
        assertEquals(failedAgain, reducer.reduceAction(failedAgain, AuthAction.ErrorShown(failed.errorId)))
        assertNull(reducer.reduceAction(failedAgain, AuthAction.ErrorShown(failedAgain.errorId)).error)
        assertFalse(failedAgain.busy)
    }

    @Test fun `session changes invalidate profile results including A to B to A`() {
        val first = reducer.reduceEvent(anonymous, AuthEvent.SessionChanged(account, true))
        assertEquals(ServerProfileState.Loading, first.serverProfile)
        val sameUid = reducer.reduceEvent(
            first,
            AuthEvent.SessionChanged(account.copy(user = account.user.copy(displayName = "Updated")), true)
        )
        assertEquals(first.profileRequestId, sameUid.profileRequestId)
        val second = reducer.reduceEvent(
            first,
            AuthEvent.SessionChanged(account.copy(user = account.user.copy(uid = "b")), true)
        )
        val third = reducer.reduceEvent(second, AuthEvent.SessionChanged(account, true))
        val oldProfile = ServerProfileState.Ready(ServerUser("server-a", "a", null, null))
        assertEquals(
            third,
            reducer.reduceEvent(third, AuthEvent.ProfileLoaded(first.profileRequestId, "a", oldProfile))
        )
        assertEquals(
            third,
            reducer.reduceEvent(
                third,
                AuthEvent.ProfileLoaded(
                    second.profileRequestId,
                    "b",
                    ServerProfileState.Failed(ServerAuthError.Network)
                )
            )
        )
        val ready = reducer.reduceEvent(third, AuthEvent.ProfileLoaded(third.profileRequestId, "a", oldProfile))
        assertEquals(oldProfile, ready.serverProfile)
        val signedOut = reducer.reduceEvent(ready, AuthEvent.SessionChanged(AuthSession.Anonymous, true))
        assertEquals(ServerProfileState.Idle, signedOut.serverProfile)
        assertEquals(signedOut, reducer.reduceAction(signedOut, AuthAction.RetryServerAuthentication))
    }

    @Test fun `operation cancellation clears only the matching active sign in or sign out`() {
        AuthOperation.entries.forEach { operation ->
            val state = anonymous.copy(operation = operation, operationId = 2)
            assertEquals(state, reducer.reduceAction(state, AuthAction.OperationCancelled(1)))
            val cancelled = reducer.reduceAction(state, AuthAction.OperationCancelled(2))
            if (operation == AuthOperation.SigningIn || operation == AuthOperation.SigningOut) {
                assertEquals(state.copy(operation = null), cancelled)
            } else {
                assertEquals(state, cancelled)
            }
        }
        assertEquals(anonymous, reducer.reduceAction(anonymous, AuthAction.OperationCancelled(0)))
    }

    @Test fun `retry locks synchronously and rejects old profile results`() {
        val loading = reducer.reduceEvent(anonymous, AuthEvent.SessionChanged(account, true))
        assertEquals(loading, reducer.reduceAction(loading, AuthAction.RetryServerAuthentication))
        val failure = AuthEvent.ProfileLoaded(
            loading.profileRequestId,
            "a",
            ServerProfileState.Failed(ServerAuthError.Network)
        )
        val failed = reducer.reduceEvent(loading, failure)
        val retry = reducer.reduceAction(failed, AuthAction.RetryServerAuthentication)
        assertEquals(ServerProfileState.Loading, retry.serverProfile)
        assertEquals(retry, reducer.reduceAction(retry, AuthAction.RetryServerAuthentication))
        assertEquals(retry, reducer.reduceEvent(retry, failure))
    }

    @Test fun `local selections and unavailable Firebase do not start business operations`() {
        assertEquals(AuthButton.Email, reducer.reduceAction(anonymous, AuthAction.EmailClicked).selectedButton)
        assertEquals(AuthButton.Guest, reducer.reduceAction(anonymous, AuthAction.GuestClicked).selectedButton)
        val unavailable = reducer.reduceAction(
            anonymous.copy(session = AuthSession.Unavailable),
            AuthAction.GoogleClicked
        )
        assertEquals(AuthError.NotConfigured, unavailable.error)
        assertFalse(unavailable.busy)
        assertFalse(reducer.reduceAction(anonymous.copy(session = account), AuthAction.GoogleClicked).busy)
        val signingOut = reducer.reduceAction(anonymous.copy(session = account), AuthAction.SignOut)
        assertEquals(AuthOperation.SigningOut, signingOut.operation)
        assertEquals(signingOut, reducer.reduceAction(signingOut, AuthAction.SignOut))
    }
}

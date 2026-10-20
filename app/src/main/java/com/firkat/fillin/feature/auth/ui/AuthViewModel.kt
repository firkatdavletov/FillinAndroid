package com.firkat.fillin.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: AuthSession = AuthSession.Loading,
    val busy: Boolean = false,
    val error: AuthError? = null,
)

data class GoogleSignInRequest(val id: Long)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AuthError?>(null)
    private val requests = Channel<GoogleSignInRequest>(Channel.BUFFERED)
    private var nextRequestId = 0L
    private var pendingRequestId: Long? = null
    val googleSignInRequests = requests.receiveAsFlow()
    val state = combine(repository.session, busy, error, ::AuthUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState(repository.session.value))

    fun signIn() {
        if (busy.value || repository.session.value == AuthSession.Loading ||
            repository.session.value is AuthSession.Authenticated
        ) return
        if (repository.session.value == AuthSession.Unavailable) {
            error.value = AuthError.NotConfigured
            return
        }
        error.value = null
        busy.value = true
        val id = ++nextRequestId
        pendingRequestId = id
        requests.trySend(GoogleSignInRequest(id))
    }

    fun credentialReceived(requestId: Long, idToken: String) {
        if (!consume(requestId)) return
        viewModelScope.launch {
            try {
                repository.signInWithGoogle(idToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthFailure) {
                error.value = failure.error
            } catch (_: Exception) {
                error.value = AuthError.SignInFailed
            } finally {
                busy.value = false
            }
        }
    }

    fun credentialCancelled(requestId: Long) {
        if (!consume(requestId)) return
        busy.value = false
    }

    fun credentialFailed(requestId: Long, reason: AuthError) {
        if (!consume(requestId)) return
        error.value = reason
        busy.value = false
    }

    fun isCredentialRequestActive(requestId: Long): Boolean = pendingRequestId == requestId

    fun cancelCredentialRequest() {
        pendingRequestId?.let(::credentialCancelled)
    }

    fun signOut() {
        if (busy.value || repository.session.value !is AuthSession.Authenticated) return
        error.value = null
        busy.value = true
        viewModelScope.launch {
            try {
                repository.signOut()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthFailure) {
                error.value = failure.error
            } catch (_: Exception) {
                error.value = AuthError.SignOutFailed
            } finally {
                busy.value = false
            }
        }
    }

    private fun consume(requestId: Long): Boolean {
        if (pendingRequestId != requestId) return false
        pendingRequestId = null
        return true
    }

    override fun onCleared() {
        requests.close()
    }
}

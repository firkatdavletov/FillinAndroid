package com.firkat.fillin.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: AuthSession = AuthSession.Loading,
    val busy: Boolean = false,
    val error: AuthError? = null,
    val serverProfile: ServerProfileState = ServerProfileState.Idle,
)

data class GoogleSignInRequest(val id: Long)

class AuthViewModel(
    private val repository: AuthRepository,
    private val serverAuth: ServerAuthRepository? = null,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AuthError?>(null)
    private val serverProfile = MutableStateFlow<ServerProfileState>(ServerProfileState.Idle)
    private val profileReload = MutableStateFlow(0L)
    private val requests = Channel<GoogleSignInRequest>(Channel.BUFFERED)
    private var nextRequestId = 0L
    private var pendingRequestId: Long? = null
    val googleSignInRequests = requests.receiveAsFlow()
    val state = combine(repository.session, busy, error, serverProfile, ::AuthUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState(repository.session.value))

    init {
        if (serverAuth != null) viewModelScope.launch {
            combine(
                repository.session.map { (it as? AuthSession.Authenticated)?.user?.uid }.distinctUntilChanged(),
                profileReload,
            ) { uid, _ -> uid }.collectLatest { uid ->
                serverProfile.value = ServerProfileState.Idle
                if (uid == null) return@collectLatest
                serverProfile.value = ServerProfileState.Loading
                try {
                    val user = serverAuth.authenticate(uid)
                    if (currentUid() == uid) serverProfile.value = ServerProfileState.Ready(user)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ServerAuthFailure) {
                    if (currentUid() == uid) serverProfile.value = ServerProfileState.Failed(failure.reason)
                } catch (_: Exception) {
                    if (currentUid() == uid) serverProfile.value = ServerProfileState.Failed(ServerAuthError.Unavailable)
                }
            }
        }
    }

    fun retryServerAuthentication() {
        if (serverProfile.value !is ServerProfileState.Loading) profileReload.update { it + 1 }
    }

    private fun currentUid() = (repository.session.value as? AuthSession.Authenticated)?.user?.uid

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

package com.firkat.fillin.feature.auth.ui.interactor

import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.domain.ServerProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AuthInteractor(
    private val repository: AuthRepository,
    private val serverAuth: ServerAuthRepository?,
    private val dispatcher: CoroutineDispatcher,
) : Interactor<AuthCommand, AuthEvent> {
    override fun execute(command: AuthCommand): Flow<AuthEvent> = flow {
        when (command) {
            AuthCommand.ObserveSession -> repository.session.collect {
                emit(AuthEvent.SessionChanged(it, serverAuth != null))
            }
            is AuthCommand.SignInWithGoogle -> {
                val error = authError(AuthError.SignInFailed) { repository.signInWithGoogle(command.idToken) }
                currentCoroutineContext().ensureActive()
                emit(AuthEvent.OperationFinished(command.operationId, error))
            }
            is AuthCommand.SignOut -> {
                val error = authError(AuthError.SignOutFailed) { repository.signOut() }
                currentCoroutineContext().ensureActive()
                emit(AuthEvent.OperationFinished(command.operationId, error))
            }
            is AuthCommand.LoadServerProfile -> {
                val server = serverAuth ?: return@flow
                if (repository.session.value.uid != command.uid) return@flow
                val profile = try {
                    ServerProfileState.Ready(server.authenticate(command.uid))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ServerAuthFailure) {
                    ServerProfileState.Failed(failure.reason)
                } catch (_: Exception) {
                    ServerProfileState.Failed(ServerAuthError.Unavailable)
                }
                currentCoroutineContext().ensureActive()
                if (repository.session.value.uid == command.uid) {
                    emit(AuthEvent.ProfileLoaded(command.requestId, command.uid, profile))
                }
            }
        }
    }.flowOn(dispatcher)

    private suspend fun authError(fallback: AuthError, operation: suspend () -> Unit): AuthError? = try {
        operation()
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: AuthFailure) {
        failure.error
    } catch (_: Exception) {
        fallback
    }
}

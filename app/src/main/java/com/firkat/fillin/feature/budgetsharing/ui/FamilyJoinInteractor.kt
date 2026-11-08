package com.firkat.fillin.feature.budgetsharing.ui

import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.budgetsharing.domain.FamilyBudgetRepository
import com.firkat.fillin.feature.budgetsharing.domain.FamilyInvitationCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FamilyJoinInteractor(
    private val auth: AuthRepository,
    private val repository: FamilyBudgetRepository,
    private val dispatcher: CoroutineDispatcher,
) : Interactor<FamilyJoinCommand, FamilyJoinEvent> {
    override fun execute(command: FamilyJoinCommand): Flow<FamilyJoinEvent> = flow {
        when (command) {
            FamilyJoinCommand.ObserveSession -> auth.session.collect { emit(FamilyJoinEvent.SessionChanged(it)) }
            is FamilyJoinCommand.Join -> {
                checkCurrent(command.request)
                val code = try { FamilyInvitationCode.normalize(command.code) }
                catch (_: IllegalArgumentException) {
                    emit(FamilyJoinEvent.Finished(command.request, error = "Введите код приглашения полностью: 20 букв и цифр."))
                    return@flow
                }
                val event = try {
                    FamilyJoinEvent.Finished(command.request, name = repository.join(command.request.uid, code).name)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { FamilyJoinEvent.Finished(command.request, error = familyError(failure)) }
                checkCurrent(command.request)
                emit(event)
            }
        }
    }.flowOn(dispatcher)
    private suspend fun checkCurrent(request: JoinRequest) {
        currentCoroutineContext().ensureActive()
        if (auth.session.value.joinUid != request.uid) throw CancellationException("Account changed")
    }
}

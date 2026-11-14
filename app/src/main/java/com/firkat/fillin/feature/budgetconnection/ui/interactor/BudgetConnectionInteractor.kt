package com.firkat.fillin.feature.budgetconnection.ui.interactor

import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BudgetConnectionInteractor(
    private val auth: AuthRepository,
    private val repository: BudgetConnectionRepository,
    private val creation: BudgetCreationRepository,
    private val dispatcher: CoroutineDispatcher
) : Interactor<BudgetConnectionCommand, BudgetConnectionEvent> {
    override fun execute(
        command: BudgetConnectionCommand
    ): Flow<BudgetConnectionEvent> = flow {
        when (command) {
            BudgetConnectionCommand.ObserveSession -> auth.session.collect {
                emit(BudgetConnectionEvent.SessionChanged(it))
            }
            BudgetConnectionCommand.ObserveActiveBudget -> repository.activeBudgetId.collect {
                emit(BudgetConnectionEvent.ActiveBudgetChanged(it))
            }
            is BudgetConnectionCommand.Load -> load(command.request)
            is BudgetConnectionCommand.Connect -> {
                checkCurrent(command.request)
                val error = try {
                    repository.connect(command.request.uid, command.budget)
                    null
                } catch (
                    cancelled: CancellationException
                ) {
                    throw cancelled
                } catch (failure: Exception) {
                    setupError(failure)
                }
                publish(command.request, BudgetConnectionEvent.Finished(command.request, error))
            }
            is BudgetConnectionCommand.Create -> {
                val request = command.request
                checkCurrent(request)
                try {
                    if (command.pending !=
                        null
                    ) {
                        creation.resume(request.uid)
                    } else {
                        creation.create(request.uid, command.draft)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    checkCurrent(request)
                    val pending = try {
                        creation.pending(request.uid)
                    } catch (
                        cancelled: CancellationException
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        command.pending
                    }
                    val error = if (failure is IllegalArgumentException || failure is java.time.DateTimeException) {
                        "Проверьте название (до 80 символов), баланс (до двух знаков после запятой) и дату начала учёта."
                    } else {
                        setupError(failure)
                    }
                    publish(request, BudgetConnectionEvent.CreationFailed(request, command.draft, pending, error))
                    publish(request, BudgetConnectionEvent.Finished(request))
                    return@flow
                }
                publish(request, BudgetConnectionEvent.Created(request))
                load(request)
            }
        }
    }.flowOn(dispatcher)

    private suspend fun FlowCollector<BudgetConnectionEvent>.load(
        request: ConnectionRequest
    ) {
        checkCurrent(request)
        val error = try {
            val cached = repository.cachedBudgets(request.uid)
            publish(request, BudgetConnectionEvent.Cached(request, cached))
            val pending = creation.pending(request.uid)
            publish(request, BudgetConnectionEvent.Pending(request, pending))
            val budgets = repository.budgets(request.uid)
            publish(
                request,
                BudgetConnectionEvent.Loaded(
                    request,
                    budgets,
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Almaty")).toString()
                )
            )
            null
        } catch (
            cancelled: CancellationException
        ) {
            throw cancelled
        } catch (failure: Exception) {
            setupError(failure)
        }
        publish(request, BudgetConnectionEvent.Finished(request, error))
    }
    private suspend fun checkCurrent(
        request: ConnectionRequest
    ) {
        currentCoroutineContext().ensureActive()
        if (auth.session.value.uid != request.uid) throw CancellationException("Account changed")
    }
    private suspend fun FlowCollector<BudgetConnectionEvent>.publish(
        request: ConnectionRequest,
        event: BudgetConnectionEvent
    ) {
        checkCurrent(request)
        emit(event)
    }
}

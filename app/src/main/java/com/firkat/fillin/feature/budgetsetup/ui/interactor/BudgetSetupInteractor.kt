package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.domain.CreateLocalBudget
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class BudgetSetupInteractor(
    private val repository: BudgetSetupRepository,
    private val createLocal: CreateLocalBudget,
    private val dispatcher: CoroutineDispatcher,
) : Interactor<BudgetSetupCommand, BudgetSetupEvent> {
    override fun execute(command: BudgetSetupCommand): Flow<BudgetSetupEvent> = flow {
        when (command) {
            BudgetSetupCommand.ObserveReadiness -> repository.ready.collect { emit(BudgetSetupEvent.ReadinessChanged(it)) }
            is BudgetSetupCommand.Create -> {
                val error = try {
                    createLocal(command.balance.trim().replace(',', '.').toBigDecimal(), LocalDate.parse(command.date))
                    null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { setupError(failure) }
                currentCoroutineContext().ensureActive()
                emit(BudgetSetupEvent.Created(command.operationId, error))
            }
        }
    }.flowOn(dispatcher)
}

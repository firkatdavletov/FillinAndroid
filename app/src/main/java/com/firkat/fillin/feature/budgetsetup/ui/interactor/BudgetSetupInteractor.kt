package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.Interactor
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.budgetsharing.ui.familyError
import com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class BudgetSetupInteractor(
    private val auth: AuthRepository,
    private val connection: BudgetConnectionRepository,
    private val creation: BudgetCreationRepository,
    private val local: LocalBudgetCreator,
    private val family: FamilyBudgetRepository,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : Interactor<BudgetSetupCommand, BudgetSetupEvent> {
    override fun execute(command: BudgetSetupCommand): Flow<BudgetSetupEvent> = flow {
        when (command) {
            BudgetSetupCommand.ObserveSession -> auth.session.collect { emit(BudgetSetupEvent.Session(it)) }
            is BudgetSetupCommand.Load -> {
                checkUid(command.uid)
                var pending: PendingBudgetCreation? = null
                val error = try {
                    pending = creation.pending(command.uid)
                    val cached = connection.cachedBudgets(command.uid)
                    checkUid(command.uid)
                    emit(BudgetSetupEvent.Catalog(command.generation, cached, pending, true))
                    val budgets = connection.budgets(command.uid)
                    checkUid(command.uid)
                    emit(BudgetSetupEvent.Catalog(command.generation, budgets, pending, false))
                    null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { setupError(e) }
                checkUid(command.uid)
                emit(BudgetSetupEvent.LoadFinished(command.generation, error))
            }
            is BudgetSetupCommand.Submit -> {
                val state = command.state
                checkUid(state.uid)
                var pending = state.pending
                val error = try {
                    val today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Almaty")))
                    when (command.kind) {
                        SubmitKind.Local -> {
                            val draft = BudgetCreationDraft(state.name, state.balance, today.toString()).validated(today)
                            local.createLocal(draft.name, draft.openingBalance.toBigDecimal(), today)
                        }
                        SubmitKind.Cloud -> if (pending != null) creation.resume(requireNotNull(state.uid)) else {
                            creation.create(requireNotNull(state.uid), BudgetCreationDraft(state.name, state.balance, today.toString()))
                        }
                        SubmitKind.Connect -> connection.connect(requireNotNull(state.uid), requireNotNull(command.budget))
                        SubmitKind.Join -> family.join(requireNotNull(state.uid), FamilyInvitationCode.normalize(state.code))
                    }
                    pending = null
                    null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (command.kind == SubmitKind.Cloud) {
                        pending = try { creation.pending(requireNotNull(state.uid)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { pending }
                    }
                    if (command.kind == SubmitKind.Join) familyError(e) else setupError(e)
                }
                checkUid(state.uid)
                emit(BudgetSetupEvent.Finished(state.generation, state.operationId, error, pending))
            }
        }
    }.flowOn(dispatcher)
    private suspend fun checkUid(uid: String?) {
        currentCoroutineContext().ensureActive()
        if ((auth.session.value as? AuthSession.Authenticated)?.user?.uid != uid) throw CancellationException("Account changed")
    }
}

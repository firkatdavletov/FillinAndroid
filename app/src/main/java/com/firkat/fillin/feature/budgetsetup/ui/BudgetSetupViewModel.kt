package com.firkat.fillin.feature.budgetsetup.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsharing.domain.FamilyBudgetRepository
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator
import kotlinx.coroutines.*

class BudgetSetupViewModel(
    auth: AuthRepository,
    connection: BudgetConnectionRepository,
    creation: BudgetCreationRepository,
    local: LocalBudgetCreator,
    family: FamilyBudgetRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    interactor: Interactor<BudgetSetupCommand, BudgetSetupEvent> = BudgetSetupInteractor(auth, connection, creation, local, family, dispatcher),
) : BaseViewModel<BudgetSetupState, BudgetSetupAction, BudgetSetupCommand, BudgetSetupEvent, BudgetSetupEffect>(
    restore(savedState), BudgetSetupReducer(), interactor,
) {
    private var observer: Job? = null
    private var catalog: Job? = null
    private var operation: Job? = null
    private var startedId: Long? = null
    private var generation = state.value.generation
    private var previousPage = state.value.page

    override fun handleAction(action: BudgetSetupAction) {
        val current = state.value
        when (action) {
            BudgetSetupAction.Start -> if (observer == null) observer = execute(BudgetSetupCommand.ObserveSession)
            BudgetSetupAction.Reset -> {
                observer?.cancel(); observer = null
                catalog?.cancel(); operation?.cancel(); startedId = null
                savedState.remove<ArrayList<String>>(SNAPSHOT)
                return
            }
            BudgetSetupAction.Create -> if (current.busy) submit(if (current.mode == BudgetSetupMode.Local) SubmitKind.Local else SubmitKind.Cloud)
            BudgetSetupAction.Join -> if (current.busy) submit(SubmitKind.Join)
            is BudgetSetupAction.Connect -> if (current.busy) submit(SubmitKind.Connect, action.budget)
            BudgetSetupAction.Refresh -> if (current.loading && catalog?.isActive != true) load()
            BudgetSetupAction.Back -> if (!current.busy && previousPage == current.page) emitEffect(BudgetSetupEffect.Exit)
            else -> Unit
        }
        previousPage = current.page
        save()
    }
    override fun handleEvent(event: BudgetSetupEvent) {
        if (generation != state.value.generation) {
            generation = state.value.generation
            operation?.cancel(); catalog?.cancel(); startedId = null
            if (state.value.uid != null) load()
        }
        previousPage = state.value.page
        save()
    }
    private fun load() {
        val current = state.value
        current.uid?.let { catalog = execute(BudgetSetupCommand.Load(it, current.generation)) }
    }
    private fun submit(kind: SubmitKind, budget: AvailableBudget? = null) {
        val current = state.value
        if (startedId == current.operationId) return
        startedId = current.operationId
        operation = execute(BudgetSetupCommand.Submit(current, kind, budget)).also { job ->
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) viewModelScope.launch {
                    onAction(BudgetSetupAction.Cancelled(current.generation, current.operationId))
                }
            }
        }
    }
    // Only unsent form data is saved here. Pending requests are restored from the durable repository.
    private fun save() {
        val s = state.value
        if (!s.initialized) return
        savedState[SNAPSHOT] = arrayListOf(s.name, s.balance, s.mode.name, s.page.name,
            s.ownerUid.orEmpty(), s.authTarget?.name.orEmpty(), s.formOpened.toString(), s.code, s.authLaunched.toString())
    }
    companion object {
        private const val SNAPSHOT = "budget_setup_draft"
        private fun restore(handle: SavedStateHandle): BudgetSetupState {
            val v = handle.get<ArrayList<String>>(SNAPSHOT) ?: return BudgetSetupState()
            return runCatching { BudgetSetupState(name = v[0], balance = v[1], mode = BudgetSetupMode.valueOf(v[2]),
                page = SetupPage.valueOf(v[3]), ownerUid = v[4].ifEmpty { null },
                authTarget = v[5].takeIf { it.isNotEmpty() }?.let(SetupAuthTarget::valueOf),
                formOpened = v[6].toBoolean(), code = v[7], authLaunched = v.getOrNull(8).toBoolean(), initialized = true) }.getOrDefault(BudgetSetupState())
        }
    }
}

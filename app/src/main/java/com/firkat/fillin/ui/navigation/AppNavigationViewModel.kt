package com.firkat.fillin.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppNavigationViewModel(
    private val auth: AuthRepository,
    private val localBudget: LocalBudgetRestorer,
    private val budgetReadiness: ActiveBudgetReadiness,
) : ViewModel() {
    // Activity ownership keeps navigation on rotation; a new process starts at Launch.
    private val entries = mutableStateListOf<AppRoute>(AppRoute.Launch)
    val backStack: List<AppRoute> get() = entries
    private val failure = MutableStateFlow(false)
    val launchFailed = failure.asStateFlow()
    private val resolving = MutableStateFlow(true)
    val resolvingBudget = resolving.asStateFlow()
    private val retries = MutableStateFlow(0)
    private var observer: Job? = null
    private var lastUid: String? = null
    private var budgetReady = false

    /** Called after Launch enters composition, even for an immediately restored user. */
    fun onLaunchShown() {
        if (observer != null) return
        observer = viewModelScope.launch {
            combine(auth.session, retries) { session, _ -> session }.collectLatest { session ->
                resolving.value = true
                budgetReady = false
                if (session == AuthSession.Loading) return@collectLatest
                val authenticated = session is AuthSession.Authenticated
                if (!authenticated && lastUid != null) replaceWith(AppRoute.Auth)
                lastUid = (session as? AuthSession.Authenticated)?.user?.uid
                failure.value = false
                try {
                    if (!authenticated && entries.last() == AppRoute.Launch) {
                        localBudget.restoreCreatedBudget()
                    }
                    var first = true
                    budgetReadiness.observe().distinctUntilChanged().collect { ready ->
                        currentCoroutineContext().ensureActive()
                        if (auth.session.value != session) return@collect
                        budgetReady = ready
                        resolving.value = false
                        when {
                            first && authenticated -> {
                                if (ready) openMain() else openSetup(BudgetSetupMode.Cloud)
                            }
                            entries.last() == AppRoute.Launch -> {
                                if (ready) openMain() else replaceWith(AppRoute.Auth)
                            }
                            ready && entries.last() is AppRoute.BudgetSetup -> openMain()
                            !ready && (entries.last() is AppRoute.EditOperationBottomSheet || entries.last() is AppRoute.PlannedOperationBottomSheet || entries.last() is AppRoute.EditCategoryBottomSheet || entries.last() in listOf(AppRoute.Main, AppRoute.OperationCreate, AppRoute.Categories, AppRoute.FamilyAccess, AppRoute.CloudSync)) -> openSetup(
                                if (authenticated) BudgetSetupMode.Cloud else BudgetSetupMode.Local,
                            )
                        }
                        first = false
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (auth.session.value != session) return@collectLatest
                    failure.value = true
                    resolving.value = false
                    if (entries.last() != AppRoute.Launch) entries.add(AppRoute.Launch)
                }
            }
        }
    }

    fun retryLaunch() { retries.update { it + 1 } }

    fun openAuthentication() {
        if (entries.last() in listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.CloudSync) && auth.session.value !is AuthSession.Authenticated) {
            entries.add(AppRoute.Auth)
        }
    }

    fun openCategories() = openMainDestination(AppRoute.Categories)

    fun openCategoryEditor(categoryId: String? = null) {
        if (entries.last() == AppRoute.Categories && budgetReady && !resolving.value) {
            entries.add(AppRoute.EditCategoryBottomSheet(categoryId))
        }
    }

    fun openFamilyAccess() = openMainDestination(AppRoute.FamilyAccess)

    fun openCloudSync() = openMainDestination(AppRoute.CloudSync)

    fun openOperationCreate() = openMainDestination(AppRoute.OperationCreate)

    fun openEditOperationBottomSheet(
        operationId: String,
        resultKey: String,
        budgetScopeId: String,
    ) = openMainDestination(AppRoute.EditOperationBottomSheet(operationId, resultKey, budgetScopeId))

    /** Ignores callbacks from a sheet that is already exiting or belongs to an old budget. */
    fun dismissEditOperationBottomSheet(route: AppRoute.EditOperationBottomSheet): Boolean {
        if (entries.lastOrNull() != route) return false
        back()
        return true
    }

    fun openPlannedOperationBottomSheet(type: PlannedOperationType, budgetScopeId: String) =
        openMainDestination(AppRoute.PlannedOperationBottomSheet(type, budgetScopeId))

    fun dismissPlannedOperationBottomSheet(route: AppRoute.PlannedOperationBottomSheet) {
        if (entries.lastOrNull() == route) back()
    }

    private fun openMainDestination(route: AppRoute) {
        if (entries.last() == AppRoute.Main && budgetReady && !resolving.value) entries.add(route)
    }

    fun continueAsGuest() {
        if (entries.last() != AppRoute.Auth || resolving.value) return
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Local)
    }

    fun continueWithAccount() {
        if (entries.last() != AppRoute.Auth || resolving.value || auth.session.value !is AuthSession.Authenticated) return
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Cloud)
    }

    fun back() {
        if (entries.size > 1 && entries.last() != AppRoute.Launch) entries.removeAt(entries.lastIndex)
    }

    private fun openSetup(mode: BudgetSetupMode) {
        entries.clear()
        entries.add(AppRoute.Auth)
        entries.add(AppRoute.BudgetSetup(mode))
    }

    private fun openMain() {
        if (AppRoute.Main in entries) {
            while (entries.last() != AppRoute.Main) entries.removeAt(entries.lastIndex)
        } else replaceWith(AppRoute.Main)
    }

    private fun replaceWith(route: AppRoute) {
        entries.clear()
        entries.add(route)
    }
}

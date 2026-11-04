package com.firkat.fillin.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget
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
    private val savedState: SavedStateHandle = SavedStateHandle(),
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
    private var setupActive: Boolean
        get() = savedState["setup_active"] ?: false
        set(value) { savedState["setup_active"] = value }
    private var setupAuth: String?
        get() = savedState["setup_auth"]
        set(value) { savedState["setup_auth"] = value }

    val configuringBudget: Boolean get() = setupActive

    fun openForecast(scopeId: String) = openMainDestination(AppRoute.ForecastDetails(scopeId))

    fun openDailyLimit(scopeId: String) { if (entries.lastOrNull() == AppRoute.Main) entries.add(AppRoute.DailyLimitDetails(scopeId)) }

    fun authenticateForSetup(target: SetupAuthTarget) {
        if (!setupActive || auth.session.value is AuthSession.Authenticated) return
        setupAuth = target.name
        if (entries.last() != AppRoute.Auth) entries.add(AppRoute.Auth)
    }

    fun showSetupPage(page: SetupPage) {
        if (!setupActive || !entries.last().isBudgetSetup()) return
        val route = when (page) {
            SetupPage.Create -> AppRoute.BudgetSetup(if (auth.session.value is AuthSession.Authenticated) BudgetSetupMode.Cloud else BudgetSetupMode.Local)
            SetupPage.List -> AppRoute.BudgetSetupList
            SetupPage.Join -> AppRoute.BudgetSetupJoin
        }
        if (entries.last() != route) entries[entries.lastIndex] = route
    }

    fun exitSetup() { setupActive = false; setupAuth = null; replaceWith(AppRoute.Auth) }
    fun finishSetup() { if (!setupActive || !entries.last().isBudgetSetup()) return; setupActive = false; setupAuth = null; openMain() }
    private fun resumeSetup() {
        val target = setupAuth
        setupAuth = null
        entries.clear()
        entries.add(AppRoute.Auth)
        entries.add(if (target == SetupAuthTarget.Join.name) AppRoute.BudgetSetupJoin else AppRoute.BudgetSetup(BudgetSetupMode.Cloud))
    }

    /** Called after Launch enters composition, even for an immediately restored user. */
    fun onLaunchShown() {
        if (observer != null) return
        observer = viewModelScope.launch {
            combine(auth.session, retries) { session, _ -> session }.collectLatest { session ->
                resolving.value = true
                budgetReady = false
                if (session == AuthSession.Loading) return@collectLatest
                val authenticated = session is AuthSession.Authenticated
                if (!authenticated && lastUid != null) { setupActive = false; setupAuth = null; replaceWith(AppRoute.Auth) }
                val accountChanged = lastUid != (session as? AuthSession.Authenticated)?.user?.uid
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
                            first && setupActive -> {
                                if (authenticated && setupAuth != null) resumeSetup()
                                else if (entries.last() == AppRoute.Launch) {
                                    entries.clear(); entries.add(AppRoute.Auth)
                                    entries.add(AppRoute.BudgetSetup(if (authenticated) BudgetSetupMode.Cloud else BudgetSetupMode.Local))
                                    if (setupAuth != null) entries.add(AppRoute.Auth)
                                }
                            }
                            first && authenticated && (accountChanged || entries.last() in listOf(AppRoute.Launch, AppRoute.Auth)) -> {
                                if (ready) openMain() else openSetup(BudgetSetupMode.Cloud)
                            }
                            entries.last() == AppRoute.Launch -> {
                                if (ready) openMain() else replaceWith(AppRoute.Auth)
                            }
                            !ready && (entries.last() is AppRoute.ForecastDetails || entries.last() is AppRoute.OperationDetails || entries.last() is AppRoute.OperationPlanLinkBottomSheet || entries.last() is AppRoute.PlannedOperationBottomSheet || entries.last() is AppRoute.PlanningRuleEditor || entries.last() is AppRoute.EditCategoryBottomSheet || entries.last() in listOf(AppRoute.Main, AppRoute.OperationCreate, AppRoute.Categories, AppRoute.FamilyAccess, AppRoute.CloudSync)) -> openSetup(
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

    fun authenticateFromPicker() { setupActive = false; setupAuth = null; replaceWith(AppRoute.Auth) }

    fun budgetChosen() { setupActive = false; setupAuth = null; openMain() }

    fun retryLaunch() { retries.update { it + 1 } }

    fun openAuthentication() {
        if (auth.session.value is AuthSession.Authenticated) { openAccountMethods(); return }
        if (entries.last() in listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.CloudSync) && auth.session.value !is AuthSession.Authenticated) {
            entries.add(AppRoute.Auth)
        }
    }

    fun openAccountMethods() { if (entries.last() in listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.CloudSync)) entries.add(AppRoute.AccountMethods) }

    fun openCategories() = openMainDestination(AppRoute.Categories)

    fun openCategoryEditor(categoryId: String? = null) {
        if (entries.last() == AppRoute.Categories && budgetReady && !resolving.value) {
            entries.add(AppRoute.EditCategoryBottomSheet(categoryId))
        }
    }

    fun openFamilyAccess() = openMainDestination(AppRoute.FamilyAccess)

    fun openCloudSync() = openMainDestination(AppRoute.CloudSync)

    fun openOperationCreate() = openMainDestination(AppRoute.OperationCreate)

    fun openOperationDetails(
        operationId: String,
        resultKey: String,
        budgetScopeId: String,
    ) = openMainDestination(AppRoute.OperationDetails(operationId, resultKey, budgetScopeId))

    /** Ignores callbacks from a sheet that is already exiting or belongs to an old budget. */
    fun dismissOperationDetails(route: AppRoute.OperationDetails): Boolean {
        if (entries.lastOrNull() != route) return false
        back()
        return true
    }

    fun operationCreated(result: com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult, scopeId: String) {
        if (entries.lastOrNull() != AppRoute.OperationCreate) return
        back()
        if (result.suggestedPlan != null) openOperationPlanLink(result.operationId, scopeId)
    }

    fun openOperationPlanLink(operationId: String, scopeId: String) =
        openMainDestination(AppRoute.OperationPlanLinkBottomSheet(operationId, scopeId))

    fun openDetailsPlanLink(route: AppRoute.OperationDetails) {
        if (entries.lastOrNull() != route) return
        entries.add(AppRoute.OperationPlanLinkBottomSheet(route.operationId, route.budgetScopeId))
    }

    fun openOperationPlanPicker(route: AppRoute.OperationDetails) {
        if (entries.lastOrNull() == route) entries.add(AppRoute.OperationPlanPicker(route.operationId, route.budgetScopeId))
    }

    fun openDetailsPlanningRule(route: AppRoute.OperationDetails, type: PlannedOperationType, ruleId: String) {
        if (entries.lastOrNull() == route && budgetReady && !resolving.value) {
            entries.add(AppRoute.PlanningRuleEditor(route.budgetScopeId, type, ruleId))
        }
    }

    fun chooseAnotherPlan(route: AppRoute.OperationPlanLinkBottomSheet) {
        if (entries.lastOrNull() != route) return
        val parent = entries.getOrNull(entries.lastIndex - 1) as? AppRoute.OperationDetails
        if (parent?.operationId == route.operationId && parent.budgetScopeId == route.budgetScopeId) back()
        else entries[entries.lastIndex] = AppRoute.OperationDetails(route.operationId, "plan-link-${route.requestId}", route.budgetScopeId)
    }

    fun dismissOperationPlanLink(route: AppRoute.OperationPlanLinkBottomSheet) {
        if (entries.lastOrNull() == route) back()
    }

    fun dismissPlanningRuleEditor(route: AppRoute.PlanningRuleEditor) {
        if (entries.lastOrNull() == route) back()
    }

    fun openPlanningRuleEditor(type: PlannedOperationType, budgetScopeId: String, ruleId: String?) =
        openMainDestination(AppRoute.PlanningRuleEditor(budgetScopeId, type, ruleId))

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
        if (setupActive && setupAuth != null) { setupAuth = null; if (entries.size > 1) entries.removeAt(entries.lastIndex); return }
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Local)
    }

    fun continueWithAccount() {
        if (entries.last() != AppRoute.Auth || resolving.value || auth.session.value !is AuthSession.Authenticated) return
        if (setupActive && setupAuth != null) { resumeSetup(); return }
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Cloud)
    }

    fun back() {
        if (entries.last().isBudgetSetup()) { exitSetup(); return }
        if (setupActive && entries.last() == AppRoute.Auth && setupAuth != null) setupAuth = null
        if (entries.size > 1 && entries.last() != AppRoute.Launch) entries.removeAt(entries.lastIndex)
    }

    private fun openSetup(mode: BudgetSetupMode) {
        setupActive = true
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

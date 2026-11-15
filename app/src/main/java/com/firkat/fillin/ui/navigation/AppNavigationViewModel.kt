package com.firkat.fillin.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import com.firkat.fillin.feature.main.ui.AppDestination
import com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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

internal data class NewOperationPlanOffer(val operationId: String, val scopeId: String, val started: TimeMark)

class AppNavigationViewModel(
    private val auth: AuthRepository,
    private val localBudget: LocalBudgetRestorer,
    private val budgetReadiness: ActiveBudgetReadiness,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
    private val activeBudget: com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager? = null
) : ViewModel() {
    // A new process starts at Launch and restores an unfinished setup after checking readiness.
    private val entries = mutableStateListOf<AppRoute>(AppRoute.Launch)
    val backStack: List<AppRoute> get() = entries
    internal var pendingPlanOffer by mutableStateOf<NewOperationPlanOffer?>(null)
        private set
    private val failure = MutableStateFlow(false)
    val launchFailed = failure.asStateFlow()
    private val resolving = MutableStateFlow(true)
    val resolvingBudget = resolving.asStateFlow()
    private val retries = MutableStateFlow(0)
    private var observer: Job? = null
    private var lastUid: String? = null
    private var budgetReady = false
    internal var mainDestination by mutableStateOf(
        AppDestination.entries.firstOrNull { it.name == savedState.get<String>("main_destination") }
            ?: AppDestination.Dashboard
    )
        private set
    private var setupActive: Boolean
        get() = savedState["setup_active"] ?: false
        set(value) {
            savedState["setup_active"] = value
        }
    private var setupAuth: String?
        get() = savedState["setup_auth"]
        set(value) {
            savedState["setup_auth"] = value
        }
    var setupFromProfile: Boolean
        get() = savedState["setup_from_profile"] ?: false
        private set(value) {
            savedState["setup_from_profile"] = value
        }

    val cloudAllowed get() = access?.cloudAllowed != false
    private val readOnly get() =
        !cloudAllowed &&
            activeBudget
                ?.active
                ?.value
                ?.profile
                ?.firebaseUid != null

    fun closeRestrictedRoutes() {
        if (cloudAllowed) return
        setupAuth = null
        val setupIndex = entries.indexOfFirst { it.isBudgetSetup() }
        if (setupActive && setupIndex >= 0 && entries.last() != AppRoute.Launch) {
            while (entries.lastIndex > setupIndex) entries.removeAt(entries.lastIndex)
            showSetupPage(SetupPage.Create)
            return
        }
        val firstRestricted =
            entries.indexOfFirst {
                it == AppRoute.Auth ||
                    it == AppRoute.AccountMethods ||
                    it == AppRoute.CloudSync ||
                    it == AppRoute.FamilyAccess ||
                    it == AppRoute.BudgetSetupList ||
                    it == AppRoute.BudgetSetupJoin ||
                    (
                        readOnly &&
                            it !is AppRoute.OperationDetails &&
                            it != AppRoute.Categories &&
                            it != AppRoute.Main &&
                            it != AppRoute.Launch &&
                            !it.isBudgetSetup() &&
                            it !is AppRoute.ForecastDetails &&
                            it !is AppRoute.DailyLimitDetails
                        )
            }
        if (firstRestricted == 0) {
            if (budgetReady) openMain() else openSetup(BudgetSetupMode.Local)
        } else if (firstRestricted > 0) {
            while (entries.size > firstRestricted) entries.removeAt(entries.lastIndex)
        }
    }

    val configuringBudget: Boolean get() = setupActive

    internal fun selectMainDestination(
        destination: AppDestination
    ) {
        cancelPlanOffer()
        mainDestination = destination
        savedState["main_destination"] = destination.name
    }

    fun openBudgetCreation() {
        if (entries.lastOrNull() != AppRoute.Main || !budgetReady || resolving.value) return
        selectMainDestination(AppDestination.Dashboard)
        setupFromProfile = true
        setupAuth = null
        setupActive = true
        entries.add(AppRoute.BudgetSetup(BudgetSetupMode.Local))
    }

    fun openForecast(
        scopeId: String
    ) = openMainDestination(AppRoute.ForecastDetails(scopeId))

    fun openDailyLimit(
        scopeId: String
    ) {
        if (entries.lastOrNull() == AppRoute.Main) entries.add(AppRoute.DailyLimitDetails(scopeId))
    }

    fun authenticateForSetup(
        target: SetupAuthTarget
    ) {
        if (!cloudAllowed || !setupActive || auth.session.value is AuthSession.Authenticated) return
        setupAuth = target.name
        if (entries.last() != AppRoute.Auth) entries.add(AppRoute.Auth)
    }

    fun showSetupPage(
        page: SetupPage
    ) {
        if (!setupActive || !entries.last().isBudgetSetup() || (!cloudAllowed && page != SetupPage.Create)) return
        val route =
            when (page) {
                SetupPage.Create -> AppRoute.BudgetSetup(BudgetSetupMode.Local)
                SetupPage.List -> AppRoute.BudgetSetupList
                SetupPage.Join -> AppRoute.BudgetSetupJoin
            }
        if (entries.last() != route) entries[entries.lastIndex] = route
    }

    /** Returns true if the activity should close after leaving initial setup. */
    fun exitSetup(): Boolean {
        if (!setupActive || !entries.last().isBudgetSetup()) return false
        val returnToProfile = setupFromProfile
        clearSetup()
        if (returnToProfile) {
            if (budgetReady) openMain() else openSetup(BudgetSetupMode.Local)
        }
        return !returnToProfile
    }

    fun finishSetup() {
        if (setupActive && entries.last().isBudgetSetup()) budgetChosen()
    }

    private fun clearSetup() {
        setupActive = false
        setupAuth = null
        setupFromProfile = false
    }

    private fun resumeSetup() {
        val target = setupAuth
        setupAuth = null
        showSetupRoot(
            if (target ==
                SetupAuthTarget.Join.name
            ) {
                AppRoute.BudgetSetupJoin
            } else {
                AppRoute.BudgetSetup(BudgetSetupMode.Cloud)
            }
        )
    }

    private fun showSetupRoot(
        route: AppRoute
    ) {
        if (setupFromProfile) {
            openMain()
            entries.add(route)
        } else {
            replaceWith(route)
        }
    }

    /** Called after Launch enters composition, even for an immediately restored user. */
    fun onLaunchShown() {
        if (observer != null) return
        observer =
            viewModelScope.launch {
                combine(auth.session, retries) { session, _ -> session }.collectLatest { session ->
                    resolving.value = true
                    budgetReady = false
                    val authenticated = session is AuthSession.Authenticated
                    if (!authenticated && lastUid != null) {
                        clearSetup()
                        selectMainDestination(AppDestination.Dashboard)
                        replaceWith(AppRoute.Launch)
                    }
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
                                    if (authenticated && setupAuth != null && cloudAllowed) {
                                        resumeSetup()
                                    } else if (setupFromProfile && entries.last() == AppRoute.Launch) {
                                        showSetupRoot(AppRoute.BudgetSetup(BudgetSetupMode.Local))
                                        if (setupAuth != null && cloudAllowed) entries.add(AppRoute.Auth)
                                    } else if (!setupFromProfile && entries.last() == AppRoute.Launch) {
                                        replaceWith(
                                            if (ready) AppRoute.Main else AppRoute.BudgetSetup(BudgetSetupMode.Local)
                                        )
                                        if (ready) {
                                            clearSetup()
                                        } else if (setupAuth != null && cloudAllowed) {
                                            entries.add(AppRoute.Auth)
                                        }
                                    } else if (!setupFromProfile &&
                                        ready &&
                                        setupAuth == null &&
                                        lastUid != null &&
                                        entries.last() == AppRoute.BudgetSetup(BudgetSetupMode.Local)
                                    ) {
                                        budgetChosen()
                                    }
                                }

                                first &&
                                    authenticated &&
                                    (accountChanged || entries.last() in listOf(AppRoute.Launch, AppRoute.Auth)) -> {
                                    if (ready) openMain() else openSetup(BudgetSetupMode.Local)
                                }

                                entries.last() == AppRoute.Launch -> {
                                    if (ready) openMain() else openSetup(BudgetSetupMode.Local)
                                }

                                ready && !setupFromProfile && entries.last().isBudgetSetup() && setupAuth == null -> {
                                    budgetChosen()
                                }

                                !ready &&
                                    (
                                        entries.last() is AppRoute.ForecastDetails ||
                                            entries.last() is AppRoute.OperationDetails ||
                                            entries.last() is AppRoute.OperationPlanLinkBottomSheet ||
                                            entries.last() is AppRoute.PlannedOperationBottomSheet ||
                                            entries.last() is AppRoute.PlanningRuleEditor ||
                                            entries.last() is AppRoute.EditCategory ||
                                            entries.last() in
                                            listOf(
                                                AppRoute.Main,
                                                AppRoute.OperationCreate,
                                                AppRoute.Categories,
                                                AppRoute.FamilyAccess,
                                                AppRoute.CloudSync
                                            )
                                        ) -> {
                                    openSetup(
                                        BudgetSetupMode.Local
                                    )
                                }
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

    private fun budgetChosen() {
        selectMainDestination(AppDestination.Dashboard)
        clearSetup()
        openMain()
    }

    fun retryLaunch() {
        retries.update { it + 1 }
    }

    fun openAuthentication() {
        if (!cloudAllowed) return
        if (auth.session.value is AuthSession.Authenticated) {
            openAccountMethods()
            return
        }
        if (entries.last() in listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.CloudSync) &&
            auth.session.value !is AuthSession.Authenticated
        ) {
            entries.add(AppRoute.Auth)
        }
    }

    fun openAccountMethods() {
        if (cloudAllowed &&
            entries.last() in listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.CloudSync)
        ) {
            entries.add(AppRoute.AccountMethods)
        }
    }

    fun openCategories() {
        openMainDestination(AppRoute.Categories)
    }

    fun openCategoryEditor(
        budgetScopeId: String,
        categoryId: String? = null
    ) {
        if (!readOnly && entries.last() == AppRoute.Categories && budgetReady && !resolving.value) {
            entries.add(AppRoute.EditCategory(budgetScopeId, categoryId))
        }
    }

    fun dismissCategoryEditor(
        route: AppRoute.EditCategory
    ) {
        if (entries.lastOrNull() == route) back()
    }

    fun openFamilyAccess() {
        if (cloudAllowed) openMainDestination(AppRoute.FamilyAccess)
    }

    fun openCloudSync() {
        if (cloudAllowed) openMainDestination(AppRoute.CloudSync)
    }

    fun openOperationCreate() = openMainDestination(AppRoute.OperationCreate)

    fun openOperationDetails(
        operationId: String,
        resultKey: String,
        budgetScopeId: String
    ) = openMainDestination(AppRoute.OperationDetails(operationId, resultKey, budgetScopeId))

    fun openProfile() = openMainDestination(AppRoute.Profile)

    /** Ignores callbacks from a sheet that is already exiting or belongs to an old budget. */
    fun dismissOperationDetails(
        route: AppRoute.OperationDetails
    ): Boolean {
        if (entries.lastOrNull() != route) return false
        back()
        return true
    }

    fun operationCreated(
        result: CreateOperationResult,
        scopeId: String
    ) {
        if (entries.lastOrNull() != AppRoute.OperationCreate) return
        back()
        if (entries.lastOrNull() == AppRoute.Main && !readOnly) {
            pendingPlanOffer = NewOperationPlanOffer(result.operationId, scopeId, TimeSource.Monotonic.markNow())
        }
    }

    internal fun finishPlanOffer(
        request: NewOperationPlanOffer,
        canShow: Boolean
    ) {
        if (pendingPlanOffer !== request) return
        pendingPlanOffer = null
        if (canShow) openMainDestination(AppRoute.OperationPlanLinkBottomSheet(request.operationId, request.scopeId))
    }

    internal fun cancelPlanOffer() {
        pendingPlanOffer = null
    }

    fun openOperationPlanPicker(
        route: AppRoute.OperationDetails
    ) {
        if (!readOnly &&
            entries.lastOrNull() == route
        ) {
            entries.add(AppRoute.OperationPlanPicker(route.operationId, route.budgetScopeId))
        }
    }

    fun openDetailsPlanningRule(
        route: AppRoute.OperationDetails,
        type: PlannedOperationType,
        ruleId: String
    ) {
        if (!readOnly && entries.lastOrNull() == route && budgetReady && !resolving.value) {
            entries.add(AppRoute.PlanningRuleEditor(route.budgetScopeId, type, ruleId))
        }
    }

    fun dismissOperationPlanLink(
        route: AppRoute.OperationPlanLinkBottomSheet
    ): Boolean {
        if (entries.lastOrNull() != route) return false
        back()
        return true
    }

    fun dismissPlanningRuleEditor(
        route: AppRoute.PlanningRuleEditor
    ) {
        if (entries.lastOrNull() == route) back()
    }

    fun openPlanningRuleEditor(
        type: PlannedOperationType,
        budgetScopeId: String,
        ruleId: String?
    ) = openMainDestination(AppRoute.PlanningRuleEditor(budgetScopeId, type, ruleId))

    fun openPlannedOperationBottomSheet(
        type: PlannedOperationType,
        budgetScopeId: String
    ) = openMainDestination(AppRoute.PlannedOperationBottomSheet(type, budgetScopeId))

    fun dismissPlannedOperationBottomSheet(
        route: AppRoute.PlannedOperationBottomSheet
    ) {
        if (entries.lastOrNull() == route) back()
    }

    private fun openMainDestination(
        route: AppRoute
    ) {
        cancelPlanOffer()
        if (readOnly &&
            route !is AppRoute.ForecastDetails &&
            route !is AppRoute.DailyLimitDetails &&
            route !is AppRoute.OperationDetails &&
            route != AppRoute.Categories
        ) {
            return
        }
        if (entries.last() == AppRoute.Main && budgetReady && !resolving.value) entries.add(route)
    }

    fun continueAsGuest() {
        if (entries.last() != AppRoute.Auth || resolving.value) return
        if (setupActive && setupAuth != null) {
            setupAuth = null
            if (entries.size > 1) entries.removeAt(entries.lastIndex)
            return
        }
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Local)
    }

    fun continueWithAccount() {
        if (entries.last() != AppRoute.Auth ||
            resolving.value ||
            auth.session.value !is AuthSession.Authenticated
        ) {
            return
        }
        if (setupActive && setupAuth != null) {
            resumeSetup()
            return
        }
        if (budgetReady) openMain() else openSetup(BudgetSetupMode.Cloud)
    }

    fun back() {
        cancelPlanOffer()
        if (entries.last().isBudgetSetup()) {
            exitSetup()
            return
        }
        if (setupActive && entries.last() == AppRoute.Auth && setupAuth != null) setupAuth = null
        if (entries.size > 1 && entries.last() != AppRoute.Launch) entries.removeAt(entries.lastIndex)
    }

    private fun openSetup(
        mode: BudgetSetupMode
    ) {
        clearSetup()
        setupActive = true
        replaceWith(AppRoute.BudgetSetup(mode))
    }

    private fun openMain() {
        cancelPlanOffer()
        if (AppRoute.Main in entries) {
            while (entries.last() != AppRoute.Main) entries.removeAt(entries.lastIndex)
        } else {
            replaceWith(AppRoute.Main)
        }
    }

    private fun replaceWith(
        route: AppRoute
    ) {
        cancelPlanOffer()
        entries.clear()
        entries.add(route)
    }
}

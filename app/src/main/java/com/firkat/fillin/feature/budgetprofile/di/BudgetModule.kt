package com.firkat.fillin.feature.budgetprofile.di

import com.firkat.fillin.feature.plannedoperations.data.repository.OfflinePlanningRulesRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlanningRulesRepository
import com.firkat.fillin.feature.budgetconnection.data.FirebaseCloudBudgetApi
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.categories.data.repository.OfflineCategoriesRepository
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import com.firkat.fillin.feature.categories.ui.CategoriesViewModel
import com.firkat.fillin.feature.dashboard.data.repository.OfflineDashboardRepository
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.dashboard.ui.DashboardViewModel
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain.ObserveAvailableToSpend
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendViewModel
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastViewModel
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitViewModel
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain.ObserveTotalBalance
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceViewModel
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncRequests
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdateOperationAmount
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdateOperation
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryViewModel
import com.firkat.fillin.feature.plannedexpenses.data.repository.OfflinePlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.ClosePlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.CreatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.DeletePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.UpdatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedincomes.data.repository.OfflinePlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.CreatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.DeletePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.usecase.UpdatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedoperations.data.repository.OfflinePlannedOperationsRepository
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.GetPlannedOperations
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsViewModel
import com.firkat.fillin.feature.sync.data.CloudBudgetApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase
import com.firkat.fillin.feature.sync.data.BudgetCurrencyOverride
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val BUDGET_SCOPE = named("budget")

val budgetModule = module {
    scope(BUDGET_SCOPE) {
        scoped { getSource<BudgetProfile>() }
        scoped<DashboardRepository> { OfflineDashboardRepository(get(), get()) }
        scoped {
            DashboardDataSource(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        } onClose { it?.close() }
        viewModel { DashboardViewModel(get()) }
        factory { ObserveTotalBalance(get()) }
        viewModel { TotalBalanceViewModel(get()) }
        factory { ObserveAvailableToSpend(get()) }
        viewModel { AvailableToSpendViewModel(get()) }
        factory { ObserveDailyLimit(get()) }
        viewModel { DailyLimitViewModel(get()) }
        factory { ObserveBalanceForecast(get()) }
        viewModel { BalanceForecastViewModel(get()) }
        viewModel { com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastDetailsViewModel(get(), androidx.lifecycle.SavedStateHandle()) }
        scoped<OperationHistoryRepository>(named("remoteOperations")) {
            com.firkat.fillin.feature.sync.data.RetiredOperationHistory
        }
        scoped { get<BudgetDatabaseFactory>().open(get<BudgetProfile>()) } onClose { it?.close() }
        scoped<CloudBudgetApi> {
            val profile = get<BudgetProfile>()
            if (profile.kind == BudgetProfileKind.CLOUD) FirebaseCloudBudgetApi(profile, get(), upload = { get<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore>().find(profile.id) }, json = get())
            else getKoin().get<CloudBudgetApi>()
        }
        scoped {
            com.firkat.fillin.feature.sync.data
                .CloudBudgetSession(get(), get(), "",
                    get(), OperationSyncScheduler { get<OperationSyncScheduler>().schedule() },
                    checkAccess = {
                        if (!get<ActiveBudgetManager>().isCurrent(get<BudgetProfile>())) throw com.firkat.fillin.feature.sync.data.CloudDisabledException()
                        if (get<BudgetProfile>().kind != BudgetProfileKind.CLOUD) throw com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException("sign_in_required")
                    }, firebase = get<BudgetProfile>().kind == BudgetProfileKind.CLOUD)
        }
        scoped<com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository> { get<com.firkat.fillin.feature.sync.data.CloudBudgetSession>() }
        scoped<com.firkat.fillin.feature.sync.domain.CloudSyncSettings> { get<com.firkat.fillin.feature.sync.data.CloudBudgetSession>() }
        factory {
            com.firkat.fillin.feature.budgetsetup.domain
                .CreateLocalBudget(get())
        }
        scoped<OperationSyncStorage> { RoomOperationSyncStorage(get(), get()) }
        scoped {
            val profile = get<BudgetProfile>()
            val uid = profile.firebaseUid
            val budgetId = profile.budgetId
            val currencyOverride = if (uid == null || budgetId == null) {
                flowOf(BudgetCurrencyOverride())
            } else {
                get<BudgetProfileDatabase>().management().observeCurrency(uid, budgetId)
            }
            com.firkat.fillin.feature.sync.data.BudgetStore(
                get(),
                OperationSyncScheduler {
                    get<OperationSyncScheduler>().schedule()
                },
                get(),
                currencyOverride = currencyOverride,
            )
        }
        scoped<com.firkat.fillin.feature.sync.domain.BudgetSyncControl> {
            get<com.firkat.fillin.feature.sync.data.BudgetStore>()
        }
        viewModel {
            com.firkat.fillin.feature.sync.ui
                .BudgetSyncViewModel(get(), get())
        }
        scoped { OperationSyncRequests(synchronize = {
            val lease = get<ActiveBudgetManager>().acquire(get<BudgetProfile>().syncIdentity)
            if (lease == null) SyncResult.Completed else try {
                lease.scope.get<OperationsSynchronizer>().synchronize().also { getKoin().getOrNull<com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement>()?.schedule() }
            } finally { lease.close() }
        }) } onClose { it?.close() }
        scoped<OperationSyncScheduler> { WorkManagerOperationSyncScheduler(androidContext(), get<BudgetProfile>().syncIdentity, get()) }
        scoped {
            OperationsSynchronizer(get(), get(), "",
                get(), budget = get(), budgetApi = get(), cloud = get(), cloudApi = get(), canSynchronize = { get<ActiveBudgetManager>().isCurrent(get<BudgetProfile>()) })
        }
        scoped {
            OfflineOperationHistoryRepository(
                get(),
                get(named("remoteOperations")),
                get(),
                get(),
                get(),
                get(),
                budgetStore = get(),
            )
        }
        scoped<com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkStore> { get<com.firkat.fillin.feature.sync.data.BudgetStore>() }
        viewModel { parameters -> com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkViewModel(parameters.get(), get(), get()) }
        viewModel { parameters -> com.firkat.fillin.feature.operationhistory.ui.OperationPlanPickerViewModel(parameters.get(), get(), get(), get()) }
        scoped<OperationHistoryStore> { get<OfflineOperationHistoryRepository>() }
        scoped<OperationHistoryRepository> { get<OfflineOperationHistoryRepository>() }
        factory { UpdateOperationAmount(repository = get()) }
        factory { UpdateOperation(repository = get()) }
        factory { RefundPurchase(repository = get()) }
        viewModel { OperationHistoryViewModel(history = get()) }
        viewModel { com.firkat.fillin.feature.operationhistory.ui.OperationCreateViewModel(history = get()) }
        viewModel { parameters ->
            com.firkat.fillin.feature.operationhistory.ui.EditOperationViewModel(parameters.get(), get(), get(), get(), links = get(), rules = get())
        }
        scoped<PlannedExpensesRepository> {
            OfflinePlannedExpensesRepository(get())
        }
        factory { GetPlannedExpenses(repository = get()) }
        factory { CreatePlannedExpenseRule(repository = get()) }
        factory { UpdatePlannedExpenseRule(repository = get()) }
        factory { DeletePlannedExpenseRule(repository = get()) }
        factory { ClosePlannedExpense(repository = get()) }
        viewModel { parameters ->
            PlannedExpensesViewModel(
                getPlannedExpenses = get(),
                getCategories = get(),
                createRule = get(),
                updateRule = get(),
                deleteRule = get(),
                closeExpense = get(),
                editorOnly = parameters.getOrNull<Boolean>() ?: false,
            )
        }
        scoped<PlannedIncomesRepository> {
            OfflinePlannedIncomesRepository(get())
        }
        factory { GetPlannedIncomes(repository = get()) }
        factory { CreatePlannedIncome(repository = get()) }
        factory { UpdatePlannedIncome(repository = get()) }
        factory { DeletePlannedIncome(repository = get()) }
        viewModel { parameters ->
            PlannedIncomesViewModel(
                repository = get(),
                getPlannedIncomes = get(),
                getCategories = get(),
                createPlannedIncome = get(),
                updatePlannedIncome = get(),
                deletePlannedIncome = get(),
                editorOnly = parameters.getOrNull<Boolean>() ?: false,
            )
        }
        scoped<PlannedOperationsRepository> {
            OfflinePlannedOperationsRepository(get())
        }
        factory { GetPlannedOperations(repository = get()) }
        scoped<PlanningRulesRepository> {
            OfflinePlanningRulesRepository(get())
        }
        factory { ObservePlanningRules(get()) }
        viewModel { (type: com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType, ruleId: String?) -> com.firkat.fillin.feature.plannedoperations.ui.PlanningRuleEditorViewModel(get(), get(), get(), get(), type, ruleId) }
        viewModel { PlannedOperationsViewModel(observeRules = get(), savedStateHandle = get()) }
        scoped<CategoriesRepository> {
            OfflineCategoriesRepository(get())
        }
        factory { GetCategories(repository = get()) }
        factory { CreateCategory(repository = get()) }
        factory { UpdateCategory(repository = get()) }
        factory { DeleteCategory(repository = get()) }
        viewModel {
            CategoriesViewModel(
                getCategories = get(),
                createCategory = get(),
                updateCategory = get(),
                deleteCategory = get(),
            )
        }
    }
}

package com.firkat.fillin.core.di

import androidx.room.Room
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.repository.CategoriesRepositoryImpl
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import com.firkat.fillin.feature.categories.ui.CategoriesViewModel
import com.firkat.fillin.feature.dashboard.data.repository.OfflineDashboardRepository
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.ui.DashboardViewModel
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.repository.OperationHistoryRepositoryImpl
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncRequests
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryViewModel
import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.repository.PlannedExpensesRepositoryImpl
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.ClosePlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.CreatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.DeletePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.UpdatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.repository.PlannedIncomesRepositoryImpl
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.CreatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.DeletePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.usecase.UpdatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedoperations.data.remote.PlannedOperationsApi
import com.firkat.fillin.feature.plannedoperations.data.repository.PlannedOperationsRepositoryImpl
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.GetPlannedOperations
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsViewModel
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain.ObserveTotalBalance
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceViewModel
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain.ObserveAvailableToSpend
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendViewModel
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitViewModel
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastViewModel
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.domain.ObserveMonthlyBudget
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui.MonthlyBudgetViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest

val appModule =
    module {
        includes(com.firkat.fillin.feature.auth.di.authModule)
        single {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        single {
            Retrofit
                .Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .addConverterFactory(
                    get<Json>().asConverterFactory("application/json".toMediaType()),
                ).build()
        }
        single<OperationHistoryApi> { get<Retrofit>().create(OperationHistoryApi::class.java) }
        single<PlannedExpensesApi> { get<Retrofit>().create(PlannedExpensesApi::class.java) }
        single<PlannedIncomesApi> { get<Retrofit>().create(PlannedIncomesApi::class.java) }
        single<PlannedOperationsApi> { get<Retrofit>().create(PlannedOperationsApi::class.java) }
        single<CategoriesApi> { get<Retrofit>().create(CategoriesApi::class.java) }
        single<DashboardRepository> { OfflineDashboardRepository(get(), get(), get()) }
        single {
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
        factory { ObserveMonthlyBudget(get()) }
        viewModel { MonthlyBudgetViewModel(get()) }
        single<OperationHistoryRepository>(named("remoteOperations")) {
            OperationHistoryRepositoryImpl(
                api = get(),
                apiToken = BuildConfig.MOBILE_API_TOKEN,
            )
        }
        single {
            val backend =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(BuildConfig.API_BASE_URL.toByteArray())
                    .take(8)
                    .joinToString("") { "%02x".format(it) }
            Room
                .databaseBuilder(androidContext(), OperationDatabase::class.java, "operations-$backend.db")
                .addMigrations(
                    com.firkat.fillin.core.database.BUDGET_MIGRATION_1_2,
                    com.firkat.fillin.core.database.BUDGET_MIGRATION_2_3,
                    com.firkat.fillin.core.database.BUDGET_MIGRATION_3_4,
                ).build()
        }
        single<com.firkat.fillin.feature.sync.data.CloudBudgetApi> { get<Retrofit>().create(com.firkat.fillin.feature.sync.data.CloudBudgetApi::class.java) }
        single {
            com.firkat.fillin.feature.sync.data
                .CloudBudgetSession(get(), get(), BuildConfig.MOBILE_API_TOKEN, get(), OperationSyncScheduler { get<OperationSyncScheduler>().schedule() })
        }
        single<com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository> { get<com.firkat.fillin.feature.sync.data.CloudBudgetSession>() }
        single<com.firkat.fillin.feature.sync.domain.CloudSyncSettings> { get<com.firkat.fillin.feature.sync.data.CloudBudgetSession>() }
        factory {
            com.firkat.fillin.feature.budgetsetup.domain
                .CreateLocalBudget(get())
        }
        factory {
            com.firkat.fillin.feature.budgetsetup.domain
                .ConnectExistingBudget(get())
        }
        viewModel {
            com.firkat.fillin.feature.budgetsetup.ui
                .BudgetSetupViewModel(get(), get(), get())
        }
        single<OperationSyncApi> { get<Retrofit>().create(OperationSyncApi::class.java) }
        single<OperationSyncStorage> { RoomOperationSyncStorage(get(), get()) }
        single<com.firkat.fillin.feature.sync.data.BudgetSyncApi> {
            get<Retrofit>().create(com.firkat.fillin.feature.sync.data.BudgetSyncApi::class.java)
        }
        single {
            com.firkat.fillin.feature.sync.data.BudgetStore(
                get(),
                OperationSyncScheduler {
                    get<OperationSyncScheduler>().schedule()
                },
                get(),
            )
        }
        single<com.firkat.fillin.feature.sync.domain.BudgetSyncControl> {
            get<com.firkat.fillin.feature.sync.data.BudgetStore>()
        }
        viewModel {
            com.firkat.fillin.feature.sync.ui
                .BudgetSyncViewModel(get(), get())
        }
        single { OperationSyncRequests(get<OperationsSynchronizer>()::synchronize) }
        single<OperationSyncScheduler> { WorkManagerOperationSyncScheduler(androidContext(), get(), get()) }
        single { AndroidOperationConnectivity(androidContext(), get()) }
        single<OperationConnectivity> { get<AndroidOperationConnectivity>() }
        single {
            OperationsSynchronizer(get(), get(), BuildConfig.MOBILE_API_TOKEN, get(), budget = get(), budgetApi = get(), cloud = get(), cloudApi = get())
        }
        single { OperationWorkerFactory { get() } }
        single {
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
        single<OperationHistoryStore> { get<OfflineOperationHistoryRepository>() }
        single<OperationHistoryRepository> { get<OfflineOperationHistoryRepository>() }
        factory { UpdatePurchaseAmount(repository = get()) }
        factory { RefundPurchase(repository = get()) }
        viewModel {
            OperationHistoryViewModel(
                history = get(),
                updatePurchaseAmount = get(),
                refundPurchase = get(),
            )
        }
        single<PlannedExpensesRepository> {
            com.firkat.fillin.feature.plannedexpenses.data.repository
                .OfflinePlannedExpensesRepository(get())
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
        single<PlannedIncomesRepository> {
            com.firkat.fillin.feature.plannedincomes.data.repository
                .OfflinePlannedIncomesRepository(get())
        }
        factory { GetPlannedIncomes(repository = get()) }
        factory { CreatePlannedIncome(repository = get()) }
        factory { UpdatePlannedIncome(repository = get()) }
        factory { DeletePlannedIncome(repository = get()) }
        viewModel { parameters ->
            PlannedIncomesViewModel(
                getPlannedIncomes = get(),
                getCategories = get(),
                createPlannedIncome = get(),
                updatePlannedIncome = get(),
                deletePlannedIncome = get(),
                editorOnly = parameters.getOrNull<Boolean>() ?: false,
            )
        }
        single<PlannedOperationsRepository> {
            com.firkat.fillin.feature.plannedoperations.data.repository
                .OfflinePlannedOperationsRepository(get())
        }
        factory { GetPlannedOperations(repository = get()) }
        viewModel { PlannedOperationsViewModel(getPlannedOperations = get()) }
        single<CategoriesRepository> {
            com.firkat.fillin.feature.categories.data.repository
                .OfflineCategoriesRepository(get())
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

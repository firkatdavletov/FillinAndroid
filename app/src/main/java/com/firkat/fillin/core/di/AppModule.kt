package com.firkat.fillin.core.di

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.repository.CategoriesRepositoryImpl
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import com.firkat.fillin.feature.categories.ui.CategoriesViewModel
import com.firkat.fillin.feature.dashboard.data.remote.DashboardApi
import com.firkat.fillin.feature.dashboard.data.repository.DashboardRepositoryImpl
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.dashboard.domain.usecase.GetDashboard
import com.firkat.fillin.feature.dashboard.ui.DashboardViewModel
import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.repository.PlannedIncomesRepositoryImpl
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.CreatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.DeletePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.usecase.UpdatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.repository.PlannedExpensesRepositoryImpl
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.ClosePlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.CreatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.DeletePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.UpdatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedoperations.data.remote.PlannedOperationsApi
import com.firkat.fillin.feature.plannedoperations.data.repository.PlannedOperationsRepositoryImpl
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.GetPlannedOperations
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsViewModel
import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.data.repository.OperationHistoryRepositoryImpl
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationHistoryPage
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(
                get<Json>().asConverterFactory("application/json".toMediaType()),
            )
            .build()
    }
    single<OperationHistoryApi> { get<Retrofit>().create(OperationHistoryApi::class.java) }
    single<PlannedExpensesApi> { get<Retrofit>().create(PlannedExpensesApi::class.java) }
    single<PlannedIncomesApi> { get<Retrofit>().create(PlannedIncomesApi::class.java) }
    single<PlannedOperationsApi> { get<Retrofit>().create(PlannedOperationsApi::class.java) }
    single<CategoriesApi> { get<Retrofit>().create(CategoriesApi::class.java) }
    single<DashboardApi> { get<Retrofit>().create(DashboardApi::class.java) }
    single<DashboardRepository> {
        DashboardRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetDashboard(repository = get()) }
    viewModel { DashboardViewModel(getDashboard = get()) }
    single<OperationHistoryRepository> {
        OperationHistoryRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetOperationHistoryPage(repository = get()) }
    factory { GetOperationPeriodTotals(repository = get()) }
    factory { UpdatePurchaseAmount(repository = get()) }
    factory { RefundPurchase(repository = get()) }
    viewModel {
        OperationHistoryViewModel(
            getOperationHistoryPage = get(),
            getOperationPeriodTotals = get(),
            updatePurchaseAmount = get(),
            refundPurchase = get(),
        )
    }
    single<PlannedExpensesRepository> {
        PlannedExpensesRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
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
        PlannedIncomesRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
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
        PlannedOperationsRepositoryImpl(api = get(), apiToken = BuildConfig.MOBILE_API_TOKEN)
    }
    factory { GetPlannedOperations(repository = get()) }
    viewModel { PlannedOperationsViewModel(getPlannedOperations = get()) }
    single<CategoriesRepository> {
        CategoriesRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
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

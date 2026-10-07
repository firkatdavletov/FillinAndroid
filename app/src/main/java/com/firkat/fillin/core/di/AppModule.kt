package com.firkat.fillin.core.di

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.repository.PlannedIncomesRepositoryImpl
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.repository.PlannedExpensesRepositoryImpl
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.purchasehistory.data.remote.PurchaseHistoryApi
import com.firkat.fillin.feature.purchasehistory.data.repository.PurchaseHistoryRepositoryImpl
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import com.firkat.fillin.feature.purchasehistory.ui.PurchaseHistoryViewModel
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
    single<PurchaseHistoryApi> { get<Retrofit>().create(PurchaseHistoryApi::class.java) }
    single<PlannedExpensesApi> { get<Retrofit>().create(PlannedExpensesApi::class.java) }
    single<PlannedIncomesApi> { get<Retrofit>().create(PlannedIncomesApi::class.java) }
    single<PurchaseHistoryRepository> {
        PurchaseHistoryRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetPurchaseHistoryPage(repository = get()) }
    viewModel { PurchaseHistoryViewModel(getPurchaseHistoryPage = get()) }
    single<PlannedExpensesRepository> {
        PlannedExpensesRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetPlannedExpenses(repository = get()) }
    viewModel { PlannedExpensesViewModel(getPlannedExpenses = get()) }
    single<PlannedIncomesRepository> {
        PlannedIncomesRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetPlannedIncomes(repository = get()) }
    viewModel { PlannedIncomesViewModel(getPlannedIncomes = get()) }
}

package com.firkat.fillin.core.di

import androidx.room.Room
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedoperations.data.remote.PlannedOperationsApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule =
    module {
        includes(com.firkat.fillin.feature.auth.di.authModule, com.firkat.fillin.feature.budgetprofile.di.budgetModule, com.firkat.fillin.feature.budgetconnection.di.budgetConnectionModule)
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
        single<com.firkat.fillin.feature.sync.data.CloudBudgetApi> { get<Retrofit>().create(com.firkat.fillin.feature.sync.data.CloudBudgetApi::class.java) }
        single<OperationSyncApi> { get<Retrofit>().create(OperationSyncApi::class.java) }
        single<com.firkat.fillin.feature.sync.data.BudgetSyncApi> { get<Retrofit>().create(com.firkat.fillin.feature.sync.data.BudgetSyncApi::class.java) }
        single { com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory(androidContext()) }
        single { Room.databaseBuilder(androidContext(), com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase::class.java, "budget-profiles.db").build() } onClose { it?.close() }
        single<com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry> {
            com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry(get(), BuildConfig.API_BASE_URL)
        }
        single {
            com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager(get(), get(), getKoin()) { identity ->
                androidx.work.WorkManager.getInstance(androidContext()).cancelUniqueWork(com.firkat.fillin.feature.budgetprofile.data.BudgetWorkIdentity.uniqueName(identity))
            }
        } onClose { it?.close() }
        single<OperationSyncScheduler> {
            OperationSyncScheduler {
                val manager = get<com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager>()
                manager.active.value?.profile?.syncIdentity?.let {
                    WorkManagerOperationSyncScheduler(androidContext(), it, manager).schedule()
                }
            }
        }
        single { AndroidOperationConnectivity(androidContext(), get()) }
        single<OperationConnectivity> { get<AndroidOperationConnectivity>() }
        single { OperationWorkerFactory(get()) }
    }

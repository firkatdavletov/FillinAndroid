package com.firkat.fillin.core.di

import androidx.room.Room
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.core.module.dsl.viewModel
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule =
    module {
        single<com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer> {
            com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer(get(), get(), get())
        }
        single<com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness> {
            com.firkat.fillin.feature.budgetsetup.data.RoomActiveBudgetReadiness(get())
        }
        viewModel { com.firkat.fillin.ui.navigation.AppNavigationViewModel(get(), get(), get(), get()) }
        includes(com.firkat.fillin.feature.auth.di.authModule, com.firkat.fillin.feature.budgetprofile.di.budgetModule, com.firkat.fillin.feature.budgetconnection.di.budgetConnectionModule, com.firkat.fillin.feature.profile.di.profileModule)
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
        single<com.firkat.fillin.feature.sync.data.CloudBudgetApi> { com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi }
        single<OperationSyncApi> { com.firkat.fillin.feature.sync.data.RetiredOperationSyncApi }
        single<com.firkat.fillin.feature.sync.data.BudgetSyncApi> { com.firkat.fillin.feature.sync.data.RetiredBudgetSyncApi }
        single { com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory(androidContext()) }
        single { Room.databaseBuilder(androidContext(), com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase::class.java, "budget-profiles.db").addMigrations(com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_1_2, com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_2_3, com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_3_4, com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_4_5, com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_5_6).build() } onClose { it?.close() }
        single<com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry> {
            com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry(get(), BuildConfig.API_BASE_URL)
        }
        single {
            com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager(get(), get(), getKoin(), blocked = { uid -> get<com.firkat.fillin.feature.profile.domain.DeletionStore>().blocked(uid) }) { identity ->
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

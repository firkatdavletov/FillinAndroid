package com.firkat.fillin.core.di

import androidx.room.Room
import android.os.SystemClock
import com.firkat.fillin.feature.appaccess.data.AppAccessApi
import com.firkat.fillin.feature.appaccess.data.BackendAppAccess
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.data.CloudAccessInterceptor
import java.io.File
import org.koin.core.qualifier.named
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.core.network.BackendHeadersInterceptor
import com.firkat.fillin.core.network.InstallationIdStore
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
        viewModel { com.firkat.fillin.ui.navigation.AppNavigationViewModel(get(), get(), get(), get(), get(), get()) }
        includes(com.firkat.fillin.feature.auth.di.authModule, com.firkat.fillin.feature.budgetprofile.di.budgetModule, com.firkat.fillin.feature.budgetconnection.di.budgetConnectionModule, com.firkat.fillin.feature.profile.di.profileModule)
        single {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        single { InstallationIdStore(androidContext().noBackupFilesDir) }
        single {
            val installationIdStore = get<InstallationIdStore>()
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(BackendHeadersInterceptor(installationIdStore::get, BuildConfig.VERSION_NAME))
                .build()
        }
        single {
            Retrofit
                .Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(get<OkHttpClient>())
                .addConverterFactory(
                    get<Json>().asConverterFactory("application/json".toMediaType()),
                ).build()
        }
        single<AppAccess> {
            val backend = BuildConfig.FIREBASE_API_BASE_URL
            val api = backend.takeIf(String::isNotBlank)?.let {
                get<Retrofit>().newBuilder().baseUrl(it).build().create(AppAccessApi::class.java)
            }
            BackendAppAccess(api, backend, get<InstallationIdStore>()::get,
                File(androidContext().noBackupFilesDir, "app-access"), SystemClock::elapsedRealtime)
        }
        single<OkHttpClient>(named("cloud")) {
            val koin = getKoin()
            get<OkHttpClient>().newBuilder()
                .dispatcher(okhttp3.Dispatcher())
                .addInterceptor(CloudAccessInterceptor { koin.get() })
                .build()
        }
        single<com.firkat.fillin.feature.sync.data.CloudBudgetApi> { com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi }
        single<OperationSyncApi> { com.firkat.fillin.feature.sync.data.RetiredOperationSyncApi }
        single<com.firkat.fillin.feature.sync.data.BudgetSyncApi> { com.firkat.fillin.feature.sync.data.RetiredBudgetSyncApi }
        single { com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory(androidContext()) }
        single { Room.databaseBuilder(androidContext(), com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase::class.java, "budget-profiles.db").addMigrations(*com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATIONS).build() } onClose { it?.close() }
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
                    WorkManagerOperationSyncScheduler(androidContext(), it, manager, get()).schedule()
                }
            }
        }
        single { AndroidOperationConnectivity(androidContext(), get()) }
        single<OperationConnectivity> { get<AndroidOperationConnectivity>() }
        single { OperationWorkerFactory(get()) }
    }

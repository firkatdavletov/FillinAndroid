package com.firkat.fillin.core.di

import android.util.Log
import androidx.room.Room
import androidx.work.WorkManager
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.analytics.di.analyticsModule
import com.firkat.fillin.core.network.BackendHeadersInterceptor
import com.firkat.fillin.core.network.InstallationIdStore
import com.firkat.fillin.feature.appaccess.data.CloudAccessInterceptor
import com.firkat.fillin.feature.appaccess.data.RemoteConfigAppAccess
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.auth.di.authModule
import com.firkat.fillin.feature.budgetconnection.di.budgetConnectionModule
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase
import com.firkat.fillin.feature.budgetprofile.data.BudgetWorkIdentity
import com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATIONS
import com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry
import com.firkat.fillin.feature.budgetprofile.di.budgetModule
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry
import com.firkat.fillin.feature.budgetsetup.data.RoomActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgettransfer.di.budgetTransferModule
import com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.WorkManagerOperationSyncScheduler
import com.firkat.fillin.feature.profile.di.profileModule
import com.firkat.fillin.feature.profile.domain.DeletionStore
import com.firkat.fillin.feature.remoteconfig.data.FirebaseAppRemoteConfig
import com.firkat.fillin.feature.remoteconfig.domain.AppRemoteConfig
import com.firkat.fillin.feature.sync.data.BudgetSyncApi
import com.firkat.fillin.feature.sync.data.CloudBudgetApi
import com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi
import com.firkat.fillin.feature.sync.data.RetiredBudgetSyncApi
import com.firkat.fillin.feature.sync.data.RetiredOperationSyncApi
import com.firkat.fillin.ui.navigation.AppNavigationViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule =
    module {
        includes(
            analyticsModule,
            authModule,
            budgetModule,
            budgetConnectionModule,
            budgetTransferModule,
            profileModule
        )
        viewModel {
            AppNavigationViewModel(
                auth = get(),
                localBudget = get(),
                budgetReadiness = get(),
                savedState = get(),
                access = get(),
                activeBudget = get()
            )
        }
        single<LocalBudgetRestorer> {
            RoomLocalBudgetRestorer(
                registry = get(),
                databases = get(),
                manager = get()
            )
        }
        single<ActiveBudgetReadiness> {
            RoomActiveBudgetReadiness(
                manager = get()
            )
        }
        single {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        single {
            if (BuildConfig.FIREBASE_CONFIGURED) {
                FirebaseAppRemoteConfig.create(debug = BuildConfig.DEBUG)
            } else {
                FirebaseAppRemoteConfig()
            }
        }
        single<AppRemoteConfig> { get<FirebaseAppRemoteConfig>() }
        single {
            InstallationIdStore(
                directory = androidContext().noBackupFilesDir
            )
        }
        single {
            val installationIdStore = get<InstallationIdStore>()
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(
                    BackendHeadersInterceptor(
                        installationId = installationIdStore::get,
                        appVersion = BuildConfig.VERSION_NAME
                    )
                )
                .build()
        }
        single {
            Retrofit
                .Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(get<OkHttpClient>())
                .addConverterFactory(
                    get<Json>().asConverterFactory("application/json".toMediaType())
                ).build()
        }
        single {
            RemoteConfigAppAccess(
                remoteConfig = get(),
                configured = BuildConfig.FIREBASE_CONFIGURED,
                logFailure = { Log.w("AppRemoteConfig", "Remote Config fetch failed", it) }
            )
        }
        single<AppAccess> { get<RemoteConfigAppAccess>() }
        single<OkHttpClient>(named("cloud")) {
            val koin = getKoin()
            get<OkHttpClient>().newBuilder()
                .dispatcher(okhttp3.Dispatcher())
                .addInterceptor(CloudAccessInterceptor { koin.get() })
                .build()
        }
        single<CloudBudgetApi> {
            LocalOnlyCloudApi
        }
        single<OperationSyncApi> {
            RetiredOperationSyncApi
        }
        single<BudgetSyncApi> {
            RetiredBudgetSyncApi
        }
        single {
            BudgetDatabaseFactory(androidContext())
        }
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    BudgetProfileDatabase::class.java,
                    "budget-profiles.db"
                )
                .addMigrations(*PROFILE_MIGRATIONS).build()
        } onClose { it?.close() }
        single<BudgetProfileRegistry> {
            RoomBudgetProfileRegistry(get(), BuildConfig.API_BASE_URL)
        }
        single {
            ActiveBudgetManager(
                registry = get(),
                auth = get(),
                koin = getKoin(),
                blocked = { uid ->
                    get<DeletionStore>().blocked(uid)
                }
            ) { identity ->
                WorkManager
                    .getInstance(
                        context = androidContext()
                    )
                    .cancelUniqueWork(
                        uniqueWorkName = BudgetWorkIdentity.uniqueName(identity)
                    )
            }
        } onClose { it?.close() }
        single<OperationSyncScheduler> {
            OperationSyncScheduler {
                val manager = get<ActiveBudgetManager>()
                manager.active.value?.profile?.syncIdentity?.let {
                    WorkManagerOperationSyncScheduler(
                        context = androidContext(),
                        identity = it,
                        budgets = manager,
                        access = get()
                    ).schedule()
                }
            }
        }
        single {
            AndroidOperationConnectivity(
                context = androidContext(),
                scheduler = get()
            )
        }
        single<OperationConnectivity> {
            get<AndroidOperationConnectivity>()
        }
        single {
            OperationWorkerFactory(get())
        }
    }

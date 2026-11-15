package com.firkat.fillin

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.firkat.fillin.core.di.appModule
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

open class FillinApplication :
    Application(),
    Configuration.Provider {
    protected open val automaticSyncEnabled: Boolean = true

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(getKoin().get<OperationWorkerFactory>()).build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FillinApplication)
            modules(appModule)
        }
        val budgets = getKoin().get<ActiveBudgetManager>()
        budgets.start()
        if (automaticSyncEnabled) {
            val access = getKoin().get<AppAccess>()
            val connectivity = getKoin().get<AndroidOperationConnectivity>()
            val scheduler = getKoin().get<OperationSyncScheduler>()
            val management = getKoin().get<BudgetManagement>()
            val work = WorkManager.getInstance(this)

            getKoin().get<com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator>().start()
            management.start()
            work.cancelUniqueWork("operation-history-sync")
            budgets.applicationScope.launch {
                budgets.active.filterNotNull().collect { scheduler.schedule() }
            }
            connectivity.start()
            budgets.applicationScope.launch {
                connectivity.online.filter { it }.collect { access.refresh() }
            }
            budgets.applicationScope.launch {
                access.mode.collect { mode ->
                    if (mode == AppMode.InternalFull) {
                        scheduler.schedule()
                        management.schedule()
                    } else {
                        getKoin().get<okhttp3.OkHttpClient>(
                            org.koin.core.qualifier.named("cloud")
                        ).dispatcher.cancelAll()
                        work.cancelUniqueWork("budget-management")
                        budgets.stopCloudSync()
                    }
                }
            }
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(
                        owner: LifecycleOwner
                    ) {
                        budgets.applicationScope.launch {
                            if (connectivity.online.value) access.refresh()
                        }
                        scheduler.schedule()
                        management.schedule()
                    }
                }
            )
        }
    }
}

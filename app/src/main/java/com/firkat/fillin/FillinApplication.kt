package com.firkat.fillin

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.firkat.fillin.core.di.appModule
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.operationhistory.data.sync.AndroidOperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

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
            getKoin().get<com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator>().start()
            // Old work has no profile/UID. Its durable queue is preserved and rescheduled below.
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("operation-history-sync")
            budgets.applicationScope.launch {
                budgets.active.filterNotNull().collect { getKoin().get<OperationSyncScheduler>().schedule() }
            }
            getKoin().get<AndroidOperationConnectivity>().start()
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        getKoin().get<OperationSyncScheduler>().schedule()
                    }
                },
            )
        }
    }
}

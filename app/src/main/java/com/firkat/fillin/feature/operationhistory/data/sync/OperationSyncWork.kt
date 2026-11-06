package com.firkat.fillin.feature.operationhistory.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetWorkIdentity
import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WorkManagerOperationSyncScheduler(
    private val context: Context,
    private val identity: BudgetSyncIdentity,
    private val budgets: ActiveBudgetManager,
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
) : OperationSyncScheduler {
    override fun schedule() {
        if (access?.cloudAllowed == false) return
        budgets.applicationScope.launch {
            val lease = budgets.acquire(identity) ?: return@launch
            try {
                if (lease.scope.get<OperationDatabase>().operations().state()?.let { it.cloudEnabled && (it.localReady || it.initialSyncComplete) } != true) return@launch
                if (access?.cloudAllowed != false && budgets.isCurrent(lease.runtime.profile)) scheduleEnabled(lease.scope.get())
            } finally { lease.close() }
        }
    }

    private fun scheduleEnabled(requests: OperationSyncRequests) {
        val request =
            OneTimeWorkRequestBuilder<OperationSyncWorker>()
                .setInputData(BudgetWorkIdentity.data(identity))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        // An explicit foreground retry must not wait behind an older worker's exponential backoff.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            // Register before enqueueing so even a worker that starts immediately shares this attempt.
            requests.startForeground(request.id)
        }
        // Appending also preserves a wake-up arriving just as the current worker finishes its last query.
        WorkManager.getInstance(context).enqueueUniqueWork(BudgetWorkIdentity.uniqueName(identity), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class OperationSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val budgets: ActiveBudgetManager,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // Pre-profile jobs have no safe routing information. Their outboxes remain in the imported DB.
        val identity = BudgetWorkIdentity.read(inputData) ?: return Result.success()
        val lease = budgets.acquire(identity) ?: return Result.success()
        return try {
            when (lease.scope.get<OperationSyncRequests>().runWorker(id)) {
                SyncResult.Completed, SyncResult.Blocked -> Result.success()
                SyncResult.Retry -> Result.retry()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Result.retry()
        } finally {
            lease.close()
        }
    }
}

class OperationWorkerFactory(
    private val budgets: ActiveBudgetManager,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == OperationSyncWorker::class.java.name) OperationSyncWorker(appContext, workerParameters, budgets) else null
}

interface OperationConnectivity {
    val online: StateFlow<Boolean>
}

class AndroidOperationConnectivity(
    context: Context,
    private val scheduler: OperationSyncScheduler,
) : OperationConnectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(connected())
    override val online: StateFlow<Boolean> = _online

    fun start() {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = update()

                override fun onLost(network: Network) = update()

                private fun update() {
                    val previous = _online.value
                    _online.value = connected()
                    if (!previous && _online.value) scheduler.schedule()
                }
            },
        )
    }

    private fun connected(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
}

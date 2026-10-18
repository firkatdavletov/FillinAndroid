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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WorkManagerOperationSyncScheduler(
    private val context: Context,
    private val synchronizer: () -> OperationsSynchronizer,
) : OperationSyncScheduler {
    private val foregroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun schedule() {
        val request =
            OneTimeWorkRequestBuilder<OperationSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        // Appending also preserves a wake-up arriving just as the current worker finishes its last query.
        WorkManager.getInstance(context).enqueueUniqueWork("operation-history-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        // An explicit foreground retry must not wait behind an older worker's exponential backoff.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            foregroundScope.launch {
                try {
                    synchronizer().synchronize()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // Persistent work still owns delivery if local storage is temporarily unavailable.
                }
            }
        }
    }
}

class OperationSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val sync: OperationsSynchronizer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        when (sync.synchronize()) {
            SyncResult.Completed, SyncResult.Blocked -> Result.success()
            SyncResult.Retry -> Result.retry()
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        Result.retry()
    }
}

class OperationWorkerFactory(
    private val sync: () -> OperationsSynchronizer,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == OperationSyncWorker::class.java.name) OperationSyncWorker(appContext, workerParameters, sync()) else null
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

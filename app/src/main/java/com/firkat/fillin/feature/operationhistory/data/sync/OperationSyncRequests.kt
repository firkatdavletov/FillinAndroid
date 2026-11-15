package com.firkat.fillin.feature.operationhistory.data.sync

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/** Shares each foreground attempt with its durable worker, including an already finished attempt. */
class OperationSyncRequests(
    private val synchronize: suspend () -> SyncResult,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AutoCloseable {
    private val foregroundAttempts = ConcurrentHashMap<UUID, Deferred<SyncResult>>()
    private val inFlight = ConcurrentHashMap<UUID, Deferred<SyncResult>>()

    fun startForeground(
        requestId: UUID
    ) {
        val attempt = scope.async(start = CoroutineStart.LAZY) { synchronize() }
        foregroundAttempts[requestId] = attempt
        inFlight[requestId] = attempt
        attempt.invokeOnCompletion { inFlight.remove(requestId, attempt) }
        attempt.start()
    }

    suspend fun runWorker(
        requestId: UUID
    ): SyncResult = // Consume once: WorkManager's next backoff attempt must perform a real retry.
        // After process death the map is empty and the persistent worker runs normally.
        foregroundAttempts.remove(requestId)?.await() ?: synchronize()

    fun cancelForeground() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        foregroundAttempts.clear()
    }

    override fun close() {
        cancelForeground()
        scope.cancel()
    }
}

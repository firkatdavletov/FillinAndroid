package com.firkat.fillin.feature.operationhistory.data.sync

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationSyncRequestsTest {
    @Test fun `profile switch cancels foreground attempt even after worker has joined it`() = runTest {
        val result = CompletableDeferred<SyncResult>()
        val requests = OperationSyncRequests({ result.await() }, backgroundScope)
        val id = UUID.randomUUID()
        requests.startForeground(id)
        val worker = async { requests.runWorker(id) }
        runCurrent()
        requests.cancelForeground()
        runCurrent()
        assertTrue(worker.isCancelled)
    }

    @Test
    fun `worker arriving after foreground completion reuses its result without another sync`() = runTest {
        var calls = 0
        val requests = OperationSyncRequests({
            calls++
            SyncResult.Completed
        }, backgroundScope)
        val id = UUID.randomUUID()
        requests.startForeground(id)
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(300)
        assertEquals(SyncResult.Completed, requests.runWorker(id))
        assertEquals(1, calls)
    }

    @Test
    fun `worker joins foreground attempt still in progress`() = runTest {
        var calls = 0
        val result = CompletableDeferred<SyncResult>()
        val requests = OperationSyncRequests({
            calls++
            result.await()
        }, backgroundScope)
        val id = UUID.randomUUID()
        requests.startForeground(id)
        val worker = async { requests.runWorker(id) }
        runCurrent()
        assertEquals(1, calls)
        assertFalse(worker.isCompleted)

        result.complete(SyncResult.Completed)
        assertEquals(SyncResult.Completed, worker.await())
        assertEquals(1, calls)
    }

    @Test
    fun `failed foreground result requests backoff and next worker attempt really retries`() = runTest {
        var calls = 0
        val requests = OperationSyncRequests({
            if (++calls ==
                1
            ) {
                SyncResult.Retry
            } else {
                SyncResult.Completed
            }
        }, backgroundScope)
        val id = UUID.randomUUID()
        requests.startForeground(id)
        runCurrent()
        assertEquals(SyncResult.Retry, requests.runWorker(id))
        assertEquals(1, calls)
        assertEquals(SyncResult.Completed, requests.runWorker(id))
        assertEquals(2, calls)
    }

    @Test
    fun `persistent worker runs when there is no foreground attempt in this process`() = runTest {
        var calls = 0
        val requests = OperationSyncRequests({
            calls++
            SyncResult.Completed
        }, backgroundScope)
        assertEquals(SyncResult.Completed, requests.runWorker(UUID.randomUUID()))
        assertEquals(1, calls)
    }

    @Test
    fun `a new request after foreground completion gets its own attempt`() = runTest {
        var calls = 0
        val requests = OperationSyncRequests({
            calls++
            SyncResult.Completed
        }, backgroundScope)
        val first = UUID.randomUUID()
        requests.startForeground(first)
        runCurrent()
        val second = UUID.randomUUID()
        requests.startForeground(second)
        runCurrent()

        assertEquals(SyncResult.Completed, requests.runWorker(first))
        assertEquals(SyncResult.Completed, requests.runWorker(second))
        assertEquals(2, calls)
    }

    @Test
    fun `unexpected foreground failure reaches worker and does not prevent retry`() = runTest {
        supervisorScope {
            var calls = 0
            val requests = OperationSyncRequests({
                if (++calls == 1) throw IOException("Synthetic storage failure")
                SyncResult.Completed
            }, this)
            val id = UUID.randomUUID()
            requests.startForeground(id)
            runCurrent()
            val failure = runCatching { requests.runWorker(id) }.exceptionOrNull()
            assertEquals(IOException::class.java, failure?.javaClass)
            assertEquals(1, calls)
            assertEquals(SyncResult.Completed, requests.runWorker(id))
            assertEquals(2, calls)
        }
    }
}

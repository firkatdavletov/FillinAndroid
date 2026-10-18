package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.operationhistory.data.local.OperationOutbox
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.remote.SyncChangeDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncScopeDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class OperationsSynchronizerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `empty outbox completes after one changes request`() =
        runTest {
            val store = MemoryStorage().apply {
                commands.clear()
                state = state.copy(errorCode = "network")
            }
            val api = FakeApi()
            val sync = OperationsSynchronizer(api, store, "synthetic", json)

            assertEquals(SyncResult.Completed, sync.synchronize())
            assertEquals(listOf("before"), api.changeCursors)
            assertTrue(api.requests.isEmpty())
            assertEquals("after", store.state.cursor)
            assertNotNull(store.state.lastSyncedAt)
            assertNull(store.state.errorCode)
            assertFalse(store.state.isSyncing)
            assertFalse(sync.running.value)
        }

    @Test
    fun `queued operations are followed by a second pull even after outbox is acknowledged`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi()

            assertEquals(SyncResult.Completed, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
            assertEquals(listOf("changes", "create", "changes"), api.events)
            assertEquals(listOf("before", "after"), api.changeCursors)
            assertEquals(listOf(REQUEST), api.requests)
            assertTrue(store.pending().isEmpty())
        }

    @Test
    fun `first pull reconciling a lost response skips upload and second pull`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi().apply {
                page = PAGE.copy(changes = listOf(SyncChangeDto(1, "LEDGER", false, operation = operation(REQUEST))))
            }

            assertEquals(SyncResult.Completed, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
            assertEquals(listOf("before"), api.changeCursors)
            assertTrue(api.requests.isEmpty())
            assertTrue(store.pending().isEmpty())
        }

    @Test
    fun `empty outbox still downloads all pages of the initial pull`() =
        runTest {
            val store = MemoryStorage().apply { commands.clear() }
            val api = FakeApi().apply {
                get = { cursor ->
                    if (cursor == "before") PAGE.copy(nextCursor = "middle", hasMore = true) else PAGE
                }
            }

            assertEquals(SyncResult.Completed, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
            assertEquals(listOf("before", "middle"), api.changeCursors)
            assertTrue(api.requests.isEmpty())
            assertEquals("after", store.state.cursor)
        }

    @Test
    fun `lost creation response preserves immutable request and retry uses same UUID`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi()
            var posted = false
            api.post = { request ->
                if (!posted) {
                    posted = true
                    throw IOException("Synthetic response loss")
                }
                Response.success(CreatedOperationDto(request.requestId, operation(request)))
            }
            val sync = OperationsSynchronizer(api, store, "synthetic-test-token", json)
            val original = store.commands.single().payload
            assertEquals(SyncResult.Retry, sync.synchronize())
            assertEquals(original, store.commands.single().payload)
            assertEquals("network", store.state.errorCode)
            assertFalse(store.state.isSyncing)
            assertEquals(SyncResult.Completed, sync.synchronize())
            assertEquals(listOf(REQUEST, REQUEST), api.requests)
            assertTrue(store.commands.isEmpty())
            assertNotNull(store.state.lastSyncedAt)
        }

    @Test
    fun `validation rejection remains visible while later queued entries can be sent`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi()
            val second = REQUEST.copy(requestId = "another-stable-uuid")
            store.commands += OperationOutbox(second.requestId, SCOPE.key, second.requestId, json.encodeToString(second))
            api.post = { request ->
                if (request.requestId == second.requestId) Response.success(CreatedOperationDto(request.requestId, operation(request)))
                else Response.error(
                    400,
                    """{"code":"invalid_operation","reason":"category_unavailable"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
            }
            assertEquals(SyncResult.Completed, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
            assertEquals("category_unavailable", store.rejected)
            assertEquals(1, store.commands.size)
            assertTrue(store.pending().isEmpty())
            assertEquals(2, api.requests.size)
        }

    @Test
    fun `authentication and idempotency errors preserve queue and do not allow a new request`() =
        runTest {
            for (status in listOf(401, 403, 409)) {
                val store = MemoryStorage()
                val api = FakeApi()
                api.post = {
                    Response.error(
                        status,
                        """{"code":"idempotency_conflict"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                }
                assertEquals(SyncResult.Blocked, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
                assertEquals(1, store.pending().size)
                assertNull(store.rejected)
                assertFalse(store.state.isSyncing)
            }
        }

    @Test
    fun `changed scope blocks upload before any pending operation is sent`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi().apply { page = PAGE.copy(scope = SCOPE.copy(memberId = "different-member")) }
            assertEquals(SyncResult.Blocked, OperationsSynchronizer(api, store, "synthetic", json).synchronize())
            assertEquals("scope_changed", store.state.errorCode)
            assertTrue(api.requests.isEmpty())
            assertEquals(1, store.commands.size)
        }

    @Test
    fun `concurrent sync triggers serialize delivery and cancellation releases running flag`() =
        runTest {
            val store = MemoryStorage()
            val api = FakeApi()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            api.post = {
                entered.complete(Unit)
                release.await()
                Response.success(CreatedOperationDto(it.requestId, operation(it)))
            }
            val sync = OperationsSynchronizer(api, store, "synthetic", json)
            val first = async { sync.synchronize() }
            entered.await()
            val second = async { sync.synchronize() }
            release.complete(Unit)
            assertEquals(SyncResult.Completed, first.await())
            assertEquals(SyncResult.Completed, second.await())
            assertEquals(1, api.requests.size)

            val nextStore = MemoryStorage()
            val waiting = CompletableDeferred<Unit>()
            val nextApi =
                FakeApi().apply {
                    post = {
                        waiting.complete(Unit)
                        CompletableDeferred<Response<CreatedOperationDto>>().await()
                    }
                }
            val cancelled = async { OperationsSynchronizer(nextApi, nextStore, "synthetic", json).synchronize() }
            waiting.await()
            cancelled.cancel()
            cancelled.join()
            assertFalse(nextStore.state.isSyncing)
            assertEquals(1, nextStore.commands.size)
        }

    private inner class MemoryStorage : OperationSyncStorage {
        var state =
            OperationSyncState(
                scopeKey = SCOPE.key,
                householdId = SCOPE.householdId,
                memberId = SCOPE.memberId,
                initialSyncComplete = true,
                cursor = "before",
            )
        val commands = mutableListOf(OperationOutbox(REQUEST.requestId, SCOPE.key, REQUEST.requestId, json.encodeToString(REQUEST)))
        var rejected: String? = null

        override suspend fun state() = state

        override suspend fun setRunning(running: Boolean) {
            state = state.copy(isSyncing = running)
        }

        override suspend fun apply(page: SyncPageDto) {
            if (page.scope.key != state.scopeKey) throw SyncBlockedException("scope_changed")
            for (change in page.changes) {
                change.operation?.requestId?.let { requestId -> commands.removeAll { it.requestId == requestId } }
            }
            state = state.copy(cursor = page.nextCursor)
        }

        override suspend fun pending() = commands.filter { it.errorCode == null }

        override suspend fun acknowledge(response: CreatedOperationDto) {
            commands.removeAll { it.requestId == response.requestId }
        }

        override suspend fun reject(
            command: OperationOutbox,
            errorCode: String,
        ) {
            rejected = errorCode
            commands[commands.indexOf(command)] = command.copy(errorCode = errorCode)
        }

        override suspend fun finish(
            errorCode: String?,
            syncedAt: String?,
        ) {
            state =
                state.copy(errorCode = errorCode, lastSyncedAt = syncedAt)
        }
    }

    private class FakeApi : OperationSyncApi {
        var page = PAGE
        val requests = mutableListOf<CreateOperationDto>()
        val changeCursors = mutableListOf<String?>()
        val events = mutableListOf<String>()
        var get: suspend (String?) -> SyncPageDto = { page }
        var post: suspend (
            CreateOperationDto,
        ) -> Response<CreatedOperationDto> = { Response.success(CreatedOperationDto(it.requestId, operation(it))) }

        override suspend fun changes(
            authorization: String,
            cursor: String?,
        ): SyncPageDto {
            changeCursors += cursor
            events += "changes"
            return get(cursor)
        }

        override suspend fun create(
            authorization: String,
            request: CreateOperationDto,
        ): Response<CreatedOperationDto> {
            requests += request
            events += "create"
            return post(request)
        }
    }

    companion object {
        private val SCOPE = SyncScopeDto("household", "member", "OWNER", "2026-09-01T00:00:00Z", "Asia/Almaty", "KZT")
        private val PAGE = SyncPageDto(SCOPE, emptyList(), "after", false)
        private val REQUEST = CreateOperationDto("stable-uuid", "household", "member", "EXPENSE", "category", "10.01", "2026-10-18")

        private fun operation(request: CreateOperationDto) =
            SyncOperationDto(
                "server-id",
                "category",
                "EXPENSE",
                "10.01",
                "KZT",
                "10.01",
                "2026-10-18",
                "2026-10-18T00:00:00Z",
                "POSTED",
                0,
                1,
                requestId = request.requestId,
            )
    }
}

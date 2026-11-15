package com.firkat.fillin.feature.operationhistory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncScopeDto
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.sync.data.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class IncomeRuleSyncCapabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, OperationDatabase::class.java).build()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val scope = SyncScopeDto(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "OWNER",
        "2026-10-01T00:00:00Z",
        "Asia/Almaty",
        "KZT"
    )
    private val store = BudgetStore(database, OperationSyncScheduler {}, json)
    private val api = FakeCloudApi()
    private val sync =
        OperationsSynchronizer(
            RetiredOperationSyncApi,
            RoomOperationSyncStorage(database, json),
            "synthetic",
            json,
            budget = store,
            budgetApi = RetiredBudgetSyncApi,
            cloudApi = api
        )

    @After fun close() = database.close()

    @Test fun explicitRetryAfterBackendFixReplaysMonthlyRuleWithoutChangingIdentity() = runBlocking {
        initialize()
        val rule = BudgetSyncCommand(
            "monthly-request", scope.householdId, scope.memberId,
            "CREATE_RULE", "monthly-rule", "2026-11-06", categoryId = "category",
            amount = "319200", frequency = "MONTHLY", scheduleValue = 19,
            startsOn = "2026-11-19", replaceDetails = true
        )
        enqueue(rule)
        api.rejectRule = true
        sync.synchronize()
        val rejected = database.budget().command(rule.requestId)!!
        assertEquals("REJECTED", rejected.status)
        assertNotNull(rejected.wirePayload)

        api.rejectRule = false
        sync.synchronize()
        assertEquals(listOf(rule), api.posts) // Background sync does not retry a rejection.
        store.retryRejectedCommands()
        assertEquals(rejected.copy(status = "PENDING", errorCode = null), store.pending().single())
        assertEquals(SyncResult.Completed, sync.synchronize())
        assertEquals(listOf(rule, rule), api.posts)
        assertEquals("ACKNOWLEDGED", database.budget().command(rule.requestId)!!.status)
        assertFalse(store.hasFailures())
    }

    @Test fun explicitRetryReleasesDependentsButPreservesConflictsAndLegacyRejections() = runBlocking {
        initialize()
        val parent = enqueue(command, frozen = true)
        store.fail(parent, "REJECTED", "invalid_request")
        val child = enqueue(command.copy(requestId = "child"))
        database.budget().update(
            child.copy(predecessor = parent.requestId, status = "BLOCKED", errorCode = "dependency_failed")
        )
        val conflict = enqueue(command.copy(requestId = "conflict"))
        store.fail(conflict, "CONFLICT", "version_conflict")
        val blocked = enqueue(command.copy(requestId = "blocked"))
        database.budget().update(
            blocked.copy(dependency = conflict.requestId, status = "BLOCKED", errorCode = "dependency_failed")
        )
        val legacy = enqueue(command.copy(requestId = "legacy"))
        database.budget().update(legacy.copy(protocolVersion = 3, status = "REJECTED"))

        store.retryRejectedCommands()

        assertEquals(setOf(parent.requestId, child.requestId), store.pending().map { it.requestId }.toSet())
        assertEquals("CONFLICT", database.budget().command(conflict.requestId)!!.status)
        assertEquals("BLOCKED", database.budget().command(blocked.requestId)!!.status)
        assertEquals("REJECTED", database.budget().command(legacy.requestId)!!.status)
    }

    @Test fun narrowCapabilitySendsIncomeRulesAndMetadataWithoutDailyPlanning() = runBlocking {
        initialize()
        val commands = listOf(
            "CREATE_INCOME",
            "UPDATE_INCOME",
            "CREATE_INCOME_RULE",
            "UPDATE_INCOME_RULE",
            "DELETE_INCOME_RULE"
        )
            .mapIndexed { index, type ->
                command.copy(requestId = "request-$index", entityId = "income-$index", type = type)
            }
        commands.forEach { enqueue(it) }
        assertEquals(SyncResult.Completed, sync.synchronize())
        assertEquals(commands, api.posts)
        assertEquals(1, api.budgetReads)
        assertTrue(store.pending().isEmpty())
    }

    @Test fun missingCapabilityPreservesFrozenRequestThenBackendUpgradeAllowsSameRequest() = runBlocking {
        initialize()
        api.capabilities = setOf(BudgetCapabilities.CONFIRMED_PLAN_LINKING)
        val item = enqueue(command, frozen = true)
        assertEquals(SyncResult.Blocked, sync.synchronize())
        assertTrue(api.posts.isEmpty())
        assertEquals("backend_update_required", database.operations().state()!!.errorCode)
        assertEquals(item, store.pending().single())

        api.capabilities = setOf(BudgetCapabilities.INCOME_RULE_EDITING)
        assertEquals(SyncResult.Completed, sync.synchronize())
        assertEquals(listOf(command), api.posts)
        assertEquals(2, api.budgetReads)
        assertTrue(store.pending().isEmpty())
    }

    @Test fun narrowCapabilityDoesNotPrepareOrSendRemainder() = runBlocking {
        initialize()
        val remainder = command.copy(
            type = "CONFIRM_REMAINDER_INCOME",
            remainderAmount = "50.00",
            remainderDueOn = "2026-11-05",
            remainderReceived = "50.00"
        )
        val item = enqueue(remainder)
        assertEquals(SyncResult.Blocked, sync.synchronize())
        assertEquals("backend_update_required", database.operations().state()!!.errorCode)
        assertTrue(api.posts.isEmpty())
        assertEquals(item, store.pending().single())
        assertNull(store.pending().single().wirePayload)
    }

    @Test fun changedScopeCannotAuthorizeIncomeEditing() = runBlocking {
        initialize()
        api.budgetScope = scope.copy(memberId = "00000000-0000-0000-0000-000000000003")
        val item = enqueue(command)
        assertEquals(SyncResult.Blocked, sync.synchronize())
        assertEquals("scope_changed", database.operations().state()!!.errorCode)
        assertTrue(api.posts.isEmpty())
        assertEquals(item, store.pending().single())
    }

    @Test fun lostIncomeReceiptRetriesIdenticalRequestAndRechecksCapabilities() = runBlocking {
        initialize()
        enqueue(command)
        api.loseReceipt = true
        assertEquals(SyncResult.Retry, sync.synchronize())
        val frozen = store.pending().single()
        assertNotNull(frozen.wirePayload)
        assertEquals("network", database.operations().state()!!.errorCode)

        assertEquals(SyncResult.Completed, sync.synchronize())
        assertEquals(listOf(command, command), api.posts)
        assertEquals(2, api.budgetReads)
        assertEquals(frozen.wirePayload, database.budget().command(command.requestId)!!.wirePayload)
        assertTrue(store.pending().isEmpty())
    }

    private suspend fun initialize() {
        database.operations().putState(
            OperationSyncState(
                scopeKey = scope.key,
                householdId = scope.householdId,
                memberId = scope.memberId,
                role = scope.role,
                accountingStartedAt = scope.accountingStartedAt,
                protocolVersion = 4,
                localReady = true,
                initialSyncComplete = true
            )
        )
        database.budget().putRecord(
            LocalSyncRecord(
                scope.key,
                "BUDGET_SETTINGS",
                scope.householdId,
                json.encodeToString(SyncBudgetSettings(scope.householdId, "0.00", "0.00")),
                0
            )
        )
    }

    private suspend fun enqueue(
        value: BudgetSyncCommand,
        frozen: Boolean = false
    ): BudgetOutbox {
        val payload = json.encodeToString(value)
        val order = database.budget().commands(scope.key).size.toLong() + 1
        val item = BudgetOutbox(
            value.requestId,
            scope.key,
            value.entityId,
            value.entityKind(),
            payload,
            order,
            wirePayload = payload.takeIf { frozen },
            protocolVersion = 4
        )
        database.budget().enqueue(item)
        return item
    }

    private val command =
        BudgetSyncCommand(
            "request", scope.householdId, scope.memberId, "UPDATE_INCOME_RULE", "income", "2026-11-04",
            categoryId = "category", amount = "100.00", dueOn = "2026-11-30", incomeName = "Зарплата",
            incomeFrequency = "MONTHLY", incomeActive = true
        )

    private inner class FakeCloudApi : CloudBudgetApi {
        var capabilities = setOf(BudgetCapabilities.INCOME_RULE_EDITING)
        var budgetScope = scope
        var budgetReads = 0
        var loseReceipt = false
        var rejectRule = false
        val posts = mutableListOf<BudgetSyncCommand>()

        override suspend fun budget(
            authorization: String
        ): Response<CloudBudget> {
            budgetReads++
            return Response.success(CloudBudget(budgetScope, capabilities))
        }
        override suspend fun create(
            authorization: String,
            request: CreateCloudBudget
        ): Nothing = error("Already bound")
        override suspend fun changes(
            authorization: String,
            cursor: String?
        ) = SyncPageDto(scope, emptyList(), "cursor", false)
        override suspend fun command(
            authorization: String,
            command: BudgetSyncCommand
        ): Response<BudgetCommandReceipt> {
            posts += command
            if (rejectRule) {
                return Response.error(
                    422,
                    "{\"code\":\"invalid_request\"}".toResponseBody("application/json".toMediaType())
                )
            }
            if (loseReceipt) {
                loseReceipt = false
                throw IOException("Lost receipt after commit")
            }
            return Response.success(BudgetCommandReceipt(command.requestId, 1, 1))
        }
    }
}

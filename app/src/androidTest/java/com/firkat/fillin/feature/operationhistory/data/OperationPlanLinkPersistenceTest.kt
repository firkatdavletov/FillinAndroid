package com.firkat.fillin.feature.operationhistory.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import retrofit2.Response

class OperationPlanLinkPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "plan-links-${UUID.randomUUID()}.db"
    private var database = open()
    private val scheduler = OperationSyncScheduler {}
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-30T06:00:00Z"), ZoneId.of("Asia/Almaty"))
    private val today = LocalDate.parse("2026-10-30")

    @get:Rule val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)
    private fun open() =
        Room.databaseBuilder(context, OperationDatabase::class.java, name).addMigrations(*BUDGET_MIGRATIONS).build()
    private fun store() = BudgetStore(database, scheduler, json, clock)
    private fun history(
        store: BudgetStore
    ) = OfflineOperationHistoryRepository(
        database,
        RetiredOperationHistory,
        OperationsSynchronizer(RetiredOperationSyncApi, RoomOperationSyncStorage(database, json), "", json),
        scheduler,
        object : OperationConnectivity {
            override val online = MutableStateFlow(false)
        },
        json,
        clock,
        store
    )
    private suspend fun initialize() {
        CloudBudgetSession(
            database,
            LocalOnlyCloudApi,
            "",
            json,
            scheduler,
            clock
        ).createLocal(BigDecimal.ZERO, today.minusMonths(1))
    }
    private suspend fun createIncomePlan(
        store: BudgetStore
    ): String {
        val category = requireNotNull(store.historySnapshot()).categories.first { it.kind == "INCOME" }
        return store.enqueue("CREATE_INCOME") {
            it.copy(categoryId = category.id, amount = "100.00", dueOn = today.toString())
        }
    }
    private suspend fun createOperation(
        store: BudgetStore,
        amount: String = "100.00"
    ): CreateOperationResult {
        val category = requireNotNull(store.historySnapshot()).categories.first { it.kind == "INCOME" }
        return history(store).create(NewOperation(OperationType.Income, category.id, BigDecimal(amount), today, null))
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
        context.deleteDatabase("$name-migration")
    }

    @Test fun saveIsImmediatelyDurableAndOnlyConfirmationChangesPlan() = runBlocking {
        initialize()
        var store = store()
        val planId = createIncomePlan(store)
        val result = createOperation(store, "85.00")
        assertNotNull(history(store).observeOperation(result.operationId).first())
        assertNull(store.proposal(result.operationId))
        assertTrue(store.prepareOffer(result.operationId))
        assertEquals("100.00", store.observeLinks(result.operationId).first().candidates.single().remainingAmount)
        database.close()
        database = open()
        store = store()
        assertFalse(store.prepareOffer(result.operationId))
        val proposal = requireNotNull(store.proposal(result.operationId))
        val suggested = (proposal.suggestion as PlanSuggestion.Choice).plans.single()
        assertEquals(planId, suggested.target.planId)
        store.confirmOffer(result.operationId, proposal.operation, suggested)
        val links = store.observeLinks(result.operationId).first()
        assertEquals("15.00", links.current.single().remainingAmount)
        assertNull(store.proposal(result.operationId))
        assertEquals(1, requireNotNull(database.budget().content()).operations.size)
        store.removeLink(
            result.operationId,
            requireNotNull(history(store).observeOperation(result.operationId).first()).version
        )
        assertEquals("100.00", store.observeLinks(result.operationId).first().candidates.single().remainingAmount)
        assertTrue(store.observeLinks(result.operationId).first().current.isEmpty())
    }

    @Test fun dismissedProposalSurvivesRestartAndLateServerReceipt() = runBlocking {
        initialize()
        var store = store()
        createIncomePlan(store)
        val result = createOperation(store)
        val candidate = store.observeLinks(result.operationId).first().candidates.single()
        assertTrue(store.prepareOffer(result.operationId))
        store.dismissProposal(result.operationId)
        database.close()
        database = open()
        store = store()
        assertNull(store.proposal(result.operationId))
        val create = store.pending().first { it.entityId == result.operationId }
        store.acknowledge(create, BudgetCommandReceipt(create.requestId, 10, 1, candidate))
        assertNull(store.proposal(result.operationId))
        assertFalse(store.prepareOffer(result.operationId))
        assertTrue(store.observeLinks(result.operationId).first().current.isEmpty())
    }

    @Test fun noMatchIgnoresLateServerReceiptButStillAllowsManualLink() = runBlocking {
        initialize()
        val store = store()
        createIncomePlan(store)
        val result = createOperation(store, "30.00")
        assertFalse(store.prepareOffer(result.operationId))
        val candidate = store.observeLinks(result.operationId).first().candidates.single()
        val create = store.pending().single { it.entityId == result.operationId }
        store.acknowledge(create, BudgetCommandReceipt(create.requestId, 10, 1, candidate))
        assertNull(store.proposal(result.operationId))
        store.setLink(result.operationId, candidate, 0)
        assertEquals("70.00", store.observeLinks(result.operationId).first().current.single().remainingAmount)
    }

    @Test fun confirmingStaleRemainderDoesNotLinkOperation() = runBlocking {
        initialize()
        val store = store()
        createIncomePlan(store)
        val result = createOperation(store)
        assertTrue(store.prepareOffer(result.operationId))
        val offer = requireNotNull(store.proposal(result.operationId))
        val candidate = (offer.suggestion as PlanSuggestion.Recommended).plan
        val payment = createOperation(store, "15.00")
        store.setLink(payment.operationId, candidate, 0)
        val current = store.observeLinks(result.operationId).first().candidates.single()
        assertEquals(candidate.version, current.version)
        assertEquals("85.00", current.remainingAmount)
        val failure = runCatching {
            store.confirmOffer(result.operationId, offer.operation, candidate)
        }.exceptionOrNull()
        assertEquals("plan_changed", (failure as? BudgetLocalFailure)?.code)
        assertTrue(store.observeLinks(result.operationId).first().current.isEmpty())
        assertNotNull(history(store).observeOperation(result.operationId).first())
    }

    @Test fun linkWaitsForBothCreatesAndFreezesWirePayloadForRetry() = runBlocking {
        initialize()
        val store = store()
        val plan = createIncomePlan(store)
        val result = createOperation(store)
        store.setLink(result.operationId, store.observeLinks(result.operationId).first().candidates.single(), 0)
        val pending = store.pending()
        val link = pending.last()
        assertNull(store.prepare(link))
        pending.filter {
            it.entityId == plan || it.entityId == result.operationId && it.requestId != link.requestId
        }.forEach {
            store.acknowledge(it, BudgetCommandReceipt(it.requestId, 1, 1))
        }
        val prepared = requireNotNull(store.prepare(link))
        assertEquals(1L, prepared.expectedToken)
        assertEquals(1L, prepared.expectedPlanVersion)
        assertEquals(prepared, store.prepare(link))
        assertEquals(
            "CONFIRM_REQUIRED",
            json.decodeFromString<BudgetSyncCommand>(
                pending.first {
                    it.entityId ==
                        result.operationId
                }.intent
            ).planMatchingMode
        )
    }

    @Test fun closedPlanCannotReplaceUserChoice() = runBlocking {
        initialize()
        val store = store()
        val plan = createIncomePlan(store)
        val result = createOperation(store)
        assertTrue(store.prepareOffer(result.operationId))
        val offer = requireNotNull(store.proposal(result.operationId))
        val candidate = (offer.suggestion as PlanSuggestion.Recommended).plan
        store.enqueue("DELETE_INCOME", plan, 0)
        try {
            store.confirmOffer(result.operationId, offer.operation, candidate)
            fail("Expected stale plan")
        } catch (
            expected: BudgetLocalFailure
        ) {
            assertEquals("plan_unavailable", expected.code)
        }
        assertFalse(
            store.pending().any {
                json.decodeFromString<BudgetSyncCommand>(it.intent).type ==
                    "SET_OPERATION_PLAN_LINK"
            }
        )
    }

    @Test fun amountEditRecalculatesLinkAndCategoryChangeRequiresUnlink() = runBlocking {
        initialize()
        val store = store()
        createIncomePlan(store)
        val result = createOperation(store)
        val history = history(store)
        store.setLink(result.operationId, store.observeLinks(result.operationId).first().candidates.single(), 0)
        val old = requireNotNull(history.observeOperation(result.operationId).first())
        history.updateOperation(old.id, old.version, OperationUpdate(BigDecimal("40.00"), old.categoryId, null))
        assertEquals("60.00", store.observeLinks(old.id).first().current.single().remainingAmount)
        val category = store.enqueue("CREATE_CATEGORY") { it.copy(name = "Премия", kind = "INCOME", icon = "INCOME") }
        val current = requireNotNull(history.observeOperation(old.id).first())
        try {
            history.updateOperation(current.id, current.version, OperationUpdate(current.amount, category, null))
            fail("Expected unlink requirement")
        } catch (
            expected: OperationHistoryException
        ) {
            assertEquals(OperationHistoryException.UnlinkBeforeCategoryChange, expected)
        }
        store.removeLink(current.id, current.version)
        val unlinked = requireNotNull(history.observeOperation(old.id).first())
        history.updateOperation(unlinked.id, unlinked.version, OperationUpdate(unlinked.amount, category, null))
        assertEquals(category, requireNotNull(history.observeOperation(old.id).first()).categoryId)
    }

    @Test fun unsupportedBackendKeepsOperationAndNeverReceivesTheNewCommand() = runBlocking {
        initialize()
        val store = store()
        val result = createOperation(store)
        var posts = 0
        val state = requireNotNull(database.operations().state())
        val scope =
            SyncScopeDto(
                state.householdId,
                state.memberId,
                state.role,
                requireNotNull(state.accountingStartedAt),
                "Asia/Almaty",
                "KZT"
            )
        val api = object : CloudBudgetApi {
            override suspend fun budget(
                authorization: String
            ) = Response.success(CloudBudget(scope))
            override suspend fun changes(
                authorization: String,
                cursor: String?
            ) = SyncPageDto(scope, emptyList(), "cursor", false)
            override suspend fun create(
                authorization: String,
                request: CreateCloudBudget
            ): Nothing = error("Already bound")
            override suspend fun command(
                authorization: String,
                command: BudgetSyncCommand
            ): Response<BudgetCommandReceipt> {
                posts++
                error("Unsupported command sent")
            }
        }
        database.operations().putState(state.copy(cloudEnabled = true, cloudBound = true))
        val session = CloudBudgetSession(database, api, "", json, scheduler, clock, firebase = true)
        val sync = OperationsSynchronizer(
            RetiredOperationSyncApi,
            RoomOperationSyncStorage(database, json),
            "",
            json,
            budget = store,
            budgetApi = RetiredBudgetSyncApi,
            cloud = session,
            cloudApi = api
        )
        assertEquals(SyncResult.Blocked, sync.synchronize())
        assertEquals(0, posts)
        assertEquals("backend_update_required", database.operations().state()!!.errorCode)
        assertEquals(1, store.pending().size)
        assertNotNull(history(store).observeOperation(result.operationId).first())
        assertNull(store.pending().single().wirePayload)
    }

    @Test fun lostLinkReceiptRetriesExactRequestAndImportsCanonicalLinkWithoutDoublePayment() = runBlocking {
        initialize()
        val store = store()
        val planId = createIncomePlan(store)
        val result = createOperation(store, "30.00")
        val initial = requireNotNull(database.operations().state())
        store.pending().forEach { store.acknowledge(it, BudgetCommandReceipt(it.requestId, 1, 1)) }
        database.budget().putRecord(
            LocalSyncRecord(
                initial.scopeKey,
                "EXPECTED_INCOME",
                planId,
                json.encodeToString(
                    SyncPlanRecord(
                        planId,
                        requireNotNull(database.operations().operation(result.operationId)).categoryId,
                        amount = "100.00",
                        dueOn = today.toString(),
                        status = "SCHEDULED",
                        version = 1,
                        createdAt = clock.instant().toString(),
                        modifiedAt = clock.instant().toString()
                    )
                ),
                1
            )
        )
        val local = requireNotNull(
            database.operations().operation(result.operationId)
        ).copy(version = 1, syncStatus = "SYNCED")
        database.operations().putOperation(local)
        database.operations().putState(initial.copy(lastRevision = 1, cloudEnabled = true, cloudBound = true))
        store.setLink(result.operationId, store.observeLinks(result.operationId).first().candidates.single(), 1)
        val wireRequests = mutableListOf<BudgetSyncCommand>()
        var committed = false
        val scope =
            SyncScopeDto(
                initial.householdId,
                initial.memberId,
                initial.role,
                requireNotNull(initial.accountingStartedAt),
                "Asia/Almaty",
                "KZT"
            )
        val api = object : CloudBudgetApi {
            override suspend fun budget(
                authorization: String
            ) = Response.success(CloudBudget(scope, setOf("confirmedPlanLinkingV1")))
            override suspend fun create(
                authorization: String,
                request: CreateCloudBudget
            ): Nothing = error("Already bound")
            override suspend fun changes(
                authorization: String,
                cursor: String?
            ): SyncPageDto {
                val changes = if (committed && cursor != "r3") {
                    listOf(
                        SyncChangeDto(
                            2,
                            "LEDGER",
                            false,
                            operation = SyncOperationDto(local.id, local.categoryId, local.type, local.originalAmount, "KZT", local.amountKzt, local.occurredOn, local.createdAt, "POSTED", 2, 1, requestId = local.requestId)
                        ),
                        SyncChangeDto(3, "INCOME_MATCH", false, record = SyncPlanRecord(planId, ledgerId = local.id))
                    )
                } else {
                    emptyList()
                }
                return SyncPageDto(scope, changes, if (committed) "r3" else "r1", false, if (committed) 3 else 1)
            }
            override suspend fun command(
                authorization: String,
                command: BudgetSyncCommand
            ): Response<BudgetCommandReceipt> {
                wireRequests += command
                if (!committed) {
                    committed = true
                    throw IOException("Lost receipt after commit")
                }
                return Response.success(BudgetCommandReceipt(command.requestId, 3, 2))
            }
        }
        val session = CloudBudgetSession(database, api, "", json, scheduler, clock, firebase = true)
        val sync = OperationsSynchronizer(
            RetiredOperationSyncApi,
            RoomOperationSyncStorage(database, json),
            "",
            json,
            budget = store,
            budgetApi = RetiredBudgetSyncApi,
            cloud = session,
            cloudApi = api
        )
        assertEquals(SyncResult.Retry, sync.synchronize())
        assertEquals("70.00", store.observeLinks(local.id).first().current.single().remainingAmount)
        assertEquals(SyncResult.Completed, sync.synchronize())
        assertEquals(2, wireRequests.size)
        assertEquals(wireRequests[0], wireRequests[1])
        assertEquals("70.00", store.observeLinks(local.id).first().current.single().remainingAmount)
        assertTrue(store.pending().isEmpty())
        assertEquals(1, requireNotNull(database.budget().content()).records.count { it.kind == "INCOME_MATCH" })
    }

    @Test fun migrationPreservesBothQueuesAndAddsEmptyDependencies() {
        val migrationName = "$name-migration"
        migrations.createDatabase(migrationName, 4).apply {
            execSQL(
                "INSERT INTO operation_outbox(requestId,scopeKey,operationId,payload) VALUES('legacy','scope','op','unchanged')"
            )
            execSQL(
                "INSERT INTO budget_outbox(requestId,scopeKey,entityId,kind,intent,createdOrder,wirePayload,status,receiptRevision,receiptToken,protocolVersion) VALUES('command','scope','op','LEDGER','intent',1,'wire','ACKNOWLEDGED',42,3,4)"
            )
            close()
        }
        migrations.runMigrationsAndValidate(migrationName, 5, true, BUDGET_MIGRATION_4_5).use { db ->
            db.query("SELECT payload FROM operation_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals("unchanged", it.getString(0))
            }
            db.query("SELECT wirePayload,receiptRevision,receiptToken,dependencies FROM budget_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals("wire", it.getString(0))
                assertEquals(42, it.getInt(1))
                assertEquals(3, it.getInt(2))
                assertEquals("[]", it.getString(3))
            }
            db.query("SELECT COUNT(*) FROM operation_plan_proposals").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }
}

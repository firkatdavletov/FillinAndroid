package com.firkat.fillin.feature.budgetsetup

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.dashboard.data.repository.OfflineDashboardRepository
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileBudgetLifecycleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "cloud-lifecycle-${UUID.randomUUID()}.db"
    private var database = open()
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    private val api = TestCloudApi()
    private val clock = Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty"))
    private val scheduler = OperationSyncScheduler {}
    private val token = "synthetic-cloud-token"

    private fun open() = Room.databaseBuilder(context, OperationDatabase::class.java, name).addMigrations(BUDGET_MIGRATION_1_2, BUDGET_MIGRATION_2_3, BUDGET_MIGRATION_3_4, BUDGET_MIGRATION_4_5).build()

    private fun session(value: String = token) = CloudBudgetSession(database, api, value, json, scheduler, clock)

    private fun store() = BudgetStore(database, scheduler, json, clock)

    private fun synchronizer(
        session: CloudBudgetSession = session(),
        store: BudgetStore = store(),
    ): OperationsSynchronizer =
        OperationsSynchronizer(
            object : OperationSyncApi {
                override suspend fun changes(
                    authorization: String,
                    cursor: String?,
                ): SyncPageDto = error("Unexpected v3 call")

                override suspend fun create(
                    authorization: String,
                    request: CreateOperationDto,
                ): Response<CreatedOperationDto> = api.legacyCreate(request)
            },
            RoomOperationSyncStorage(database, json),
            token,
            json,
            clock,
            store,
            object : BudgetSyncApi {
                override suspend fun command(
                    authorization: String,
                    command: BudgetSyncCommand,
                ): Response<BudgetCommandReceipt> = error("Unexpected v2 command")
            },
            session,
            api,
        )

    private fun history(store: BudgetStore = store()): OfflineOperationHistoryRepository =
        OfflineOperationHistoryRepository(
            database,
            object : OperationHistoryRepository {
                override suspend fun getOperations(
                    limit: Int,
                    cursor: Long?,
                    filter: OperationHistoryFilter,
                ): OperationPage = error("Remote read")

                override suspend fun updateOperationAmount(
                    id: String,
                    version: Long,
                    amount: BigDecimal,
                ): Operation = error("Online edit")

                override suspend fun refundPurchase(
                    id: String,
                    version: Long,
                ) = error("Online refund")
            },
            synchronizer(store = store),
            scheduler,
            object : OperationConnectivity {
                override val online = MutableStateFlow(false)
            },
            json,
            clock,
            store,
        )

    private val filter get() = OperationHistoryFilter(dateFrom = LocalDate.parse("2026-10-01"), dateTo = LocalDate.parse("2026-10-18"))

    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
        context.deleteDatabase("$name-migration")
    }

    @Test fun freshLocalBudgetAndOfflineEditsSurviveRestartWithoutNetwork() =
        runBlocking {
            val session = session("")
            session.createLocal(BigDecimal("100.00"), LocalDate.parse("2026-10-01"))
            assertTrue(session.ready.first())
            val repository = history()
            val category =
                repository.context
                    .first()
                    .categories
                    .first { it.kind == com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense }
            repository.create(NewOperation(OperationType.Expense, category.id, BigDecimal("10.00"), LocalDate.parse("2026-10-17"), null))
            var operation =
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .items
                    .single()
            repository.updateOperationAmount(operation.id, operation.version, BigDecimal("15.00"))
            operation =
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .items
                    .single()
            assertEquals(BigDecimal("15.00"), operation.amount)
            repository.refundPurchase(operation.id, operation.version)
            assertEquals(
                0,
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .totals.expenses
                    .signum(),
            )
            assertEquals(
                3,
                database
                    .budget()
                    .content()!!
                    .commands.size,
            )
            assertEquals(0, api.calls)
            database.close()
            database = open()
            assertTrue(session().ready.first())
            assertEquals(
                0,
                history()
                    .observeHistory(filter, 100)
                    .first()
                    .totals.expenses
                    .signum(),
            )
            assertEquals(
                BigDecimal("100.00"),
                OfflineDashboardRepository(database, store(), json, clock)
                    .observeDashboard()
                    .first()
                    .availableMoney.amount,
            )
            assertFalse(database.operations().state()!!.cloudEnabled)
            assertEquals(0, api.calls)
        }

    @Test fun incomeEditsSurviveRestartAndLostResponseWithoutChangingOtherFields() = runBlocking {
        session().createLocal(BigDecimal.ZERO, LocalDate.parse("2026-10-01"))
        val repository = history()
        val category = repository.context.first().categories.first { it.kind == com.firkat.fillin.feature.categories.domain.model.CategoryKind.Income }
        repository.create(NewOperation(OperationType.Income, category.id, BigDecimal("100.00"), LocalDate.parse("2026-10-17"), "Аванс"))
        val original = repository.observeHistory(filter, 100).first().items.single()
        repository.updateOperationAmount(original.id, original.version, BigDecimal("125.10"))
        val firstEdit = repository.observeOperation(original.id).first()!!
        repository.updateOperationAmount(original.id, firstEdit.version, BigDecimal("150.25"))
        val edited = repository.observeOperation(original.id).first()!!
        assertEquals(original.copy(amount = BigDecimal("150.25"), originalAmount = BigDecimal("150.25"), version = 2), edited)
        assertEquals(BigDecimal("150.25"), repository.observeHistory(filter, 100).first().totals.incomes)
        assertEquals(BigDecimal("150.25"), OfflineDashboardRepository(database, store(), json, clock).observeDashboard().first().availableMoney.amount)
        try {
            repository.refundPurchase(edited.id, edited.version)
            fail("Income cannot be refunded")
        } catch (failure: OperationHistoryException) {
            assertEquals(OperationHistoryException.InvalidRequest, failure)
        }
        val commands = database.budget().content()!!.commands
        assertEquals(3, commands.size)
        assertEquals(0, api.calls)
        database.close()
        database = open()
        assertEquals(edited, history().observeOperation(original.id).first())
        assertEquals(commands, database.budget().content()!!.commands)
        session().enable()
        api.loseCommand = "UPDATE_OPERATION"
        assertEquals(SyncResult.Retry, synchronizer().synchronize())
        val attempted = database.budget().content()!!.commands.first { it.wirePayload != null && it.status == "PENDING" }
        database.close()
        database = open()
        assertEquals(attempted.wirePayload, database.budget().content()!!.commands.first { it.requestId == attempted.requestId }.wirePayload)
        assertEquals(SyncResult.Completed, synchronizer().synchronize())
        assertEquals(1, api.operations.size)
        val synced = history().observeOperation(original.id).first()!!
        assertEquals(edited.copy(syncStatus = OperationSyncStatus.Synced), synced)
        assertEquals(BigDecimal("150.25"), history().observeHistory(filter, 100).first().totals.incomes)
        assertEquals(BigDecimal("150.25"), OfflineDashboardRepository(database, store(), json, clock).observeDashboard().first().availableMoney.amount)
        assertEquals(0, store().context.first().pending)
    }

    @Test fun expenseDetailsSurviveOfflineRestartAndLostResponse() = runBlocking { detailsSurviveRestart(OperationType.Expense) }

    @Test fun incomeDetailsSurviveOfflineRestartAndLostResponse() = runBlocking { detailsSurviveRestart(OperationType.Income) }

    private suspend fun detailsSurviveRestart(type: OperationType) {
        session().createLocal(BigDecimal.ZERO, LocalDate.parse("2026-10-01"))
        val repository = history()
        val category = repository.context.first().categories.first { it.kind.name == type.name }
        val categoryId = UUID.randomUUID().toString()
        store().enqueue("CREATE_CATEGORY", categoryId) { it.copy(name = "Новая категория", kind = type.apiValue, icon = "HOME") }
        repository.create(NewOperation(type, category.id, BigDecimal("100.00"), LocalDate.parse("2026-10-17"), "старое"))
        val original = repository.observeHistory(filter, 100).first().items.single()
        repository.updateOperation(original.id, original.version, OperationUpdate(original.originalAmount, categoryId, "покупка 2 шт"))
        val first = repository.observeOperation(original.id).first()!!
        assertEquals("покупка 2 шт", first.description)
        assertEquals(categoryId, first.categoryId)
        assertEquals(original.originalAmount, first.originalAmount)
        repository.updateOperation(original.id, first.version, OperationUpdate(BigDecimal("150.25"), categoryId, null))
        val edited = repository.observeOperation(original.id).first()!!
        assertNull(edited.description)
        assertEquals(2L, edited.version)
        assertEquals(original.occurredOn, edited.occurredOn)
        assertEquals(0, api.calls)
        val commands = database.budget().content()!!.commands
        database.close()
        database = open()
        assertEquals(edited, history().observeOperation(original.id).first())
        assertEquals(commands, database.budget().content()!!.commands)
        session().enable()
        api.loseCommand = "UPDATE_OPERATION"
        assertEquals(SyncResult.Retry, synchronizer().synchronize())
        val attempted = database.budget().content()!!.commands.first { it.wirePayload != null && it.status == "PENDING" }
        database.close()
        database = open()
        assertEquals(attempted.wirePayload, database.budget().content()!!.commands.first { it.requestId == attempted.requestId }.wirePayload)
        assertEquals(SyncResult.Completed, synchronizer().synchronize())
        assertEquals(1, api.operations.size)
        val synced = history().observeOperation(original.id).first()!!
        assertEquals(edited.copy(syncStatus = OperationSyncStatus.Synced), synced)
        assertEquals(categoryId, api.operations.getValue(original.id).categoryId)
        assertNull(api.operations.getValue(original.id).description)
        assertEquals(0, store().context.first().pending)
    }

    @Test fun invalidDetailsAndStaleVersionsDoNotChangeTheDurableQueue() = runBlocking {
        session().createLocal(BigDecimal.ZERO, LocalDate.parse("2026-10-01"))
        val repository = history()
        val categories = repository.context.first().categories
        val expense = categories.first { it.kind.name == "Expense" }
        val income = categories.first { it.kind.name == "Income" }
        val archived = UUID.randomUUID().toString()
        store().enqueue("CREATE_CATEGORY", archived) { it.copy(name = "Архивная категория", kind = "EXPENSE", icon = "HOME") }
        store().enqueue("DELETE_CATEGORY", archived)
        repository.create(NewOperation(OperationType.Expense, expense.id, BigDecimal.TEN, LocalDate.parse("2026-10-17"), "старое"))
        val original = repository.observeHistory(filter, 100).first().items.single()
        val count = database.budget().content()!!.commands.size
        val valid = OperationUpdate(BigDecimal("15.00"), expense.id, "новое")
        for (invalid in listOf(valid.copy(categoryId = income.id), valid.copy(categoryId = archived),
            valid.copy(categoryId = UUID.randomUUID().toString()), valid.copy(description = "я".repeat(161)))) {
            try { repository.updateOperation(original.id, original.version, invalid); fail("Expected invalid request") }
            catch (failure: OperationHistoryException) { assertEquals(OperationHistoryException.InvalidRequest, failure) }
            assertEquals(count, database.budget().content()!!.commands.size)
            assertEquals(original, repository.observeOperation(original.id).first())
        }
        repository.updateOperation(original.id, original.version, valid)
        try { repository.updateOperation(original.id, original.version, valid.copy(description = "устаревшее")); fail("Expected conflict") }
        catch (failure: OperationHistoryException) { assertEquals(OperationHistoryException.Conflict, failure) }
        assertEquals(count + 1, database.budget().content()!!.commands.size)
        assertEquals("новое", repository.observeOperation(original.id).first()!!.description)
    }

    @Test fun missingRemoteBudgetAndInterruptedBootstrapNeverInitializeLocalBudget() =
        runBlocking {
            try {
                session().connectExisting()
                fail("Expected not found")
            } catch (e: SyncBlockedException) {
                assertEquals("budget_not_found", e.code)
            }
            assertFalse(session().ready.first())
            api.installRemote()
            api.pageSize = 1
            api.failPage = 2
            try {
                session().connectExisting()
                fail("Expected interrupted transfer")
            } catch (_: IOException) {
            }
            assertFalse(session().ready.first())
            assertTrue(database.budget().staged().isEmpty())
            api.failPage = null
            session().connectExisting()
            val state = database.operations().state()!!
            assertTrue(state.localReady && state.cloudEnabled && state.cloudBound)
            assertNotNull(state.lastSyncedAt)
            assertNull(state.bootstrapPayload)
            assertEquals(
                "250.00",
                json
                    .decodeFromString<SyncBudgetSettings>(
                        database
                            .budget()
                            .content()!!
                            .records
                            .single { it.kind == "BUDGET_SETTINGS" }
                            .payload,
                    ).openingBalance,
            )
            assertEquals(0, api.creates)
        }

    @Test fun occupiedTokenDoesNotAttachTheNewLocalBudget() =
        runBlocking {
            session().createLocal(BigDecimal.ZERO, LocalDate.parse("2026-10-01"))
            val scope = database.operations().state()!!.scopeKey
            api.installRemote()
            session().enable()
            assertEquals(SyncResult.Blocked, synchronizer().synchronize())
            val state = database.operations().state()!!
            assertEquals(scope, state.scopeKey)
            assertFalse(state.cloudEnabled)
            assertFalse(state.cloudBound)
            assertEquals("budget_already_exists", state.errorCode)
            assertTrue(state.localReady)
        }

    @Test fun lostCreationAndCommandResponsesRetrySameIdentitiesWithoutDuplicates() =
        runBlocking {
            session().createLocal(BigDecimal("100.00"), LocalDate.parse("2026-10-01"))
            val repository = history()
            val category =
                repository.context
                    .first()
                    .categories
                    .first { it.kind == com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense }
            repository.create(NewOperation(OperationType.Expense, category.id, BigDecimal("10.00"), LocalDate.parse("2026-10-17"), null))
            var operation =
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .items
                    .single()
            repository.updateOperationAmount(operation.id, operation.version, BigDecimal("15.00"))
            operation =
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .items
                    .single()
            repository.refundPurchase(operation.id, operation.version)
            session().enable()
            api.loseCreate = true
            assertEquals(SyncResult.Retry, synchronizer().synchronize())
            api.loseCommand = "REFUND_OPERATION"
            assertEquals(SyncResult.Retry, synchronizer().synchronize())
            assertEquals(SyncResult.Completed, synchronizer().synchronize())
            assertEquals(1, api.budgets)
            assertEquals(2, api.operations.size)
            assertEquals(
                0,
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .totals.expenses
                    .signum(),
            )
            assertEquals(
                operation.id,
                repository
                    .observeHistory(filter, 100)
                    .first()
                    .items
                    .single()
                    .id,
            )
            val state = database.operations().state()!!
            assertNotNull(state.lastSyncedAt)
            assertTrue(state.cloudBound)
            val calls = api.calls
            session().disable()
            database.close()
            database = open()
            assertEquals(SyncResult.Completed, synchronizer().synchronize())
            assertEquals(calls, api.calls)
            assertEquals(state.lastSyncedAt, database.operations().state()!!.lastSyncedAt)
        }

    @Test fun migratedPendingOperationIsCreatedBeforeItsOfflineEditAndDoesNotDuplicate() =
        runBlocking {
            checkMigratedOperation(false)
        }

    @Test fun migratedLostCreationResponseRebindsOnlyUnattemptedEdits() =
        runBlocking {
            checkMigratedOperation(true)
        }

    private suspend fun checkMigratedOperation(committedBeforeSync: Boolean) {
        session().createLocal(BigDecimal("100.00"), LocalDate.parse("2026-10-01"))
        val state = database.operations().state()!!
        val category =
            history()
                .context
                .first()
                .categories
                .first { it.kind == com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense }
        val id = UUID.randomUUID().toString()
        val request = CreateOperationDto(id, state.householdId, state.memberId, "EXPENSE", category.id, "10.00", "2026-10-17")
        val payload = json.encodeToString(request)
        database.operations().putOperation(LocalOperation(id, state.scopeKey, category.id, "EXPENSE", "10.00", "KZT", "10.00", request.occurredOn, "2026-10-18T05:00:00Z", "POSTED", 0, 0, null, null, id, "PENDING"))
        database.operations().enqueue(OperationOutbox(id, state.scopeKey, id, payload))
        history().updateOperationAmount(id, 0, BigDecimal("15.00"))
        session().enable()
        if (committedBeforeSync) {
            session().ensureBound()
            api.legacyCreate(request)
        }
        assertEquals(payload, database.operations().outboxForOperation(id)!!.payload)
        assertEquals(SyncResult.Completed, synchronizer().synchronize())
        assertEquals(1, api.operations.size)
        assertEquals(
            "15.00",
            api.operations.values
                .single()
                .originalAmount,
        )
        assertEquals(
            BigDecimal("15.00"),
            history()
                .observeHistory(filter, 100)
                .first()
                .totals.expenses,
        )
        assertTrue(database.operations().pending(state.scopeKey).isEmpty())
    }

    @Test fun migrationPreservesPayloadsReceiptsAndLocalReadiness() {
        val migrationName = "$name-migration"
        migration.createDatabase(migrationName, 3).apply {
            execSQL("INSERT INTO operation_sync_state(id,scopeKey,householdId,memberId,accountingStartedAt,lastRevision,initialSyncComplete,isSyncing,role,protocolVersion) VALUES(1,'h:m','h','m','2026-10-01T00:00:00Z',42,1,1,'OWNER',3)")
            execSQL("INSERT INTO operation_outbox(requestId,scopeKey,operationId,payload) VALUES('old-operation','h:m','op','immutable-operation')")
            execSQL("INSERT INTO budget_outbox(requestId,scopeKey,entityId,kind,intent,createdOrder,wirePayload,status,receiptRevision,receiptToken) VALUES('old-command','h:m','category','CATEGORY','immutable-intent',1,'immutable-wire','ACKNOWLEDGED',40,3)")
            close()
        }
        migration.runMigrationsAndValidate(migrationName, 4, true, BUDGET_MIGRATION_3_4).use { db ->
            db.query("SELECT localReady,cloudEnabled,cloudBound,isSyncing,protocolVersion FROM operation_sync_state").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(listOf(1, 1, 1, 0, 4), (0..4).map { c.getInt(it) })
            }
            db.query("SELECT payload FROM operation_outbox").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("immutable-operation", c.getString(0))
            }
            db.query("SELECT wirePayload,receiptRevision,receiptToken,protocolVersion FROM budget_outbox").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("immutable-wire", c.getString(0))
                assertEquals(40L, c.getLong(1))
                assertEquals(3L, c.getLong(2))
                assertEquals(2, c.getInt(3))
            }
        }
    }
}

internal class TestCloudApi : CloudBudgetApi {
    var scope: SyncScopeDto? = null
    val operations = linkedMapOf<String, SyncOperationDto>()
    val log = mutableListOf<SyncChangeDto>()
    private val receipts = linkedMapOf<String, BudgetCommandReceipt>()
    private var creation: CreateCloudBudget? = null
    var calls = 0
    var creates = 0
    var budgets = 0
    var loseCreate = false
    var loseCommand: String? = null
    var pageSize = 200
    var failPage: Int? = null
    private var pageCalls = 0

    fun installRemote() {
        val h = UUID.randomUUID().toString()
        scope = SyncScopeDto(h, UUID.randomUUID().toString(), "OWNER", "2026-09-30T19:00:00Z", "Asia/Almaty", "KZT")
        append("BUDGET_SETTINGS", settings = SyncBudgetSettings(h, "250.00", "0.00"))
        append("CATEGORY", category = SyncCategoryDto(UUID.randomUUID().toString(), "Продукты", "EXPENSE", "FOOD", "🍔", false, "GROCERIES"))
    }

    override suspend fun budget(authorization: String): Response<CloudBudget> {
        calls++
        return scope?.let { Response.success(CloudBudget(it, capabilities = setOf("confirmedPlanLinkingV1"))) } ?: error(404)
    }

    override suspend fun create(
        authorization: String,
        request: CreateCloudBudget,
    ): Response<CloudBudget> {
        calls++
        creates++
        if (scope != null && creation != request) return error(409)
        if (scope == null) {
            creation = request
            budgets++
            scope =
                SyncScopeDto(
                    request.householdId,
                    request.memberId,
                    "OWNER",
                    LocalDate
                        .parse(request.accountingStartedOn)
                        .atStartOfDay(ZoneId.of("Asia/Almaty"))
                        .toInstant()
                        .toString(),
                    "Asia/Almaty",
                    "KZT",
                )
            append("BUDGET_SETTINGS", settings = SyncBudgetSettings(request.householdId, request.openingBalance, "0.00"))
            request.categories.forEach { append("CATEGORY", category = SyncCategoryDto(it.id, it.name, it.kind, it.icon, "📦", false, it.dictionaryKey)) }
        }
        if (loseCreate) {
            loseCreate = false
            throw IOException("Lost creation response")
        }
        return Response.success(CloudBudget(scope!!))
    }

    override suspend fun changes(
        authorization: String,
        cursor: String?,
    ): SyncPageDto {
        calls++
        pageCalls++
        if (failPage == pageCalls) throw IOException("Interrupted bootstrap")
        val parts = cursor?.split(':')
        val after = parts?.get(0)?.toLong() ?: 0
        val upper = parts?.get(1)?.toLong()?.takeIf { it != 0L } ?: log.size.toLong()
        val remaining = log.filter { it.revision > after && it.revision <= upper }
        val rows = remaining.take(pageSize)
        val more = remaining.size > rows.size
        return SyncPageDto(scope!!, rows, "${if (more) rows.last().revision else upper}:${if (more) upper else 0}", more, upper)
    }

    suspend fun legacyCreate(request: CreateOperationDto): Response<CreatedOperationDto> {
        calls++
        val existing = operations.values.firstOrNull { it.requestId == request.requestId }
        if (existing != null) return Response.success(CreatedOperationDto(request.requestId, existing))
        val operation = SyncOperationDto(UUID.randomUUID().toString(), request.categoryId, request.type, request.amount, "KZT", request.amount, request.occurredOn, "2026-10-18T05:00:00Z", "POSTED", 0, operations.size + 1L, request.description, null, request.requestId)
        operations[operation.id] = operation
        append("LEDGER", operation = operation)
        return Response.success(CreatedOperationDto(request.requestId, operation))
    }

    override suspend fun command(
        authorization: String,
        command: BudgetSyncCommand,
    ): Response<BudgetCommandReceipt> {
        calls++
        receipts[command.requestId]?.let { return Response.success(it) }
        when (command.type) {
            "CREATE_CATEGORY" -> {
                append("CATEGORY", category = SyncCategoryDto(command.entityId, command.name!!, command.kind!!, command.icon!!, "📦", false))
            }
            "CREATE_OPERATION" -> {
                val date = command.occurredOn!!
                val operation =
                    SyncOperationDto(
                        command.entityId,
                        command.categoryId!!,
                        command.kind!!,
                        command.amount!!,
                        "KZT",
                        command.amount,
                        date,
                        "2026-10-18T05:00:00Z",
                        "POSTED",
                        0,
                        operations.size + 1L,
                        command.description,
                        null,
                        command.requestId,
                        LocalDate
                            .parse(date)
                            .atStartOfDay(ZoneId.of("Asia/Almaty"))
                            .toInstant()
                            .toString(),
                    )
                operations[operation.id] = operation
                append("LEDGER", operation = operation)
            }

            "UPDATE_OPERATION" -> {
                val old = operations.getValue(command.entityId)
                val op = old.copy(originalAmount = command.amount!!, amountKzt = command.amount, version = old.version + 1,
                    categoryId = if (command.replaceDetails) command.categoryId!! else old.categoryId,
                    description = if (command.replaceDetails) command.description else old.description)
                operations[op.id] = op
                append("LEDGER", operation = op)
            }

            "REFUND_OPERATION" -> {
                val old = operations.getValue(command.entityId)
                val expense = old.copy(version = old.version + 1)
                operations[expense.id] = expense
                append("LEDGER", operation = expense)
                val refund = old.copy(id = command.refundId!!, type = "EXPENSE_REFUND", originalAmount = command.amount!!, amountKzt = command.amount, occurredOn = command.authoredOn, refundOf = old.id, requestId = command.requestId, version = 0, sequence = operations.size + 1L)
                operations[refund.id] = refund
                append("LEDGER", operation = refund)
            }

            else -> {
                error("Unexpected command ${command.type}")
            }
        }
        val receipt = BudgetCommandReceipt(command.requestId, log.size.toLong(), operations[command.entityId]?.version ?: 0)
        receipts[command.requestId] = receipt
        if (loseCommand == command.type) {
            loseCommand = null
            throw IOException("Lost command response")
        }
        return Response.success(receipt)
    }

    private fun append(
        kind: String,
        operation: SyncOperationDto? = null,
        category: SyncCategoryDto? = null,
        settings: SyncBudgetSettings? = null,
    ) {
        log += SyncChangeDto(log.size + 1L, kind, false, operation, category, budgetSettings = settings)
    }

    private fun <T> error(code: Int): Response<T> = Response.error(code, "{}".toResponseBody("application/json".toMediaType()))
}

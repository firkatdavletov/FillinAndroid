package com.firkat.fillin.feature.sync

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.BUDGET_MIGRATION_1_2
import com.firkat.fillin.core.database.BUDGET_MIGRATION_2_3
import com.firkat.fillin.feature.categories.data.repository.OfflineCategoriesRepository
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.data.OfflineTestEnvironment
import com.firkat.fillin.feature.operationhistory.data.SyntheticSyncApi
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.plannedincomes.data.repository.OfflinePlannedIncomesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.sync.data.*
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BudgetOfflinePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "budget-test-${UUID.randomUUID()}.db"
    private var database = open()
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @get:Rule val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)

    private fun open() = Room.databaseBuilder(context, OperationDatabase::class.java, name).addMigrations(BUDGET_MIGRATION_1_2, BUDGET_MIGRATION_2_3).build()

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
        context.deleteDatabase("$name-migration")
    }

    private fun store() = BudgetStore(database, OperationSyncScheduler {}, json, OfflineTestEnvironment.CLOCK)

    private fun sync(
        api: SyntheticSyncApi,
        store: BudgetStore,
        remote: BudgetSyncApi,
    ) = OperationsSynchronizer(
        api,
        RoomOperationSyncStorage(database, json),
        "synthetic-test-token",
        json,
        OfflineTestEnvironment.CLOCK,
        store,
        remote,
    )

    @Test fun migrationPreservesLegacyQueueAndRequiresV2Bootstrap() {
        val migrationName = "$name-migration"
        migrations.createDatabase(migrationName, 1).apply {
            execSQL(
                "INSERT INTO operation_sync_state VALUES (1, 'h:m', 'h', 'm', '2026-09-01T00:00:00Z', 'old-cursor', 10, 1, 0, NULL, NULL)",
            )
            execSQL("INSERT INTO operation_outbox VALUES ('request', 'h:m', 'operation', 'immutable-payload', NULL)")
            execSQL(
                "INSERT INTO local_operations(id,scopeKey,categoryId,type,originalAmount,originalCurrency,amountKzt,occurredOn,createdAt,status,version,sequence,syncStatus) VALUES ('operation','h:m','category','EXPENSE','10.01','KZT','10.01','2026-09-02','2026-09-02T00:00:00Z','POSTED',0,0,'PENDING')",
            )
            close()
        }
        migrations.runMigrationsAndValidate(migrationName, 2, true, BUDGET_MIGRATION_1_2).apply {
            query("SELECT requestId, payload FROM operation_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals("request", it.getString(0))
                assertEquals("immutable-payload", it.getString(1))
            }
            query("SELECT amountKzt FROM local_operations").use {
                assertTrue(it.moveToFirst())
                assertEquals("10.01", it.getString(0))
            }
            query("SELECT cursor, initialSyncComplete, protocolVersion FROM operation_sync_state").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals(0, it.getInt(1))
                assertEquals(2, it.getInt(2))
            }
            close()
        }
    }

    @Test fun onceExpenseSurvivesRestartAndValidatesDateAtCreation() = runBlocking {
        val api = SyntheticSyncApi()
        var store = store()
        assertEquals(SyncResult.Completed, sync(api, store, Mutations(api)).synchronize())
        val category = OfflineCategoriesRepository(store).createCategory("Разовый расход", CategoryKind.Expense, "HOME")
        val expenses = com.firkat.fillin.feature.plannedexpenses.data.repository.OfflinePlannedExpensesRepository(store)
        val day = java.time.LocalDate.now(OfflineTestEnvironment.CLOCK)
        val frequency = com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency.Once
        val rule = expenses.createRule(category.id, "100.00".toBigDecimal(), frequency, null, day)
        val filter = PlannedOperationsFilter(period = PlannedOperationPeriod.Custom, dateFrom = day, dateTo = day.plusYears(2))
        val first = store.observePlans(filter).first().items.filterIsInstance<PlannedOperation.Expense>().single()
        assertEquals("${rule.id}@$day", first.id)
        assertThrows(com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException.InvalidRequest::class.java) {
            runBlocking { expenses.createRule(category.id, "100".toBigDecimal(), frequency, null, day.minusDays(1)) }
        }
        database.close()
        database = open()
        store = BudgetStore(database, OperationSyncScheduler {}, json,
            java.time.Clock.offset(OfflineTestEnvironment.CLOCK, java.time.Duration.ofDays(1)))
        val restored = store.observePlans(filter).first().items.filterIsInstance<PlannedOperation.Expense>().single()
        assertEquals(first.id, restored.id)
        assertTrue(restored.expense.overdue)
    }

    @Test fun categoryIncomeAndEditsSurviveRestartAndLostResponse() =
        runBlocking {
            val api = SyntheticSyncApi()
            val remote = Mutations(api)
            var store = store()
            assertEquals(SyncResult.Completed, sync(api, store, remote).synchronize())
            val categories = OfflineCategoriesRepository(store)
            val category = categories.createCategory("Подработка", CategoryKind.Income, "INCOME")
            val incomes = OfflinePlannedIncomesRepository(store)
            val income =
                incomes.createPlannedIncome(
                    category.id,
                    "99.01".toBigDecimal(),
                    java.time.LocalDate.parse("2026-10-19"),
                )
            incomes.updatePlannedIncome(income.id, 0, null, "100.02".toBigDecimal(), null)
            assertEquals(
                "100.02".toBigDecimal(),
                store
                    .observePlans(PlannedOperationsFilter())
                    .first()
                    .totals.incomes,
            )
            remote.loseNextResponse = true
            assertEquals(SyncResult.Retry, sync(api, store, remote).synchronize())
            val wire =
                database
                    .budget()
                    .commands(api.scope.key)
                    .first()
                    .wirePayload
            database.close()
            database = open()
            store = store()
            assertEquals(
                wire,
                database
                    .budget()
                    .commands(api.scope.key)
                    .first()
                    .wirePayload,
            )
            assertEquals(SyncResult.Completed, sync(api, store, remote).synchronize())
            assertEquals(3, remote.receipts.size)
            assertEquals(0, store.context.first().pending)
            val plans = store.observePlans(PlannedOperationsFilter()).first()
            assertEquals("100.02".toBigDecimal(), plans.totals.incomes)
            assertEquals(1L, (plans.items.single() as PlannedOperation.Income).income.version)
            assertEquals(
                category.id,
                OfflineCategoriesRepository(store)
                    .getCategories()
                    .first {
                        it.name == "Подработка"
                    }.id,
            )
        }

    @Test fun conflictsKeepBothVersionsAndSupportReapplyAndAcceptServer() =
        runBlocking {
            val api = SyntheticSyncApi()
            val remote = Mutations(api)
            val store = store()
            val synchronizer = sync(api, store, remote)
            synchronizer.synchronize()
            val id = api.expenseCategory.id
            val categories = OfflineCategoriesRepository(store)
            categories.updateCategory(id, "Моя правка", null)
            api.log +=
                SyncChangeDto(api.log.size + 1L, "CATEGORY", false, category = api.expenseCategory.copy(name = "Серверная"))
            assertEquals(SyncResult.Completed, synchronizer.synchronize())
            val issue =
                store.context
                    .first()
                    .issues
                    .single()
            assertEquals("CONFLICT", issue.status)
            assertTrue(issue.server.contains("Серверная"))
            assertTrue(issue.local.contains("Моя правка"))
            store.resolve(issue.requestId, true)
            assertEquals(SyncResult.Completed, synchronizer.synchronize())
            assertEquals("Моя правка", categories.getCategories().first { it.id == id }.name)
            categories.updateCategory(id, "Вторая правка", null)
            api.log +=
                SyncChangeDto(
                    api.log.size + 1L,
                    "CATEGORY",
                    false,
                    category = api.expenseCategory.copy(name = "Сохранить серверную"),
                )
            synchronizer.synchronize()
            store.resolve(
                store.context
                    .first()
                    .issues
                    .single()
                    .requestId,
                false,
            )
            assertEquals("Сохранить серверную", categories.getCategories().first { it.id == id }.name)
            assertTrue(
                store.context
                    .first()
                    .issues
                    .isEmpty(),
            )
        }

    @Test fun onceRuleFromSyncAcceptsNullScheduleAndPastDate() = runBlocking {
        val api = SyntheticSyncApi()
        val rule = SyncPlanRecord(UUID.randomUUID().toString(), api.expenseCategory.id,
            amount = "10.01", frequency = "ONCE", startsOn = "2026-10-01", active = true,
            version = 0, createdAt = "2026-10-01T00:00:00Z", modifiedAt = "2026-10-01T00:00:00Z")
        val wire = json.encodeToString(rule)
        assertFalse(wire.contains("scheduleValue"))
        assertEquals(rule, json.decodeFromString<SyncPlanRecord>(wire.dropLast(1) + ",\"scheduleValue\":null}"))
        RoomOperationSyncStorage(database, json).apply(SyncPageDto(api.scope,
            api.log + SyncChangeDto(3, "RECURRING_RULE", false, record = rule), "done", false, 3))
        val filter = PlannedOperationsFilter(period = PlannedOperationPeriod.Custom,
            dateFrom = java.time.LocalDate.parse("2026-10-01"), dateTo = java.time.LocalDate.parse("2028-10-01"))
        val expense = store().observePlans(filter).first().items.filterIsInstance<PlannedOperation.Expense>().single().expense
        assertTrue(expense.overdue)
        assertEquals("${rule.id}@2026-10-01", expense.id)
    }

    @Test fun stagedPagesPublishPlansOnlyAfterWholeSnapshot() =
        runBlocking {
            val api = SyntheticSyncApi()
            val store = store()
            val storage = RoomOperationSyncStorage(database, json)
            val rule =
                SyncPlanRecord(
                    UUID.randomUUID().toString(),
                    api.expenseCategory.id,
                    amount = "10.01",
                    frequency = "MONTHLY",
                    scheduleValue = 20,
                    startsOn = "2026-10-01",
                    active = true,
                    version = 0,
                    createdAt = "2026-10-01T00:00:00Z",
                    modifiedAt = "2026-10-01T00:00:00Z",
                )
            storage.apply(SyncPageDto(api.scope, listOf(api.log.first()), "page-1", true, 3))
            assertFalse(store.context.first().ready)
            assertTrue(
                database
                    .budget()
                    .content()!!
                    .categories
                    .isEmpty(),
            )
            database.close()
            database = open()
            RoomOperationSyncStorage(
                database,
                json,
            ).apply(
                SyncPageDto(
                    api.scope,
                    listOf(api.log.last(), SyncChangeDto(3, "RECURRING_RULE", false, record = rule)),
                    "done",
                    false,
                    3,
                ),
            )
            assertTrue(store().context.first().ready)
            assertEquals(
                "10.01".toBigDecimal(),
                store()
                    .observePlans(PlannedOperationsFilter())
                    .first()
                    .totals.expenses,
            )
        }

    @Test fun dependencyConflictBlocksOnlyItsChildrenAndCanBeReapplied() =
        runBlocking {
            val api = SyntheticSyncApi()
            val remote = Mutations(api)
            val store = store()
            val synchronizer = sync(api, store, remote)
            synchronizer.synchronize()
            OfflineCategoriesRepository(store).updateCategory(api.incomeCategory.id, "Мой доход", null)
            OfflinePlannedIncomesRepository(
                store,
            ).createPlannedIncome(api.incomeCategory.id, "20.00".toBigDecimal(), java.time.LocalDate.parse("2026-10-20"))
            OfflineCategoriesRepository(store).createCategory("Независимая", CategoryKind.Expense, "HOME")
            api.log += SyncChangeDto(api.log.size + 1L, "CATEGORY", false, category = api.incomeCategory.copy(name = "Серверный доход"))
            synchronizer.synchronize()
            assertEquals(
                setOf("CONFLICT", "BLOCKED"),
                store.context
                    .first()
                    .issues
                    .map { it.status }
                    .toSet(),
            )
            assertEquals(1, remote.receipts.size)
            store.resolve(
                store.context
                    .first()
                    .issues
                    .first { it.status == "CONFLICT" }
                    .requestId,
                true,
            )
            synchronizer.synchronize()
            assertTrue(
                store.context
                    .first()
                    .issues
                    .isEmpty(),
            )
            assertEquals(
                "20.00".toBigDecimal(),
                store
                    .observePlans(PlannedOperationsFilter())
                    .first()
                    .totals.incomes,
            )
        }

    @Test fun pendingFactUploadsBeforeCategoryDeletionAndKeepsItsAmount() =
        runBlocking {
            val api = SyntheticSyncApi()
            val fallback = SyncCategoryDto(UUID.randomUUID().toString(), "Другое", "EXPENSE", "OTHER", "📦", false, "OTHER")
            api.log += SyncChangeDto(api.log.size + 1L, "CATEGORY", false, category = fallback)
            val remote = Mutations(api)
            val store = store()
            val synchronizer = sync(api, store, remote)
            synchronizer.synchronize()
            val legacy = OfflineTestEnvironment(database, api)
            legacy.repository.create(
                com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation(
                    com.firkat.fillin.feature.operationhistory.domain.model.OperationType.Expense,
                    api.expenseCategory.id,
                    "12.34".toBigDecimal(),
                    java.time.LocalDate.parse("2026-10-18"),
                    null,
                ),
            )
            OfflineCategoriesRepository(store).deleteCategory(api.expenseCategory.id)
            assertEquals(SyncResult.Completed, synchronizer.synchronize())
            assertEquals(1, api.postCount)
            assertTrue(database.operations().pending(api.scope.key).isEmpty())
            val operation =
                database
                    .budget()
                    .content()!!
                    .operations
                    .single()
            assertEquals(fallback.id, operation.categoryId)
            assertEquals("12.34", operation.amountKzt)
        }

    @Test fun editorRetainsObservedRevisionWhenServerChangesBeforeLocalSave() = runBlocking {
        val api = SyntheticSyncApi()
        val remote = Mutations(api)
        val store = store()
        val synchronizer = sync(api, store, remote)
        synchronizer.synchronize()
        val repository = OfflineCategoriesRepository(store)
        val opened = repository.getCategories().first { it.id == api.expenseCategory.id }
        assertEquals(1L, opened.revision)
        api.log += SyncChangeDto(api.log.size + 1L, "CATEGORY", false, category = api.expenseCategory.copy(name = "Изменено во время ввода"))
        synchronizer.synchronize()
        repository.updateCategory(opened, "Локальный ввод", null)
        synchronizer.synchronize()
        assertEquals("CONFLICT", store.context.first().issues.single().status)
    }

    @Test fun delayedConflictReapplyKeepsOriginalIncomeDate() = runBlocking {
        val api = SyntheticSyncApi()
        val remote = Mutations(api)
        fun at(day: String) = BudgetStore(database, OperationSyncScheduler {}, json,
            java.time.Clock.fixed(java.time.Instant.parse("${day}T05:00:00Z"), java.time.ZoneId.of("Asia/Almaty")))
        val early = at("2026-10-17")
        val synchronizer = sync(api, early, remote)
        synchronizer.synchronize()
        val id = early.enqueue("CREATE_INCOME") {
            it.copy(categoryId = api.incomeCategory.id, amount = "10.00", dueOn = "2026-10-18")
        }
        synchronizer.synchronize()
        early.enqueue("UPDATE_INCOME", id, 0) { it.copy(amount = "20.00", dueOn = "2026-10-18") }
        val original = api.log.last { it.record?.id == id }.record!!
        api.log += SyncChangeDto(api.log.size + 1L, "EXPECTED_INCOME", false,
            record = original.copy(amount = "30.00", version = 1))
        val late = at("2026-10-20")
        val resumed = sync(api, late, remote)
        resumed.synchronize()
        late.resolve(late.context.first().issues.single().requestId, true)
        assertEquals(SyncResult.Completed, resumed.synchronize())
        val overdue = late.observePlans(PlannedOperationsFilter(period = PlannedOperationPeriod.Custom,
            dateFrom = java.time.LocalDate.parse("2026-10-01"),
            dateTo = java.time.LocalDate.parse("2026-10-31"))).first()
        assertEquals("20.00".toBigDecimal(), overdue.totals.incomes)
        assertEquals(java.time.LocalDate.parse("2026-10-18"), overdue.items.single().dueOn)
    }

    @Test fun opaqueIconSurvivesLocalSaveRestartRenameAndSync() = runBlocking {
        val api = SyntheticSyncApi()
        val remote = Mutations(api)
        var store = store()
        assertEquals(SyncResult.Completed, sync(api, store, remote).synchronize())
        val code = " custom.icon-v2 "
        val created = OfflineCategoriesRepository(store).createCategory("Техника", CategoryKind.Expense, code)
        assertEquals(code, created.icon.code)
        database.close()
        database = open()
        store = store()
        var repository = OfflineCategoriesRepository(store)
        val restored = repository.getCategories().single { it.id == created.id }
        assertEquals(code, restored.icon.code)
        repository.updateCategory(restored, "Электроника", null)
        assertEquals(SyncResult.Completed, sync(api, store, remote).synchronize())
        assertEquals(code, api.log.last { it.category?.id == created.id }.category!!.iconCode)
        assertEquals(code, repository.getCategories().single { it.id == created.id }.icon.code)
        val current = repository.getCategories().single { it.id == created.id }
        repository.updateCategory(current, null, "next.icon-V3")
        assertEquals(SyncResult.Completed, sync(api, store, remote).synchronize())
        database.close()
        database = open()
        repository = OfflineCategoriesRepository(store())
        assertEquals("next.icon-V3", repository.getCategories().single { it.id == created.id }.icon.code)
    }

    private class Mutations(
        private val api: SyntheticSyncApi,
    ) : BudgetSyncApi {
        val receipts = mutableMapOf<String, BudgetCommandReceipt>()
        var loseNextResponse = false

        override suspend fun command(
            authorization: String,
            command: BudgetSyncCommand,
        ): Response<BudgetCommandReceipt> {
            receipts[command.requestId]?.let { return Response.success(it) }
            val revision = api.log.size + 1L
            val token: Long
            when (command.type) {
                "CREATE_CATEGORY" -> {
                    api.log +=
                        SyncChangeDto(
                            revision,
                            "CATEGORY",
                            false,
                            category =
                                SyncCategoryDto(
                                    command.entityId,
                                    requireNotNull(command.name),
                                    requireNotNull(command.kind),
                                    requireNotNull(command.icon),
                                    "📦",
                                    false,
                                ),
                        )
                    token = revision
                }

                "DELETE_CATEGORY" -> {
                    val old = api.log.last { it.category?.id == command.entityId }
                    val fallback = api.log.last { it.category?.dictionaryKey == "OTHER" }.category!!
                    api.log
                        .filter { it.kind == "LEDGER" }
                        .mapNotNull { it.operation }
                        .distinctBy { it.id }
                        .filter { it.categoryId == command.entityId }
                        .forEach { operation ->
                            api.log +=
                                SyncChangeDto(
                                    api.log.size + 1L,
                                    "LEDGER",
                                    false,
                                    operation =
                                        operation.copy(
                                            categoryId = fallback.id,
                                            version =
                                                operation.version + 1,
                                        ),
                                )
                        }
                    val finalRevision = api.log.size + 1L
                    api.log += SyncChangeDto(finalRevision, "CATEGORY", false, category = old.category!!.copy(archived = true))
                    val receipt = BudgetCommandReceipt(command.requestId, finalRevision, finalRevision)
                    receipts[command.requestId] = receipt
                    return Response.success(receipt)
                }

                "UPDATE_CATEGORY" -> {
                    val old = api.log.last { it.category?.id == command.entityId }
                    if (old.revision != command.expectedToken) {
                        return Response.error(
                            409,
                            """{"code":"entity_changed"}""".toResponseBody("application/json".toMediaType()),
                        )
                    }
                    api.log +=
                        SyncChangeDto(
                            revision,
                            "CATEGORY",
                            false,
                            category =
                                old.category!!.copy(
                                    name =
                                        command.name ?: old.category.name,
                                    iconCode = command.icon ?: old.category.iconCode,
                                ),
                        )
                    token = revision
                }

                "CREATE_INCOME" -> {
                    api.log +=
                        SyncChangeDto(
                            revision,
                            "EXPECTED_INCOME",
                            false,
                            record =
                                SyncPlanRecord(
                                    command.entityId,
                                    command.categoryId,
                                    amount = command.amount,
                                    dueOn = command.dueOn,
                                    status = "SCHEDULED",
                                    version = 0,
                                    createdAt = "2026-10-18T05:00:00Z",
                                    modifiedAt = "2026-10-18T05:00:00Z",
                                ),
                        )
                    token = 0
                }

                "UPDATE_INCOME" -> {
                    val old = api.log.last { it.record?.id == command.entityId }.record!!
                    if (old.version != command.expectedToken) return Response.error(409,
                        "{\"code\":\"entity_changed\"}".toResponseBody("application/json".toMediaType()))
                    token = old.version!! + 1
                    api.log +=
                        SyncChangeDto(
                            revision,
                            "EXPECTED_INCOME",
                            false,
                            record =
                                old.copy(
                                    amount =
                                        command.amount ?: old.amount,
                                    version = token,
                                ),
                        )
                }

                else -> {
                    error("Unexpected command")
                }
            }
            val receipt = BudgetCommandReceipt(command.requestId, revision, token)
            receipts[command.requestId] = receipt
            if (loseNextResponse) {
                loseNextResponse = false
                throw IOException("Synthetic lost response")
            }
            return Response.success(receipt)
        }
    }
}

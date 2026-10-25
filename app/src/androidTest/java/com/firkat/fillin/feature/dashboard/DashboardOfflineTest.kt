package com.firkat.fillin.feature.dashboard

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.BUDGET_MIGRATION_2_3
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.feature.dashboard.data.repository.OfflineDashboardRepository
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.operationhistory.data.OfflineTestEnvironment
import com.firkat.fillin.feature.operationhistory.data.SyntheticSyncApi
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.sync.data.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardOfflineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "dashboard-${UUID.randomUUID()}.db"
    private var database = open()
    private val json = Json { ignoreUnknownKeys = true }

    @get:Rule val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)
    private fun open() = Room.databaseBuilder(context, OperationDatabase::class.java, name).build()

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
        context.deleteDatabase("$name-migration")
    }
    private fun repository() = OfflineDashboardRepository(
        database,
        BudgetStore(
            database,
            OperationSyncScheduler {
            },
            json,
            OfflineTestEnvironment.CLOCK
        ),
        json,
        OfflineTestEnvironment.CLOCK
    )
    private suspend fun dashboard() = withTimeout(5000) { repository().observeDashboard().first() }

    @Test fun dailyLimitStaysStableThroughOfflineCreateEditAndRefund() = runBlocking {
        val api = SyntheticSyncApi()
        api.log += SyncChangeDto(
            3, "BUDGET_SETTINGS", false,
            budgetSettings = SyncBudgetSettings(api.scope.householdId, "140000.00", "0.00"),
        )
        val environment = OfflineTestEnvironment(database, api)
        assertEquals(SyncResult.Completed, environment.synchronizer.synchronize())
        environment.connectivity.online.value = false
        val store = BudgetStore(database, OperationSyncScheduler {}, json, OfflineTestEnvironment.CLOCK)
        withTimeout(10000) {
            val changes = Channel<DailyLimitSummary>(Channel.UNLIMITED)
            val observer = launch(Dispatchers.Default) {
                repository().observeDashboard().map(DailyLimitSummary::from).distinctUntilChanged().collect { changes.send(it) }
            }
            suspend fun expect(spent: String, remaining: String, percent: String) {
                val summary = changes.receive()
                assertEquals("10000.00", summary.limit.amount.toPlainString())
                assertEquals(spent, summary.spentToday.amount.toPlainString())
                assertEquals(remaining, summary.remainingToday.amount.toPlainString())
                assertEquals(percent, summary.remainingPercent!!.toPlainString())
                val forecast = dashboard().balanceForecast
                val expectedBalance = "140000.00".toBigDecimal() - spent.toBigDecimal()
                assertEquals(31, forecast.points.size)
                assertTrue(forecast.points.all { it.balance.amount == expectedBalance })
            }
            try {
                expect("0.00", "10000.00", "100.0")
                environment.repository.create(NewOperation(
                    OperationType.Expense, api.expenseCategory.id, "2500.00".toBigDecimal(),
                    LocalDate.parse("2026-10-18"), null,
                ))
                expect("2500.00", "7500.00", "75.0")
                val expense = database.operations().pending(api.scope.key).single().operationId
                store.enqueue("UPDATE_OPERATION", expense, 0) { it.copy(amount = "5000.00") }
                expect("5000.00", "5000.00", "50.0")
                store.enqueue("REFUND_OPERATION", expense, 1) {
                    it.copy(amount = "2000.00", refundId = UUID.randomUUID().toString())
                }
                expect("3000.00", "7000.00", "70.0")
            } finally {
                observer.cancelAndJoin()
                changes.close()
            }
        }
        database.close()
        database = open()
        val restored = DailyLimitSummary.from(dashboard())
        assertEquals("10000.00", restored.limit.amount.toPlainString())
        assertEquals("3000.00", restored.spentToday.amount.toPlainString())
        assertEquals("7000.00", restored.remainingToday.amount.toPlainString())
        assertEquals("137000.00", dashboard().balanceForecast.points.last().balance.amount.toPlainString())
    }

    @Test fun pendingOperationsAndMatchedIncomeRecalculateOfflineAndSurviveRestart() = runBlocking {
        val api = SyntheticSyncApi()
        val expectedId = UUID.randomUUID().toString()
        api.log += SyncChangeDto(
            3,
            "BUDGET_SETTINGS",
            false,
            budgetSettings = SyncBudgetSettings(api.scope.householdId, "100.00", "0.00")
        )
        api.log += SyncChangeDto(
            4,
            "EXPECTED_INCOME",
            false,
            record = SyncPlanRecord(
                expectedId,
                categoryId = api.incomeCategory.id,
                amount = "50.00",
                dueOn = "2026-10-20",
                status = "SCHEDULED",
                version = 0,
                createdAt = AT,
                modifiedAt = AT
            )
        )
        var environment = OfflineTestEnvironment(database, api)
        assertEquals(SyncResult.Completed, environment.synchronizer.synchronize())
        assertEquals("100.00", dashboard().availableMoney.amount.toPlainString())
        assertEquals("50.00", dashboard().forecast.base.expectedIncome.amount.toPlainString())
        environment.connectivity.online.value = false
        withTimeout(5000) {
            val first = CompletableDeferred<Unit>()
            val changes = async(Dispatchers.Default) {
                repository().observeDashboard().onEach { first.complete(Unit) }.take(2).toList()
            }
            first.await()
            environment.repository.create(
                NewOperation(
                    OperationType.Expense,
                    api.expenseCategory.id,
                    "10.00".toBigDecimal(),
                    LocalDate.parse("2026-10-18"),
                    null
                )
            )
            assertEquals("90.00", changes.await().last().availableMoney.amount.toPlainString())
        }
        environment.repository.create(
            NewOperation(
                OperationType.Income,
                api.incomeCategory.id,
                "50.00".toBigDecimal(),
                LocalDate.parse("2026-10-18"),
                null
            )
        )
        assertEquals("140.00", dashboard().availableMoney.amount.toPlainString())
        assertEquals("0.00", dashboard().forecast.base.expectedIncome.amount.toPlainString())
        database.close()
        database = open()
        assertEquals("140.00", dashboard().availableMoney.amount.toPlainString())
        assertEquals(2, database.operations().pending(api.scope.key).size)
        val income = database.operations().pending(api.scope.key).last()
        val localIncome = database.operations().operation(income.operationId)!!
        val canonicalId = UUID.randomUUID().toString()
        val accepted = SyncOperationDto(
            canonicalId, api.incomeCategory.id, "INCOME", "50.00", "KZT", "50.00",
            "2026-10-18", AT, "POSTED", 0, 1, requestId = income.requestId, occurredAt = AT
        )
        val storage = RoomOperationSyncStorage(database, json)
        storage.acknowledge(CreatedOperationDto(income.requestId, accepted))
        assertEquals("140.00", dashboard().availableMoney.amount.toPlainString())
        assertEquals("0.00", dashboard().forecast.base.expectedIncome.amount.toPlainString())
        storage.apply(
            SyncPageDto(
                api.scope,
                listOf(
                    SyncChangeDto(5, "LEDGER", false, operation = accepted),
                    SyncChangeDto(6, "INCOME_MATCH", false, record = SyncPlanRecord(expectedId, ledgerId = canonicalId))
                ),
                "complete",
                false,
                6
            )
        )
        assertNull(database.operations().operation(localIncome.id))
        assertEquals("140.00", dashboard().availableMoney.amount.toPlainString())
        assertEquals("0.00", dashboard().forecast.base.expectedIncome.amount.toPlainString())
        val expense = database.operations().pending(api.scope.key).single()
        storage.reject(expense, "invalid_operation")
        assertEquals("150.00", dashboard().availableMoney.amount.toPlainString())
    }

    @Test fun inputPagesPublishAtomicallyAndIncludeReserveUpdatesAndDeletions() = runBlocking {
        val api = SyntheticSyncApi()
        val storage = RoomOperationSyncStorage(database, json)
        val settings =
            SyncChangeDto(
                1,
                "BUDGET_SETTINGS",
                false,
                budgetSettings = SyncBudgetSettings(api.scope.householdId, "100.00", "10.00")
            )
        storage.apply(SyncPageDto(api.scope, listOf(settings), "partial", true, 3))
        assertFalse(storage.state().initialSyncComplete)
        assertEquals(0, database.budget().content()!!.records.size)
        val goal = SyncSavingsGoal(UUID.randomUUID().toString(), "20.00")
        storage.apply(
            SyncPageDto(
                api.scope,
                listOf(
                    SyncChangeDto(2, "SAVINGS_GOAL", false, savingsGoal = goal),
                    SyncChangeDto(3, "CATEGORY", false, category = api.expenseCategory)
                ),
                "finished",
                false,
                3
            )
        )
        assertEquals("70.00", dashboard().freeMoney.amount.amount.toPlainString())
        storage.apply(
            SyncPageDto(
                api.scope,
                listOf(
                    SyncChangeDto(4, "SAVINGS_GOAL", true, savingsGoal = goal),
                    settings.copy(
                        revision = 5,
                        budgetSettings = settings.budgetSettings!!.copy(protectedReserve = "30.00")
                    )
                ),
                "updated",
                false,
                5
            )
        )
        assertEquals("70.00", dashboard().freeMoney.amount.amount.toPlainString())
        assertEquals("0.00", dashboard().freeMoney.goalReserve.amount.toPlainString())
    }

    @Test fun migrationKeepsBothQueuesAndRequiresFullV3Snapshot() {
        val file = "$name-migration"
        migrations.createDatabase(file, 2).apply {
            execSQL(
                "INSERT INTO operation_sync_state(id,scopeKey,householdId,memberId,cursor,lastRevision,initialSyncComplete,isSyncing,role,protocolVersion) VALUES (1,'h:m','h','m','v2',17,1,1,'OWNER',2)"
            )
            execSQL(
                "INSERT INTO operation_outbox VALUES ('operation-request','h:m','operation','immutable-operation',NULL)"
            )
            execSQL(
                "INSERT INTO budget_outbox(requestId,scopeKey,entityId,kind,intent,createdOrder,status) VALUES ('plan-request','h:m','plan','EXPECTED_INCOME','immutable-plan',1,'PENDING')"
            )
            execSQL("INSERT INTO sync_staging_pages(payload) VALUES ('old-page')")
            execSQL(
                "INSERT INTO local_operations(id,scopeKey,categoryId,type,originalAmount,originalCurrency,amountKzt,occurredOn,createdAt,status,version,sequence,syncStatus) VALUES ('operation','h:m','c','EXPENSE','10.01','KZT','10.01','2026-09-02','2026-09-02T00:00:00Z','POSTED',0,0,'PENDING')"
            )
            close()
        }
        migrations.runMigrationsAndValidate(file, 3, true, BUDGET_MIGRATION_2_3).apply {
            query("SELECT cursor,lastRevision,initialSyncComplete,protocolVersion FROM operation_sync_state").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals(0L, it.getLong(1))
                assertEquals(0, it.getInt(2))
                assertEquals(3, it.getInt(3))
            }
            query("SELECT payload FROM operation_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals("immutable-operation", it.getString(0))
            }
            query("SELECT intent FROM budget_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals("immutable-plan", it.getString(0))
            }
            query("SELECT count(*) FROM sync_staging_pages").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            query("SELECT amountKzt,occurredAt FROM local_operations").use {
                assertTrue(it.moveToFirst())
                assertEquals("10.01", it.getString(0))
                assertTrue(it.isNull(1))
            }
            close()
        }
    }
    companion object {
        private const val AT = "2026-10-18T00:00:00Z"
    }
}

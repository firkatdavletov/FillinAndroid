package com.firkat.fillin.feature.budgettransfer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.BUDGET_MIGRATION_7_8
import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.core.database.OperationPlanProposal
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgettransfer.data.BudgetCsvCodec
import com.firkat.fillin.feature.budgettransfer.data.BudgetDocuments
import com.firkat.fillin.feature.budgettransfer.data.BudgetSnapshotValidator
import com.firkat.fillin.feature.budgettransfer.data.RoomBudgetSnapshot
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import com.firkat.fillin.feature.sync.data.BudgetStore
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetTransferPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true }
    private val scheduler = OperationSyncScheduler {}
    private val snapshots = RoomBudgetSnapshot(json)
    private val documents = BudgetDocuments(context.contentResolver, BudgetCsvCodec(BudgetSnapshotValidator(json)))

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)

    @Test
    fun csvRestoresLocalEditsAndPlanLinksWithoutReplayingAcknowledgedChanges() = runBlocking {
        val source = open()
        val copy = open()
        val file = File.createTempFile("budget-", ".csv", context.cacheDir)
        try {
            val initialDate = LocalDate.of(2025, 1, 1)
            session(source).createLocal("100.00".toBigDecimal(), initialDate, BudgetCurrency.RUB)
            val original = requireNotNull(source.budget().content())
            val state = original.sync
            val category = original.categories.first { it.kind == "EXPENSE" }
            val operationId = UUID.randomUUID().toString()
            source.operations().putOperation(
                LocalOperation(
                    operationId, state.scopeKey, category.id, "EXPENSE", "50.00", "KZT", "50.00",
                    initialDate.toString(), "${initialDate}T10:00:00Z", "POSTED", 0, 1, "Кофе ☕", null, null
                )
            )
            val store = store(source)
            val dueOn = LocalDate.now().plusDays(2).toString()
            val ruleId = store.enqueue("CREATE_RULE") {
                it.copy(categoryId = category.id, startsOn = dueOn, frequency = "ONCE", amount = "50.00")
            }
            val offer = store.observeLinks(operationId)
            val target = offer.first().candidates.single { it.target.ruleId == ruleId }
            store.setLink(operationId, target, 0)
            source.categoryUsage().record(
                CategoryUsageEvent(state.scopeKey, operationId, operationId, category.id, Instant.now().toEpochMilli())
            )
            source.budget().putProposal(OperationPlanProposal(state.scopeKey, operationId, null, "DISMISSED"))
            val alreadyApplied = BudgetSyncCommand(
                "handled",
                state.householdId,
                state.memberId,
                "UPDATE_OPERATION",
                operationId,
                initialDate.toString(),
                amount = "9.00"
            )
            source.budget().enqueue(
                BudgetOutbox(
                    alreadyApplied.requestId,
                    state.scopeKey,
                    operationId,
                    "LEDGER",
                    json.encodeToString(alreadyApplied),
                    0,
                    status = "ACKNOWLEDGED",
                    receiptRevision = 5
                )
            )
            source.operations().putState(state.copy(lastRevision = 5, cursor = "private-cursor"))
            val before = store.snapshot()
            val document = Uri.fromFile(file).toString()

            documents.write(document, snapshots.read(source, "Семейный бюджет"))
            snapshots.restore(copy, documents.read(document))

            val restored = requireNotNull(copy.budget().content())
            assertTrue(restored.sync.importedLocalOnly && restored.sync.localReady)
            assertFalse(restored.sync.cloudBound || restored.sync.cloudEnabled)
            assertNull(restored.sync.cursor)
            assertNull(restored.sync.bootstrapPayload)
            assertNotEquals(state.scopeKey, restored.sync.scopeKey)
            assertEquals("RUB", restored.sync.currency)
            assertEquals("50.00", store(copy).snapshot().operations.getValue(operationId).accountingAmount)
            assertEquals(before.links(operationId), store(copy).snapshot().links(operationId))
            assertEquals("DISMISSED", copy.budget().proposal(restored.sync.scopeKey, operationId)?.decision)
            assertEquals(1, copy.categoryUsage().events(restored.sync.scopeKey).size)
            assertTrue(
                restored.commands.all {
                    json.decodeFromString<BudgetSyncCommand>(it.intent).householdId == restored.sync.householdId
                }
            )
            val error = runCatching { session(copy).enable() }.exceptionOrNull()
            assertTrue(error is SyncBlockedException)
        } finally {
            source.close()
            copy.close()
            file.delete()
        }
    }

    @Test
    fun migrationKeepsCloudStateAndDoesNotMarkExistingBudgetAsImported() {
        val name = "transfer-migration-${UUID.randomUUID()}.db"
        try {
            migrations.createDatabase(name, 7).use {
                it.execSQL(
                    "INSERT INTO operation_sync_state " +
                        "(id, scopeKey, householdId, memberId, lastRevision, " +
                        "initialSyncComplete, isSyncing, cloudEnabled) " +
                        "VALUES (1, 'h:m', 'h', 'm', 7, 1, 0, 1)"
                )
            }
            migrations.runMigrationsAndValidate(name, 8, true, BUDGET_MIGRATION_7_8).use { database ->
                database.query("SELECT cloudEnabled, importedLocalOnly FROM operation_sync_state").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                    assertEquals(0, it.getInt(1))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun open() = Room.inMemoryDatabaseBuilder(context, OperationDatabase::class.java).build()

    private fun store(
        database: OperationDatabase
    ) = BudgetStore(database, scheduler, json)

    private fun session(
        database: OperationDatabase
    ) = CloudBudgetSession(database, LocalOnlyCloudApi, "", json, scheduler)
}

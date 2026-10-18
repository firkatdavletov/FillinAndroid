package com.firkat.fillin.feature.operationhistory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncChangeDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineOperationPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "offline-test-${UUID.randomUUID()}.db"
    private var database = open()

    private fun open() = Room.databaseBuilder(context, OperationDatabase::class.java, name).build()

    private fun reopen() {
        database.close()
        database = open()
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun incomeAndExpenseSurviveReopeningAndEachUploadOnce() =
        runBlocking {
            val api = SyntheticSyncApi()
            var env = OfflineTestEnvironment(database, api)
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            env.connectivity.online.value = false
            env.repository.create(draft(api, OperationType.Expense, "10.01"))
            env.repository.create(draft(api, OperationType.Income, "100.02"))
            var page = env.repository.observeHistory(FILTER, 20).first()
            assertEquals(2, page.items.size)
            assertTrue(page.items.all { it.syncStatus == OperationSyncStatus.Pending })
            assertEquals(BigDecimal("10.01"), page.totals.expenses)
            assertEquals(BigDecimal("100.02"), page.totals.incomes)
            assertEquals(0, api.postCount)
            env.storage.setRunning(true) // Simulate a process dying during a sync attempt.
            reopen()
            env = OfflineTestEnvironment(database, api)
            assertFalse(env.repository.context.first().isSyncing)
            assertEquals(2, env.storage.pending().size)
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            assertEquals(2, api.received.size)
            assertEquals(2, api.postCount)
            assertTrue(env.storage.pending().isEmpty())
            page = env.repository.observeHistory(FILTER, 20).first()
            assertEquals(2, page.items.size)
            assertTrue(page.items.all { it.syncStatus == OperationSyncStatus.Synced })
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            assertEquals(2, api.postCount)
        }

    @Test fun lostResponseIsReconciledByPullAfterRestartWithoutAnotherUpload() =
        runBlocking {
            val api = SyntheticSyncApi()
            var env = OfflineTestEnvironment(database, api)
            env.synchronizer.synchronize()
            env.repository.create(draft(api))
            val pendingId =
                env.storage
                    .pending()
                    .single()
                    .requestId
            api.loseNextResponse = true
            assertEquals(SyncResult.Retry, env.synchronizer.synchronize())
            assertEquals(
                pendingId,
                env.storage
                    .pending()
                    .single()
                    .requestId,
            )
            assertEquals(1, api.received.size)
            reopen()
            env = OfflineTestEnvironment(database, api)
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            assertTrue(env.storage.pending().isEmpty())
            assertEquals(1, api.postCount)
            val rows =
                env.repository
                    .observeHistory(FILTER, 20)
                    .first()
                    .items
            assertEquals(
                listOf(
                    api.received.values
                        .single()
                        .operation.id,
                ),
                rows.map { it.id },
            )
            assertEquals(OperationSyncStatus.Synced, rows.single().syncStatus)
        }

    @Test fun interruptedBootstrapResumesItsCursorAndBlocksOfflineCreationUntilComplete() =
        runBlocking {
            val api =
                SyntheticSyncApi().apply {
                    pageSize = 1
                    failChangeAtCall = 2
                }
            var env = OfflineTestEnvironment(database, api)
            assertEquals(SyncResult.Retry, env.synchronizer.synchronize())
            assertFalse(env.storage.state().initialSyncComplete)
            assertEquals("1:2", env.storage.state().cursor)
            assertThrows(OperationHistoryException.InvalidRequest::class.java) { runBlocking { env.repository.create(draft(api)) } }
            reopen()
            api.failChangeAtCall = null
            env = OfflineTestEnvironment(database, api)
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            assertTrue(env.storage.state().initialSyncComplete)
            assertEquals(
                2,
                env.repository.context
                    .first()
                    .categories.size,
            )
        }

    @Test fun serverScopeChangeNeverUploadsTheOldBudgetsQueue() =
        runBlocking {
            val api = SyntheticSyncApi()
            val env = OfflineTestEnvironment(database, api)
            env.synchronizer.synchronize()
            env.repository.create(draft(api))
            api.scope = api.scope.copy(memberId = UUID.randomUUID().toString())
            assertEquals(SyncResult.Blocked, env.synchronizer.synchronize())
            assertEquals("scope_changed", env.storage.state().errorCode)
            assertEquals(0, api.postCount)
            assertEquals(1, env.storage.pending().size)
        }

    @Test fun rejectedRecordCanBeCorrectedWithANewRequestAndDoesNotCountInTotals() =
        runBlocking {
            val api = SyntheticSyncApi()
            val env = OfflineTestEnvironment(database, api)
            env.synchronizer.synchronize()
            env.repository.create(draft(api))
            val original = env.storage.pending().single()
            api.reject = true
            assertEquals(SyncResult.Completed, env.synchronizer.synchronize())
            val rejected = env.repository.observeHistory(FILTER, 20).first()
            assertEquals(OperationSyncStatus.Rejected, rejected.items.single().syncStatus)
            assertEquals("category_unavailable", rejected.items.single().syncError)
            assertEquals(0, rejected.totals.expenses.signum())
            env.repository.create(draft(api, amount = "11.02"), original.operationId)
            assertNotEquals(
                original.requestId,
                env.storage
                    .pending()
                    .single()
                    .requestId,
            )
            api.reject = false
            env.synchronizer.synchronize()
            assertEquals(1, api.received.size)
            assertEquals(
                BigDecimal("11.02"),
                env.repository
                    .observeHistory(FILTER, 20)
                    .first()
                    .totals.expenses,
            )
        }

    @Test fun staleJournalVersionDoesNotOverwriteTheCanonicalCreationResponseAndBadPagesRollBack() =
        runBlocking {
            val api = SyntheticSyncApi()
            val env = OfflineTestEnvironment(database, api)
            env.synchronizer.synchronize()
            env.repository.create(draft(api))
            env.synchronizer.synchronize()
            val accepted = api.received.values.single()
            env.storage.acknowledge(
                CreatedOperationDto(
                    accepted.requestId,
                    accepted.operation.copy(version = 2, amountKzt = "19.99", originalAmount = "19.99"),
                ),
            )
            val revision = env.storage.state().lastRevision
            env.storage.apply(
                SyncPageDto(
                    api.scope,
                    listOf(SyncChangeDto(revision + 1, "LEDGER", false, operation = accepted.operation)),
                    "stale",
                    false,
                ),
            )
            assertEquals(
                BigDecimal("19.99"),
                env.repository
                    .observeHistory(FILTER, 20)
                    .first()
                    .totals.expenses,
            )
            val before = env.storage.state()
            val badPage =
                SyncPageDto(
                    api.scope,
                    listOf(
                        SyncChangeDto(revision + 2, "CATEGORY", false, category = api.expenseCategory.copy(name = "Нельзя сохранить")),
                        SyncChangeDto(revision + 3, "LEDGER", false, operation = accepted.operation.copy(amountKzt = "invalid")),
                    ),
                    "must-not-advance",
                    false,
                )
            assertThrows(IllegalArgumentException::class.java) { runBlocking { env.storage.apply(badPage) } }
            assertEquals(before.cursor, env.storage.state().cursor)
            assertEquals(
                "Продукты",
                env.repository.context
                    .first()
                    .categories
                    .first { it.id == api.expenseCategory.id }
                    .name,
            )
        }

    companion object {
        val FILTER = OperationHistoryFilter(dateFrom = LocalDate.parse("2026-10-01"), dateTo = LocalDate.parse("2026-10-31"))

        internal fun draft(
            api: SyntheticSyncApi,
            type: OperationType = OperationType.Expense,
            amount: String = "10.01",
        ) = NewOperation(
            type,
            if (type == OperationType.Expense) api.expenseCategory.id else api.incomeCategory.id,
            BigDecimal(amount),
            LocalDate.parse("2026-10-18"),
            null,
        )
    }
}

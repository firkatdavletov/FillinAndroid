package com.firkat.fillin.feature.operationhistory.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.core.database.BUDGET_MIGRATION_6_7
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.categories.data.sync.*
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class CategoryUsagePersistenceTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OperationDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun migrationKeepsExistingScopeWithoutBackfill() {
        val name = "usage-migration-${UUID.randomUUID()}"
        try {
            migration.createDatabase(name, 6).use {
                it.execSQL("INSERT INTO local_categories (scopeKey, id, name, kind, iconCode, iconEmoji, archived, revision) VALUES ('budget:member', 'category', 'Продукты', 'EXPENSE', 'FOOD', '', 0, 1)")
            }
            migration.runMigrationsAndValidate(name, 7, true, BUDGET_MIGRATION_6_7).use {
                it.query("SELECT name FROM local_categories").use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals("Продукты", rows.getString(0))
                }
                it.query("SELECT COUNT(*) FROM category_usage_events").use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(0, rows.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun eventWriteFailureRollsBackOperationAndOutbox() = runBlocking {
        withDatabase { db ->
            val api = SyntheticSyncApi()
            val env = OfflineTestEnvironment(db, api)
            env.synchronizer.synchronize()
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fail_usage BEFORE INSERT ON category_usage_events
                BEGIN SELECT RAISE(ABORT, 'forced usage write failure'); END
            """.trimIndent())
            val draft = NewOperation(OperationType.Expense, api.expenseCategory.id, BigDecimal("12.00"), LocalDate.now(OfflineTestEnvironment.CLOCK), null)
            assertTrue(runCatching { env.repository.create(draft) }.exceptionOrNull() is SQLiteConstraintException)
            assertTrue(env.repository.observeHistory(OperationHistoryFilter.currentMonth(LocalDate.now(OfflineTestEnvironment.CLOCK)), 10).first().items.isEmpty())
            assertTrue(env.storage.pending().isEmpty())
            assertTrue(db.categoryUsage().pending(api.scope.key).isEmpty())
        }
    }

    @Test fun retryAndPaginatedPullMergeDeviceEventsOnlyOnce() = runBlocking {
        withDatabase { db ->
            val scope = SyntheticSyncApi().scope
            val state = com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState(scopeKey = scope.key)
            db.operations().putState(state)
            val category = UUID.randomUUID().toString()
            val localId = UUID.randomUUID().toString()
            val remoteId = UUID.randomUUID().toString()
            val local = CategoryUsageEventDto(localId, localId, category, 1)
            val remote = CategoryUsageEventDto(remoteId, remoteId, category, 2)
            db.categoryUsage().record(CategoryUsageEvent(scope.key, localId, localId, category, 1))
            val stored = linkedMapOf(remoteId to remote)
            var loseReceipt = true
            val cursors = mutableListOf<String?>()
            val api = object : CategoryUsageApi {
                override suspend fun push(authorization: String, budgetId: String, batch: CategoryUsageBatch): Response<CategoryUsageReceipt> {
                    assertEquals("Bearer test-token", authorization)
                    assertEquals(scope.householdId, budgetId)
                    batch.events.forEach { stored.putIfAbsent(it.id, it) }
                    if (loseReceipt) {
                        loseReceipt = false
                        return Response.error(503, "".toResponseBody())
                    }
                    return Response.success(CategoryUsageReceipt(scope.key, batch.events.map { it.id }))
                }

                override suspend fun pull(authorization: String, budgetId: String, cursor: String?): Response<CategoryUsagePage> {
                    cursors += cursor
                    return Response.success(when (cursor) {
                        null -> CategoryUsagePage(scope.key, listOf(stored.getValue(remoteId)), "1", true)
                        "1" -> CategoryUsagePage(scope.key, listOf(stored.getValue(localId)), "2", false)
                        else -> CategoryUsagePage(scope.key, emptyList(), "2", false)
                    })
                }
            }
            val tokens = object : FirebaseIdTokenProvider {
                override fun isCurrentUser(uid: String) = uid == "uid"
                override suspend fun idToken(uid: String, forceRefresh: Boolean) = "test-token"
            }
            val sync = CategoryUsageSync(db, "uid", scope.householdId, CategoryUsageRequests(api, tokens))
            assertTrue(runCatching { sync.exchange(scope.key) {} }.exceptionOrNull() is HttpException)
            assertEquals(localId, db.categoryUsage().pending(scope.key).single().id)
            sync.exchange(scope.key) {}
            sync.exchange(scope.key) {}
            assertEquals(listOf(null, "1", "2"), cursors)
            assertEquals(2, stored.size)
            assertEquals(2L, db.categoryUsage().observeTotals().first().single().uses)
            assertTrue(db.categoryUsage().pending(scope.key).isEmpty())
            db.operations().putState(state.copy(scopeKey = "${scope.householdId}:${UUID.randomUUID()}"))
            assertTrue(db.categoryUsage().observeTotals().first().isEmpty())
        }
    }

    private suspend fun withDatabase(block: suspend (OperationDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, OperationDatabase::class.java).build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }
}

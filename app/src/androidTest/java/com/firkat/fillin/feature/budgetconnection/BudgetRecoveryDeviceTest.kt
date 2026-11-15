package com.firkat.fillin.feature.budgetconnection

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class BudgetRecoveryDeviceTest {
    @Test fun offlineQueueSurvivesLogoutAnotherAccountAndReopeningTheOriginalAccount() = runBlocking {
        val server = MultipleBudgetsDeviceTest.MultiServer()
        val first = BudgetConnectionDeviceTest.Environment(apiOverride = server, deleteOnClose = false)
        val registry = first.registryName
        val backend = first.backend
        val cloud: BudgetProfile
        val queue: List<BudgetOutbox>
        try {
            first.auth.login("owner")
            first.manager.restore()
            first.creation.create("owner", draft("Исходный", "100.00"))
            cloud = first.manager.active.value!!.profile
            server.offline = true
            expense(first, "12.34")
            queue = first.database(cloud) { it.budget().content()!!.commands }
            assertEquals(SyncResult.Retry, sync(first))
            first.auth.signOut()
            first.manager.restore()
            assertFalse(first.manager.isCurrent(cloud))
            assertTrue(first.registry.available(null).none { it.kind == BudgetProfileKind.CLOUD })
            first.auth.login("other")
            first.manager.restore()
            assertTrue(first.registry.available("other").none { it.id == cloud.id })
            assertTrue(first.connection.cachedBudgets("other").isEmpty())
            val calls = server.calls
            val worker = TestListenableWorkerBuilder<OperationSyncWorker>(first.context)
                .setWorkerFactory(
                    OperationWorkerFactory(first.manager)
                ).setInputData(BudgetWorkIdentity.data(cloud.syncIdentity)).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertEquals(calls, server.calls)
            assertEquals(queue, first.database(cloud) { it.budget().content()!!.commands })
        } finally {
            first.close()
        }
        BudgetConnectionDeviceTest.Environment(
            registryName = registry,
            backend = backend,
            apiOverride = server
        ).use { next ->
            next.auth.login("owner")
            next.manager.restore()
            assertEquals(cloud, next.manager.active.value!!.profile)
            assertEquals(queue, next.database(cloud) { it.budget().content()!!.commands })
            server.offline = false
            assertEquals(SyncResult.Completed, sync(next))
            val remote = server.budgets.getValue(cloud.budgetId!!)
            assertEquals("12.34", remote.entries.single().amountKzt)
            assertEquals(queue.single().requestId, remote.receipts.keys.single())
            assertEquals(SyncResult.Completed, sync(next))
            assertEquals(1, remote.entries.size)
        }
    }

    @Test fun accountSwitchAfterServerCommitKeepsTheOriginalQueueUntilItsOwnerReturns() = runBlocking {
        val server = MultipleBudgetsDeviceTest.MultiServer()
        var committed: suspend () -> Unit = {}
        val api = object : FirebaseBudgetApi by server {
            override suspend fun command(
                authorization: String,
                budgetId: String,
                command: BudgetSyncCommand
            ): Response<BudgetCommandReceipt> {
                val result = server.command(authorization, budgetId, command)
                committed()
                return result
            }
        }
        BudgetConnectionDeviceTest.Environment(apiOverride = api).use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.creation.create("owner", draft("Первый", "100.00"))
            val cloud = env.manager.active.value!!.profile
            expense(env, "11.25")
            val original = env.database(cloud) { it.budget().content()!!.commands.single() }
            committed = {
                env.auth.login("other")
                env.manager.restore()
            }
            assertEquals(SyncResult.Blocked, sync(env))
            val pending = env.database(cloud) { it.budget().content()!!.commands.single() }
            assertEquals(original.requestId, pending.requestId)
            assertNotNull(pending.wirePayload)
            assertEquals("PENDING", pending.status)
            assertFalse(env.manager.isCurrent(cloud))
            assertTrue(env.connection.cachedBudgets("other").isEmpty())
            assertEquals(1, server.budgets.getValue(cloud.budgetId!!).entries.size)
            committed = {}
            env.auth.login("owner")
            env.manager.restore()
            assertEquals(cloud, env.manager.active.value!!.profile)
            assertEquals(SyncResult.Completed, sync(env))
            assertEquals(1, server.budgets.getValue(cloud.budgetId!!).receipts.size)
            assertTrue(env.database(cloud) { it.budget().content()!!.commands.isEmpty() })
        }
    }

    @Test fun changingBudgetDuringDeliveryStopsRemainingCommandsAndReturningReusesTheExchange() = runBlocking {
        val server = MultipleBudgetsDeviceTest.MultiServer()
        var committed: suspend () -> Unit = {}
        val api = object : FirebaseBudgetApi by server {
            override suspend fun command(
                authorization: String,
                budgetId: String,
                command: BudgetSyncCommand
            ): Response<BudgetCommandReceipt> {
                val result = server.command(authorization, budgetId, command)
                committed()
                return result
            }
        }
        BudgetConnectionDeviceTest.Environment(apiOverride = api).use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.creation.create("owner", draft("Первый", "100.00"))
            val first = env.manager.active.value!!.profile
            expense(env, "10.00")
            expense(env, "2.34")
            env.creation.create("owner", draft("Второй", "900.00"))
            val second = env.manager.active.value!!.profile
            env.manager.select(first.id)
            env.manager.acquire(first.syncIdentity)!!.use { lease ->
                committed = {
                    env.manager.select(second.id)
                    // While the old exchange owns a lease, returning must not reset its live flag.
                    env.manager.select(first.id)
                    assertSame(lease.runtime, env.manager.active.value)
                    assertTrue(lease.scope.get<OperationDatabase>().operations().state()!!.isSyncing)
                    env.manager.select(second.id)
                }
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
            }
            assertEquals(second, env.manager.active.value!!.profile)
            assertEquals(1, server.budgets.getValue(first.budgetId!!).entries.size)
            assertTrue(server.budgets.getValue(second.budgetId!!).entries.isEmpty())
            assertEquals(
                1,
                env.database(first) {
                    it.budget().content()!!.commands.count { c -> c.status == "PENDING" }
                }
            )
            committed = {}
            env.manager.select(first.id)
            assertEquals(SyncResult.Completed, sync(env))
            assertEquals(2, server.budgets.getValue(first.budgetId!!).entries.size)
            assertEquals(2, server.budgets.getValue(first.budgetId!!).receipts.size)
            assertTrue(server.budgets.getValue(second.budgetId!!).entries.isEmpty())
        }
    }

    @Test fun repeated401AndNetworkFailureKeepTheSamePreparedCommandForRecovery() = runBlocking {
        val server = MultipleBudgetsDeviceTest.MultiServer()
        var denied = true
        var attempts = 0
        val bodies = mutableListOf<BudgetSyncCommand>()
        val api = object : FirebaseBudgetApi by server {
            override suspend fun command(
                authorization: String,
                budgetId: String,
                command: BudgetSyncCommand
            ): Response<BudgetCommandReceipt> {
                attempts++
                bodies += command
                return if (denied) {
                    Response.error(
                        401,
                        "{}".toResponseBody()
                    )
                } else {
                    server.command(authorization, budgetId, command)
                }
            }
        }
        BudgetConnectionDeviceTest.Environment(apiOverride = api).use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.creation.create("owner", draft("Восстановление", "100.00"))
            val cloud = env.manager.active.value!!.profile
            expense(env, "12.34")
            assertEquals(SyncResult.Blocked, sync(env))
            assertEquals(2, attempts)
            val prepared = env.database(cloud) { it.budget().content()!!.commands.single() }
            assertNotNull(prepared.wirePayload)
            assertFalse(env.database(cloud) { it.operations().state()!!.isSyncing })
            server.offline = true
            assertEquals(SyncResult.Retry, sync(env))
            assertEquals(prepared, env.database(cloud) { it.budget().content()!!.commands.single() })
            denied = false
            server.offline = false
            assertEquals(SyncResult.Completed, sync(env))
            assertEquals(3, attempts)
            assertTrue(bodies.all { it == bodies.first() })
            assertEquals(prepared.requestId, server.budgets.getValue(cloud.budgetId!!).receipts.keys.single())
        }
    }

    @Test fun interruptedPagedPullPublishesAtomicallyAfterReopening() = runBlocking {
        val server = BudgetConnectionDeviceTest.Server()
        var interrupted = false
        var failLastPage = true
        val categoryId = UUID.randomUUID().toString()
        val initialRevision = server.categories.size + 1L
        val api = object : FirebaseBudgetApi by server {
            override suspend fun changes(
                authorization: String,
                budgetId: String,
                cursor: String?
            ): Response<SyncPageDto> {
                if (!interrupted) return server.changes(authorization, budgetId, cursor)
                return if (cursor == "t11-page") {
                    if (failLastPage) throw IOException("Synthetic missing last page")
                    Response.success(
                        SyncPageDto(
                            server.scope,
                            listOf(
                                SyncChangeDto(
                                    initialRevision + 2,
                                    "CATEGORY",
                                    false,
                                    category = SyncCategoryDto(categoryId, "Новая", "EXPENSE", "FOOD", "🍔", false)
                                )
                            ),
                            "t11-complete",
                            false,
                            initialRevision + 2
                        )
                    )
                } else {
                    Response.success(
                        SyncPageDto(
                            server.scope,
                            listOf(
                                SyncChangeDto(
                                    initialRevision + 1,
                                    "BUDGET_SETTINGS",
                                    false,
                                    budgetSettings = SyncBudgetSettings(server.scope.householdId, "250.00", "0.00")
                                )
                            ),
                            "t11-page",
                            true,
                            initialRevision + 2
                        )
                    )
                }
            }
        }
        val first = BudgetConnectionDeviceTest.Environment(server = server, apiOverride = api, deleteOnClose = false)
        val registry = first.registryName
        val backend = first.backend
        first.auth.login("owner")
        first.manager.restore()
        first.connection.connect("owner", first.connection.budgets("owner").single())
        val profile = first.manager.active.value!!.profile
        interrupted = true
        assertEquals(SyncResult.Retry, sync(first))
        first.database(profile) { db ->
            assertEquals(1, db.budget().staged().size)
            assertEquals("100.00", opening(db))
            assertFalse(db.budget().content()!!.categories.any { it.id == categoryId })
        }
        first.close()
        BudgetConnectionDeviceTest.Environment(
            server = server,
            registryName = registry,
            backend = backend,
            apiOverride = api
        ).use { next ->
            next.auth.login("owner")
            next.manager.restore()
            failLastPage = false
            assertEquals(SyncResult.Completed, sync(next))
            next.database(profile) { db ->
                assertTrue(db.budget().staged().isEmpty())
                assertEquals("250.00", opening(db))
                assertEquals(1, db.budget().content()!!.categories.count { it.id == categoryId })
                assertEquals("t11-complete", db.operations().state()!!.cursor)
            }
        }
    }

    companion object {
        fun draft(
            name: String,
            balance: String
        ) = BudgetCreationDraft(name, balance, LocalDate.now().minusDays(1).toString())
        internal suspend fun expense(
            env: BudgetConnectionDeviceTest.Environment,
            amount: String
        ) {
            env.manager.acquire(env.manager.active.value!!.profile.syncIdentity)!!.use { lease ->
                val db = lease.scope.get<OperationDatabase>()
                lease.scope.get<OperationHistoryStore>().create(
                    NewOperation(
                        OperationType.Expense,
                        db.budget().content()!!.categories.first { it.kind == "EXPENSE" }.id,
                        amount.toBigDecimal(),
                        lease.scope.get<CloudBudgetSession>().today,
                        null
                    ),
                    null
                )
            }
        }
        internal suspend fun sync(
            env: BudgetConnectionDeviceTest.Environment
        ) = env.manager.acquire(env.manager.active.value!!.profile.syncIdentity)!!.use {
            it.scope.get<OperationsSynchronizer>().synchronize()
        }
        private suspend fun opening(
            db: OperationDatabase
        ) = Json.decodeFromString<SyncBudgetSettings>(
            db.budget().content()!!.records.single {
                it.kind ==
                    "BUDGET_SETTINGS"
            }.payload
        ).openingBalance
    }
}

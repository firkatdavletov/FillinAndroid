package com.firkat.fillin.feature.budgetconnection

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class MultipleBudgetsDeviceTest {
    @Test fun twoCloudBudgetsKeepOfflineQueuesAndOnlyTheSelectedBudgetSynchronizesAfterRestart() = runBlocking {
        val server = MultiServer()
        val first = BudgetConnectionDeviceTest.Environment(apiOverride = server, deleteOnClose = false)
        val registryName = first.registryName
        val backend = first.backend
        first.auth.login("owner")
        first.manager.restore()
        first.creation.create("owner", draft("Личный", "100.00"))
        val personal = first.manager.active.value!!.profile
        addExpense(first, "11.25")
        val originalQueue = first.database(personal) { it.budget().content()!!.commands }
        first.creation.create("owner", draft("Семейный", "900.00"))
        val family = first.manager.active.value!!.profile
        addExpense(first, "22.50")
        assertNotEquals(personal.databaseName, family.databaseName)
        val catalog = first.connection.budgets("owner")
        assertEquals(setOf("Личный", "Семейный"), catalog.map { it.name }.toSet())
        server.offline = true
        val calls = server.calls
        first.connection.connect("owner", catalog.single { it.id == personal.budgetId })
        assertEquals(personal, first.manager.active.value!!.profile)
        first.connection.connect("owner", catalog.single { it.id == family.budgetId })
        assertEquals(calls, server.calls) // Neither switch attempts any network operation.
        assertEquals(originalQueue, first.database(personal) { it.budget().content()!!.commands })
        assertEquals("100.00", opening(first, personal))
        assertEquals("900.00", opening(first, family))
        val staleWorker = TestListenableWorkerBuilder<OperationSyncWorker>(first.context)
            .setWorkerFactory(
                OperationWorkerFactory(first.manager)
            ).setInputData(BudgetWorkIdentity.data(personal.syncIdentity)).build()
        assertEquals(ListenableWorker.Result.success(), staleWorker.doWork())
        assertEquals(calls, server.calls)
        first.close()
        BudgetConnectionDeviceTest.Environment(
            registryName = registryName,
            backend = backend,
            apiOverride = server
        ).use { reopened ->
            reopened.auth.login("owner")
            reopened.manager.restore()
            assertEquals(family, reopened.manager.active.value!!.profile)
            assertEquals(catalog.toSet(), reopened.connection.cachedBudgets("owner").toSet())
            assertEquals(originalQueue, reopened.database(personal) { it.budget().content()!!.commands })
            server.offline = false
            assertEquals(SyncResult.Completed, sync(reopened))
            assertTrue(server.budgets.getValue(personal.budgetId!!).entries.isEmpty())
            assertEquals("22.50", server.budgets.getValue(family.budgetId!!).entries.single().amountKzt)
            reopened.connection.connect("owner", catalog.single { it.id == personal.budgetId })
            server.budgets.getValue(personal.budgetId!!).loseResponse = true
            assertEquals(SyncResult.Retry, sync(reopened))
            assertEquals(SyncResult.Completed, sync(reopened))
            assertEquals("11.25", server.budgets.getValue(personal.budgetId!!).entries.single().amountKzt)
            assertEquals(1, server.budgets.getValue(personal.budgetId!!).receipts.size)
            assertEquals(1, server.budgets.getValue(family.budgetId!!).receipts.size)
            BudgetConnectionDeviceTest.Environment(apiOverride = server).use { otherDevice ->
                otherDevice.auth.login("owner")
                otherDevice.manager.restore()
                for (budget in otherDevice.connection.budgets("owner")) {
                    otherDevice.connection.connect("owner", budget)
                    val selected = otherDevice.manager.active.value!!.profile
                    assertEquals(budget.id, selected.budgetId)
                    val amount = if (budget.id == personal.budgetId) "11.25" else "22.50"
                    assertEquals(
                        amount,
                        otherDevice.database(selected) {
                            it.budget().content()!!.operations.single().amountKzt
                        }
                    )
                }
            }
        }
    }

    @Test fun cachedNamesAndRolesArePrivateAndAnUndownloadedBudgetCannotReplaceTheActiveOneOffline() = runBlocking {
        val server = MultiServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = server).use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.creation.create("owner", draft("Доступный", "100.00"))
            val original = env.manager.active.value!!.profile
            val extra = BudgetConnectionDeviceTest.Server().apply {
                budgetName = "Участник"
                scope =
                    scope.copy(role = "MEMBER")
            }
            server.budgets[extra.scope.householdId] = extra
            val budgets = env.connection.budgets("owner")
            val member = budgets.single { it.id == extra.scope.householdId }
            assertEquals("MEMBER", member.role)
            server.offline = true
            assertTrue(runCatching { env.connection.connect("owner", member) }.isFailure)
            assertEquals(original, env.manager.active.value!!.profile)
            assertEquals(budgets.toSet(), env.connection.cachedBudgets("owner").toSet())
            env.auth.login("other")
            env.manager.restore()
            assertTrue(env.connection.cachedBudgets("other").isEmpty())
            assertEquals(
                "account_changed",
                (
                    runCatching {
                        env.connection.cachedBudgets("owner")
                    }.exceptionOrNull() as BudgetConnectionFailure
                    ).code
            )
            assertTrue(runCatching { env.connection.connect("other", budgets.first()) }.isFailure)
            assertNotEquals(original, env.manager.active.value!!.profile)
            env.auth.login("owner")
            env.manager.restore()
            assertEquals(original, env.manager.active.value!!.profile)
            server.offline = false
            env.connection.connect("owner", member)
            assertEquals("MEMBER", env.manager.active.value!!.profile.role)
            // A newer successful list replaces stale metadata, including an empty list.
            extra.budgetName = "Новое название"
            env.connection.budgets("owner")
            assertEquals("Новое название", env.connection.cachedBudgets("owner").single { it.id == member.id }.name)
            server.budgets.clear()
            assertTrue(env.connection.budgets("owner").isEmpty())
            assertTrue(env.connection.cachedBudgets("owner").isEmpty())
        }
    }

    @Test fun lostAdditionalCreationResponseResumesTheSameBudgetWithoutChangingTheFirstBudget() = runBlocking {
        val server = MultiServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = server).use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.creation.create("owner", draft("Первый", "100.00"))
            val original = env.manager.active.value!!.profile
            addExpense(env, "10.00")
            val before = env.database(original) { it.budget().content()!! }
            server.loseCreationResponse = true
            assertTrue(runCatching { env.creation.create("owner", draft("Второй", "200.00")) }.isFailure)
            val pending = env.creation.pending("owner")!!
            assertEquals(original, env.manager.active.value!!.profile)
            env.creation.resume("owner")
            assertNull(env.creation.pending("owner"))
            assertEquals(2, server.budgets.size)
            assertEquals(2, server.creations.size)
            assertEquals(
                server.creations.getValue(pending.requestId).second.id,
                env.manager.active.value!!.profile.budgetId
            )
            assertEquals(before, env.database(original) { it.budget().content()!! })
        }
    }

    private fun draft(
        name: String,
        balance: String
    ) = BudgetCreationDraft(name, balance, LocalDate.now().minusDays(1).toString())
    private suspend fun opening(
        env: BudgetConnectionDeviceTest.Environment,
        profile: BudgetProfile
    ) = env.database(profile) { db ->
        Json.decodeFromString<SyncBudgetSettings>(
            db.budget().content()!!.records.single {
                it.kind == "BUDGET_SETTINGS"
            }.payload
        ).openingBalance
    }
    private suspend fun addExpense(
        env: BudgetConnectionDeviceTest.Environment,
        amount: String
    ) {
        val profile = env.manager.active.value!!.profile
        env.manager.acquire(profile.syncIdentity)!!.use { lease ->
            val content = lease.scope.get<OperationDatabase>().budget().content()!!
            lease.scope.get<OperationHistoryStore>().create(
                NewOperation(
                    OperationType.Expense,
                    content.categories.first {
                        it.kind == "EXPENSE"
                    }.id,
                    amount.toBigDecimal(),
                    lease.scope.get<CloudBudgetSession>().today,
                    null
                ),
                null
            )
        }
    }
    private suspend fun sync(
        env: BudgetConnectionDeviceTest.Environment
    ) = env.manager.acquire(env.manager.active.value!!.profile.syncIdentity)!!.use {
        it.scope.get<OperationsSynchronizer>().synchronize()
    }

    internal class MultiServer : FirebaseBudgetApi {
        override suspend fun rename(
            authorization: String,
            id: String,
            request: com.firkat.fillin.feature.budgetconnection.data.RenameBudgetDto
        ): retrofit2.Response<com.firkat.fillin.feature.budgetconnection.data.AvailableBudgetDto> =
            error("Not used in this fixture")
        override suspend fun delete(
            authorization: String,
            id: String,
            requestId: String
        ): retrofit2.Response<Unit> = error("Not used in this fixture")
        val budgets = linkedMapOf<String, BudgetConnectionDeviceTest.Server>()
        val creations = linkedMapOf<String, Pair<CreateFirebaseBudgetDto, AvailableBudgetDto>>()
        var offline = false
        var calls = 0
        var loseCreationResponse = false
        private fun checkOnline() {
            if (offline) throw IOException("Synthetic offline")
            calls++
        }
        override suspend fun budgets(
            authorization: String
        ): Response<List<AvailableBudgetDto>> {
            checkOnline()
            return Response.success(
                if (authorization == "Bearer synthetic-owner") {
                    budgets.values.map {
                        AvailableBudgetDto(it.scope.householdId, it.budgetName, it.scope.memberId, it.scope.role)
                    }
                } else {
                    emptyList()
                }
            )
        }
        override suspend fun create(
            authorization: String,
            request: CreateFirebaseBudgetDto
        ): Response<AvailableBudgetDto> {
            checkOnline()
            check(authorization == "Bearer synthetic-owner")
            val previous = creations[request.requestId]
            if (previous != null && previous.first != request) return Response.error(409, "{}".toResponseBody())
            val summary =
                previous?.second ?: BudgetConnectionDeviceTest.Server().apply { budgetExists = false }.let { server ->
                    server.create(authorization, request).body()!!.also {
                        budgets[it.id] = server
                        creations[request.requestId] =
                            request to it
                    }
                }
            if (loseCreationResponse) {
                loseCreationResponse = false
                throw IOException("Synthetic lost additional creation response")
            }
            return Response.success(summary)
        }
        override suspend fun budget(
            authorization: String,
            budgetId: String
        ): Response<CloudBudget> {
            checkOnline()
            return budgets.getValue(budgetId).budget(authorization, budgetId)
        }
        override suspend fun changes(
            authorization: String,
            budgetId: String,
            cursor: String?
        ): Response<SyncPageDto> {
            checkOnline()
            return budgets.getValue(budgetId).changes(authorization, budgetId, cursor)
        }
        override suspend fun command(
            authorization: String,
            budgetId: String,
            command: BudgetSyncCommand
        ): Response<BudgetCommandReceipt> {
            checkOnline()
            return budgets.getValue(budgetId).command(authorization, budgetId, command)
        }
        override suspend fun importLocal(
            authorization: String,
            request: ImportFirebaseBudgetDto
        ): Response<AvailableBudgetDto> = error("Unused in T08")
    }
}

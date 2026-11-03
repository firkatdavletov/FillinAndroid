package com.firkat.fillin.feature.budgetupload

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.AvailableBudget
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.sync.data.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class LocalBudgetUploadDeviceTest {
    @Test fun offlineBudgetBecomesAnotherCloudBudgetInTheSameFileAndRetriesMoneyOnce() = runBlocking {
        val remote = UploadServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = remote).use { env ->
            val local = env.seedLegacy()
            env.manager.acquire(local.syncIdentity)!!.use { lease ->
                val session = lease.scope.get<CloudBudgetSession>()
                val content = lease.scope.get<OperationDatabase>().budget().content()!!
                lease.scope.get<OperationHistoryStore>().create(NewOperation(OperationType.Income,
                    content.categories.first { it.kind == "INCOME" }.id, "7.00".toBigDecimal(), session.today, null), null)
            }
            val before = env.database(local) { it.budget().content()!! }
            val originalCommand = before.commands.first()
            env.connection.connect("owner", remote.other.summary())
            val otherProfile = env.manager.active.value!!.profile
            val otherBefore = env.database(otherProfile) { it.budget().content()!! }
            assertTrue(env.uploadConnection.candidates.first().any { it.profileId == local.id })
            env.uploadConnection.connect("owner", local.id, "С устройства")
            val cloud = env.manager.active.value!!.profile
            assertEquals(local.id, cloud.id)
            assertEquals(local.databaseName, cloud.databaseName)
            assertEquals(BudgetProfileKind.CLOUD, cloud.kind)
            val after = env.database(cloud) { it.budget().content()!! }
            assertEquals(before.commands, after.commands)
            assertEquals(before.operations, after.operations)
            assertEquals(before.records, after.records)
            remote.target.loseResponse = true
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Retry, lease.scope.get<OperationsSynchronizer>().synchronize())
                val pending = lease.scope.get<BudgetStore>().pending().first { it.requestId == originalCommand.requestId }
                val payload = pending.wirePayload
                assertEquals(originalCommand.requestId, pending.requestId)
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
                assertTrue(lease.scope.get<BudgetStore>().pending().isEmpty())
                assertNotNull(payload)
            }
            assertEquals(before.sync.householdId, remote.target.scope.householdId)
            assertEquals(before.sync.memberId, remote.target.scope.memberId)
            assertEquals(before.categories.map { it.id }.toSet(), remote.target.categories.map { it.id }.toSet())
            assertEquals(before.commands.map { it.requestId }.toSet(), remote.target.receipts.keys.toSet())
            assertEquals(2, remote.target.entries.size)
            assertEquals(otherBefore, env.database(otherProfile) { it.budget().content()!! })
            assertEquals(2, env.connection.budgets("owner").size)
            BudgetConnectionDeviceTest.Environment(apiOverride = remote).use { second ->
                second.auth.login("owner"); second.manager.restore()
                second.connection.connect("owner", second.connection.budgets("owner").single { it.id == cloud.budgetId })
                val imported = second.manager.active.value!!.profile
                assertNotEquals(cloud.databaseName, imported.databaseName)
                val operations = second.database(imported) { it.budget().content()!!.operations }
                assertEquals(remote.target.entries.map { it.id }.toSet(), operations.map { it.id }.toSet())
                assertEquals(listOf("12.34", "7.00"), operations.map { it.amountKzt }.sorted())
            }
        }
    }

    @Test fun lostImportResponseSurvivesRestartAndCloudDataStaysPrivateToTheReservedUid() = runBlocking {
        val remote = UploadServer().apply { loseImportResponse = true }
        val first = BudgetConnectionDeviceTest.Environment(deleteOnClose = false, apiOverride = remote)
        val local = first.seedLegacy()
        val before = first.database(local) { it.budget().content()!! }
        first.uploadConnection.connect("owner", local.id, "Из офлайна")
        val cloud = first.manager.active.value!!.profile
        first.manager.acquire(cloud.syncIdentity)!!.use { lease ->
            assertEquals(SyncResult.Retry, lease.scope.get<OperationsSynchronizer>().synchronize())
            assertFalse(lease.scope.get<OperationDatabase>().operations().state()!!.cloudBound)
        }
        val saved = first.uploads.find(local.id)!!
        assertEquals(before.sync.bootstrapPayload, saved.bootstrapPayload)
        val registryName = first.registryName
        val backend = first.backend
        first.close()
        BudgetConnectionDeviceTest.Environment(registryName = registryName, backend = backend, apiOverride = remote).use { reopened ->
            reopened.auth.login("other"); reopened.manager.restore()
            assertFalse(reopened.registry.available("other").any { it.id == local.id })
            assertTrue(reopened.uploadConnection.candidates.first().none { it.profileId == local.id })
            assertEquals("account_changed", (runCatching { reopened.uploadConnection.connect("other", local.id, "Другой") }.exceptionOrNull() as BudgetConnectionFailure).code)
            reopened.auth.login("owner"); reopened.manager.restore()
            assertEquals(cloud, reopened.manager.active.value!!.profile)
            assertEquals(saved, reopened.uploads.find(local.id))
            reopened.uploadConnection.connect("owner", local.id, "Название не заменяет сохранённое")
            reopened.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
            }
            assertEquals(1, remote.imports.size)
            assertEquals(2, remote.calls.size)
            assertEquals(remote.calls[0], remote.calls[1])
            assertEquals(before.commands.single().requestId, remote.target.receipts.keys.single())
        }
    }

    @Test fun failureBetweenRegistryCommitAndSyncFlagCanBeResumedWithoutChangingTheQueue() = runBlocking {
        val remote = UploadServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = remote).use { env ->
            val local = env.seedLegacy()
            val before = env.database(local) { it.budget().content()!! }
            val failure = runCatching { env.manager.attachLocal("owner", local.id) { profile, db ->
                val state = db.operations().state()!!
                env.uploads.bind(profile, "owner", state.householdId, "После остановки", state.bootstrapPayload!!)
                error("Synthetic interruption after registry commit")
            } }.exceptionOrNull()
            assertNotNull(failure)
            val cloud = env.manager.active.value!!.profile
            assertEquals(BudgetProfileKind.CLOUD, cloud.kind)
            assertFalse(env.database(cloud) { it.operations().state()!!.cloudEnabled })
            assertEquals(before.commands, env.database(cloud) { it.budget().content()!!.commands })
            env.uploadConnection.connect("owner", cloud.id, "Не заменяется")
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
            }
            assertEquals(1, remote.imports.size)
        }
    }

    @Test fun accountSwitchAfterServerCommitCannotSendCommandsUsingAnotherAccountsIdentity() = runBlocking {
        val remote = UploadServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = remote).use { env ->
            val local = env.seedLegacy()
            env.uploadConnection.connect("owner", local.id, "Приватный")
            val cloud = env.manager.active.value!!.profile
            remote.afterImport = { env.auth.login("other") }
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Blocked, lease.scope.get<OperationsSynchronizer>().synchronize())
            }
            assertTrue(remote.target.entries.isEmpty())
            env.manager.restore()
            assertNotEquals(local.id, env.manager.active.value!!.profile.id)
            remote.afterImport = {}
            env.auth.login("owner"); env.manager.restore()
            env.uploadConnection.connect("owner", local.id, "Приватный")
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
            }
            assertEquals(1, remote.imports.size)
            assertEquals(1, remote.target.entries.size)
        }
    }

    internal class UploadServer : FirebaseBudgetApi {
        override suspend fun rename(authorization: String, id: String, request: com.firkat.fillin.feature.budgetconnection.data.RenameBudgetDto): retrofit2.Response<com.firkat.fillin.feature.budgetconnection.data.AvailableBudgetDto> = error("Not used in this fixture")
        override suspend fun delete(authorization: String, id: String, requestId: String): retrofit2.Response<Unit> = error("Not used in this fixture")
        val other = BudgetConnectionDeviceTest.Server()
        val target = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
        val imports = linkedMapOf<String, ImportFirebaseBudgetDto>()
        val calls = mutableListOf<ImportFirebaseBudgetDto>()
        var loseImportResponse = false
        var afterImport: () -> Unit = {}
        override suspend fun importLocal(authorization: String, request: ImportFirebaseBudgetDto): Response<AvailableBudgetDto> {
            check(authorization == "Bearer synthetic-owner")
            calls += request
            val saved = imports[request.budget.requestId]
            if (saved != null && saved != request) return Response.error(409, "{}".toResponseBody())
            if (saved == null) {
                imports[request.budget.requestId] = request
                val b = request.budget
                target.scope = SyncScopeDto(b.householdId, b.memberId, "OWNER", LocalDate.parse(b.accountingStartedOn).atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant().toString(), "Asia/Almaty", "KZT")
                target.categories = b.categories.map { LocalCategory(target.scope.key, it.id, it.name, it.kind, it.icon, "", false, 0, it.dictionaryKey) }
                target.openingBalance = b.openingBalance
                target.budgetName = request.name
                target.budgetExists = true
            }
            afterImport()
            if (loseImportResponse) { loseImportResponse = false; throw IOException("Synthetic lost import response after commit") }
            val summary = target.summary()
            return Response.success(AvailableBudgetDto(summary.id, summary.name, summary.memberId, summary.role))
        }
        private fun server(id: String) = if (id == target.scope.householdId && target.budgetExists) target else other
        override suspend fun create(authorization: String, request: CreateFirebaseBudgetDto): Response<AvailableBudgetDto> = error("Not used")
        override suspend fun budgets(authorization: String) = Response.success(if (authorization == "Bearer synthetic-owner") listOfNotNull(other.summary(), target.summary().takeIf { target.budgetExists }).map { AvailableBudgetDto(it.id, it.name, it.memberId, it.role) } else emptyList())
        override suspend fun budget(authorization: String, budgetId: String) = server(budgetId).budget(authorization, budgetId)
        override suspend fun changes(authorization: String, budgetId: String, cursor: String?) = server(budgetId).changes(authorization, budgetId, cursor)
        override suspend fun command(authorization: String, budgetId: String, command: BudgetSyncCommand) = server(budgetId).command(authorization, budgetId, command)
    }
}

package com.firkat.fillin.feature.budgetconnection

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetconnection.data.FirebaseBudgetApi
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID

/** The optional prepare/recover phases allow the host to kill the real app process between them. */
class BudgetProcessRecoveryTest {
    @Test fun committedCommandRecoversAfterProcessRestartWithoutChangingItsIdentityOrMoney() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("t11Phase")
        val runId = args.getString("t11Run") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-f0-9-]{36}")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val journal = File(context.filesDir, "t11-process-$runId.json")
        if (phase != "recover") prepare(journal, phase == "prepare")
        recover(journal, phase == "recover")
    }

    private suspend fun prepare(journal: File, waitForKill: Boolean) {
        val server = BudgetConnectionDeviceTest.Server()
        lateinit var env: BudgetConnectionDeviceTest.Environment
        val api = object : FirebaseBudgetApi by server {
            override suspend fun command(authorization: String, budgetId: String, command: BudgetSyncCommand): Response<BudgetCommandReceipt> {
                val response = server.command(authorization, budgetId, command)
                // Synthetic remote state survives the app process in a private test-only journal.
                // A real backend persists this transaction independently of the Android process.
                val page = server.changes(authorization, budgetId, null).body()!!
                val fixture = Fixture(env.registryName, env.backend, env.manager.active.value!!.profile.id,
                    page, command, response.body()!!, Process.myPid())
                journal.writeText(json.encodeToString(fixture))
                assertTrue(env.manager.active.value!!.scope.get<OperationDatabase>().operations().state()!!.isSyncing)
                if (waitForKill) awaitCancellation()
                throw IOException("Synthetic response lost after commit")
            }
        }
        env = BudgetConnectionDeviceTest.Environment(server = server, apiOverride = api, deleteOnClose = false)
        try {
            env.auth.login("owner"); env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
            BudgetRecoveryDeviceTest.expense(env, "12.34")
            assertEquals(SyncResult.Retry, BudgetRecoveryDeviceTest.sync(env))
            // Default suite exercises the same durable state without terminating its own runner.
            env.manager.active.value!!.scope.get<OperationDatabase>().operations().let { dao ->
                dao.putState(dao.state()!!.copy(isSyncing = true))
            }
        } finally { env.close() }
    }

    private suspend fun recover(journal: File, requireNewProcess: Boolean) {
        val saved = json.decodeFromString<Fixture>(journal.readText())
        if (requireNewProcess) assertNotEquals(saved.processId, Process.myPid())
        val server = BudgetConnectionDeviceTest.Server().apply {
            scope = saved.page.scope
            categories = saved.page.changes.mapNotNull { it.category }.map {
                LocalCategory(scope.key, it.id, it.name, it.kind, it.iconCode, it.iconEmoji, it.archived, 0, it.dictionaryKey)
            }
            openingBalance = saved.page.changes.single { it.budgetSettings != null }.budgetSettings!!.openingBalance
            entries.addAll(saved.page.changes.mapNotNull { it.operation })
            receipts[saved.command.requestId] = saved.receipt
        }
        val api = object : FirebaseBudgetApi by server {
            override suspend fun command(authorization: String, budgetId: String, command: BudgetSyncCommand): Response<BudgetCommandReceipt> {
                assertEquals(saved.command, command)
                return server.command(authorization, budgetId, command)
            }
        }
        BudgetConnectionDeviceTest.Environment(server = server, registryName = saved.registryName, backend = saved.backend, apiOverride = api).use { env ->
            val profile = env.registry.find(saved.profileId)!!
            env.database(profile) { db ->
                assertTrue(db.operations().state()!!.isSyncing)
                val queued = db.budget().content()!!.commands.single()
                assertEquals(saved.command.requestId, queued.requestId)
                assertEquals(saved.command, json.decodeFromString<BudgetSyncCommand>(queued.wirePayload!!))
            }
            env.auth.login("owner"); env.manager.restore()
            env.manager.acquire(profile.syncIdentity)!!.use { lease ->
                val db = lease.scope.get<OperationDatabase>()
                assertFalse(db.operations().state()!!.isSyncing)
                assertEquals("sync_interrupted", db.operations().state()!!.errorCode)
                assertFalse(lease.scope.get<BudgetStore>().context.first().syncing)
            }
            assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(env))
            assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(env))
            assertEquals(1, server.receipts.size)
            assertEquals("12.34", server.entries.single().amountKzt)
            env.database(profile) { db ->
                val content = db.budget().content()!!
                assertEquals(saved.command.entityId, content.operations.single().id)
                assertEquals("12.34", content.operations.single().amountKzt)
                assertTrue(content.commands.isEmpty())
                assertFalse(content.sync.isSyncing)
                assertNull(content.sync.errorCode)
            }
        }
        check(journal.delete())
    }

    @Serializable private data class Fixture(val registryName: String, val backend: String, val profileId: String,
        val page: SyncPageDto, val command: BudgetSyncCommand, val receipt: BudgetCommandReceipt, val processId: Int)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
}

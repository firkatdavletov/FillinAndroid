package com.firkat.fillin.feature.budgetconnection

import androidx.room.Room
import com.firkat.fillin.core.database.BudgetContent
import com.firkat.fillin.feature.sync.data.SyncBudgetSettings
import kotlinx.serialization.json.Json
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.BudgetStore
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class BudgetCreationDeviceTest {
    private fun openingBalance(content: BudgetContent) = Json.decodeFromString<SyncBudgetSettings>(content.records.single { it.kind == "BUDGET_SETTINGS" }.payload).openingBalance
    private fun draft() = BudgetCreationDraft("Новый бюджет", "450,50", LocalDate.now().minusDays(1).toString())

    @Test fun createdBudgetKeepsOperationsAfterReopeningAndDownloadsToIndependentDeviceStorage() = runBlocking {
        val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
        val first = BudgetConnectionDeviceTest.Environment(server, deleteOnClose = false)
        val registryName = first.registryName
        val backend = first.backend
        val local = first.seedLegacy()
        server.budgetExists = false
        val before = first.database(local) { it.budget().content()!! }
        first.creation.create("owner", draft())
        val cloud = first.manager.active.value!!.profile
        assertNotEquals(local.databaseName, cloud.databaseName)
        assertEquals("450.50", first.database(cloud) { openingBalance(it.budget().content()!!) })
        assertEquals(before, first.database(local) { it.budget().content()!! })
        assertNull(first.creation.pending("owner"))
        first.manager.acquire(cloud.syncIdentity)!!.use { lease ->
            val session = lease.scope.get<CloudBudgetSession>()
            lease.scope.get<OperationHistoryStore>().create(NewOperation(OperationType.Expense,
                server.categories.first().id, "12.34".toBigDecimal(), session.today, null), null)
            assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
            assertTrue(lease.scope.get<BudgetStore>().pending().isEmpty())
        }
        first.close()
        BudgetConnectionDeviceTest.Environment(server, registryName, backend).use { reopened ->
            reopened.auth.login("owner"); reopened.manager.restore()
            assertEquals(cloud, reopened.manager.active.value!!.profile)
            assertEquals("12.34", reopened.database(cloud) { it.budget().content()!!.operations.single().amountKzt })
            BudgetConnectionDeviceTest.Environment(server).use { secondDevice ->
                secondDevice.auth.login("owner"); secondDevice.manager.restore()
                secondDevice.connection.connect("owner", secondDevice.connection.budgets("owner").single())
                val second = secondDevice.manager.active.value!!.profile
                assertNotEquals(cloud.databaseName, second.databaseName)
                assertEquals(cloud.budgetId, second.budgetId)
                val content = secondDevice.database(second) { it.budget().content()!! }
                assertEquals("450.50", openingBalance(content))
                assertEquals("12.34", content.operations.single().amountKzt)
            }
        }
    }

    @Test fun lostResponseSurvivesProcessRecreationAndRetryReusesRequestAndBudget() = runBlocking {
        val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false; loseCreationResponse = true }
        val first = BudgetConnectionDeviceTest.Environment(server, deleteOnClose = false)
        first.auth.login("owner"); first.manager.restore()
        assertEquals("network", (runCatching { first.creation.create("owner", draft()) }.exceptionOrNull() as BudgetConnectionFailure).code)
        val pending = first.creation.pending("owner")!!
        val registryName = first.registryName
        val backend = first.backend
        assertEquals(1, server.creations.size)
        first.close()
        BudgetConnectionDeviceTest.Environment(server, registryName, backend).use { resumed ->
            resumed.auth.login("other"); resumed.manager.restore()
            assertNull(resumed.creation.pending("other"))
            assertEquals("account_changed", (runCatching { resumed.creation.resume("owner") }.exceptionOrNull() as BudgetConnectionFailure).code)
            resumed.auth.login("owner"); resumed.manager.restore()
            assertEquals(pending, resumed.creation.pending("owner"))
            assertEquals("creation_pending", (runCatching { resumed.creation.create("owner", draft().copy(name = "Другой")) }.exceptionOrNull() as BudgetConnectionFailure).code)
            resumed.creation.resume("owner")
            assertNull(resumed.creation.pending("owner"))
            assertEquals(server.creations.values.single().second.id, resumed.manager.active.value!!.profile.budgetId)
            assertEquals(1, server.creations.size)
            assertEquals(pending.requestId, server.creations.keys.single())
        }
    }

    @Test fun downloadFailureKeepsCreationReceiptAndRetryCompletesWithoutAnotherBudget() = runBlocking {
        val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false; failSecondPage = true }
        BudgetConnectionDeviceTest.Environment(server).use { env ->
            env.auth.login("owner"); env.manager.restore()
            val original = env.manager.active.value!!.profile
            assertTrue(runCatching { env.creation.create("owner", draft()) }.isFailure)
            assertEquals(original, env.manager.active.value!!.profile)
            assertNotNull(env.creation.pending("owner"))
            server.failSecondPage = false
            env.creation.resume("owner")
            assertEquals(1, server.creations.size)
            assertEquals(BudgetProfileKind.CLOUD, env.manager.active.value!!.profile.kind)
            assertNull(env.creation.pending("owner"))
        }
    }

    @Test fun profileRegistryMigrationPreservesExistingSelectionAndCanPersistCreationRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "t06-migration-${UUID.randomUUID()}.db"
        val backend = "https://migration.invalid/"
        try {
            // Exact v1 table and index schema, from the checked-in schema artifact.
            val schema = org.json.JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase/1.json").bufferedReader().readText()).getJSONObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                old.execSQL("INSERT INTO budget_profiles VALUES ('local','budget-synthetic.db','LOCAL',NULL,NULL,NULL)")
                old.execSQL("INSERT INTO budget_profile_selection VALUES ('local','local')")
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.version = 1
            }
            Room.databaseBuilder(context, BudgetProfileDatabase::class.java, name).addMigrations(*PROFILE_MIGRATIONS).build().use { db ->
                val registry = RoomBudgetProfileRegistry(db, backend)
                assertEquals("local", registry.selected(null).id)
                val store = com.firkat.fillin.feature.budgetconnection.data.RoomBudgetCreationStore(db)
                val pending = PendingBudgetCreation(UUID.randomUUID().toString(), draft())
                assertEquals(pending, store.save("owner", pending))
                assertEquals(pending, store.pending("owner"))
                assertNull(store.pending("other"))
            }
        } finally { context.deleteDatabase(name) }
    }
}

private inline fun <T : androidx.room.RoomDatabase, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }

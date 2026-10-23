package com.firkat.fillin.feature.budgetconnection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.AvailableBudget
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BudgetCatalogMigrationTest {
    @Test fun migrationFromV3PreservesProfilesSelectionsPendingCreationAndUploadWhileAddingPrivateCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "t08-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO budget_profiles VALUES ('first','budget-one.db','CLOUD','owner','budget-one','OWNER')")
                old.execSQL("INSERT INTO budget_profiles VALUES ('second','budget-two.db','CLOUD','owner','budget-two','MEMBER')")
                old.execSQL("INSERT INTO budget_profile_selection VALUES ('uid:owner','second')")
                old.execSQL("INSERT INTO pending_budget_creation VALUES ('owner','original-request','Незавершённый','123.45','2026-10-20')")
                old.execSQL("INSERT INTO local_budget_upload VALUES ('first','owner','Из офлайна','original-bootstrap')")
                old.version = 3
            }
            val database = Room.databaseBuilder(context, BudgetProfileDatabase::class.java, name).addMigrations(PROFILE_MIGRATION_3_4).build()
            try {
                val registry = RoomBudgetProfileRegistry(database, "https://migration.invalid/")
                assertEquals("second", registry.selected("owner").id)
                assertEquals("budget-one.db", registry.find("first")!!.databaseName)
                assertEquals("MEMBER", registry.find("second")!!.role)
                assertEquals("original-request", RoomBudgetCreationStore(database).pending("owner")!!.requestId)
                assertEquals("original-bootstrap", RoomLocalBudgetUploadStore(database).find("first")!!.bootstrapPayload)
                val catalog = RoomBudgetCatalogStore(database)
                assertTrue(catalog.budgets("owner").isEmpty())
                val budget = AvailableBudget("budget-one", "Первый", "member-one", "OWNER")
                catalog.replace("owner", listOf(budget))
                catalog.remember("other", budget.copy(name = "Чужой"))
                assertEquals(listOf(budget), catalog.budgets("owner"))
                catalog.replace("owner", emptyList())
                assertEquals("Чужой", catalog.budgets("other").single().name)
                assertEquals("second", registry.selected("owner").id)
            } finally { database.close() }
        } finally { context.deleteDatabase(name) }
    }
}

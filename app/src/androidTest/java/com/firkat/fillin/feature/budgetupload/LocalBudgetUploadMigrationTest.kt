package com.firkat.fillin.feature.budgetupload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetconnection.data.RoomBudgetCreationStore
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalBudgetUploadMigrationTest {
    @Test fun registryMigrationKeepsBothBudgetProfilesSelectionAndUnfinishedCreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "t07-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets.open(
                "com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase/2.json"
            ).bufferedReader().use {
                it.readText()
            }
        ).getJSONObject("database")
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) {
                        old.execSQL(
                            indices.getJSONObject(
                                j
                            ).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))
                        )
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL(
                    "INSERT INTO budget_profiles VALUES ('local','budget-local-synthetic.db','LOCAL',NULL,NULL,NULL)"
                )
                old.execSQL(
                    "INSERT INTO budget_profiles VALUES ('cloud','budget-cloud-synthetic.db','CLOUD','owner','old-budget','OWNER')"
                )
                old.execSQL("INSERT INTO budget_profile_selection VALUES ('local','local')")
                old.execSQL("INSERT INTO budget_profile_selection VALUES ('uid:owner','cloud')")
                old.execSQL(
                    "INSERT INTO pending_budget_creation VALUES ('owner','original-request','Незавершённый','123.45','2026-10-20')"
                )
                old.version = 2
            }
            val db = Room.databaseBuilder(
                context,
                BudgetProfileDatabase::class.java,
                name
            ).addMigrations(*PROFILE_MIGRATIONS).build()
            try {
                val registry = RoomBudgetProfileRegistry(db, "https://migration.invalid/")
                assertEquals("cloud", registry.selected("owner").id)
                assertEquals("local", registry.selected(null).id)
                val creation = RoomBudgetCreationStore(db).pending("owner")!!
                val local = registry.find("local")!!
                val uploads = RoomLocalBudgetUploadStore(db)
                uploads.bind(local, "owner", "new-budget", "Из офлайна", "immutable-bootstrap")
                assertEquals("budget-local-synthetic.db", registry.find("local")!!.databaseName)
                assertEquals("local", registry.selected("owner").id)
                assertFalse(registry.available("other").any { it.id == "local" || it.id == "cloud" })
                assertEquals("immutable-bootstrap", uploads.find("local")!!.bootstrapPayload)
                assertEquals(creation, RoomBudgetCreationStore(db).pending("owner"))
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

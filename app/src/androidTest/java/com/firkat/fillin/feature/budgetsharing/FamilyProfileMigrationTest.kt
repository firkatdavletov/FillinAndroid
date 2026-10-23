package com.firkat.fillin.feature.budgetsharing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetprofile.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FamilyProfileMigrationTest {
    @Test fun V4ProfilesAndSelectionSurviveWhileArchivedMembershipKeepsItsFileAndIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "t10-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase/4.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
                old.execSQL("INSERT INTO budget_profiles VALUES ('owner','budget-owner.db','CLOUD','other','personal','OWNER')")
                old.execSQL("INSERT INTO budget_profiles VALUES ('member','budget-member.db','CLOUD','other','family','MEMBER')")
                old.execSQL("INSERT INTO budget_profile_selection VALUES ('uid:other','member')")
                old.execSQL("INSERT INTO cloud_budget_catalog VALUES ('other','family','Семейный','member-id','MEMBER')")
                old.version = 4
            }
            val database = Room.databaseBuilder(context, BudgetProfileDatabase::class.java, name).addMigrations(PROFILE_MIGRATION_4_5).build()
            try {
                val registry = RoomBudgetProfileRegistry(database, "https://migration.invalid/")
                val original = registry.selected("other")
                assertEquals("member", original.id)
                assertFalse(original.archived)
                assertEquals(2, registry.available("other").count { it.firebaseUid == "other" })
                val replacementId = UUID.randomUUID().toString()
                val replacement = com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile(replacementId, "budget-$replacementId.db", com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind.CLOUD, "other", "family", "MEMBER")
                registry.replaceCloudMembership("member", replacement)
                val archived = registry.find("member")!!
                assertTrue(archived.archived)
                assertEquals(original.databaseName, archived.databaseName)
                assertEquals(original.budgetId, archived.budgetId)
                assertFalse(archived.accessibleTo("other"))
                assertNotEquals("member", registry.selected("other").id)
                val renewed = registry.registerCloud("other", "family", "MEMBER")
                assertNotEquals(original.id, renewed.id)
                assertNotEquals(original.databaseName, renewed.databaseName)
                assertEquals(1, registry.available("other").count { it.budgetId == "family" })
                assertEquals("Семейный", database.catalog().budgets("other").single().name)
            } finally { database.close() }
        } finally { context.deleteDatabase(name) }
    }
}

package com.firkat.fillin.feature.profile

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase
import com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BudgetProfileMigrationTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BudgetProfileDatabase::class.java)

    @Test fun versionFiveProfilesKeepTheirDataAndHaveNoName() {
        val database = "profile-migration-5-6"
        migration.createDatabase(database, 5).use { db ->
            db.execSQL("INSERT INTO budget_profiles (id, databaseName, kind, firebaseUid, budgetId, role, activeCloudKey) VALUES ('local', 'local.db', 'LOCAL', NULL, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO budget_profile_selection (accountKey, profileId) VALUES ('local', 'local')")
        }
        migration.runMigrationsAndValidate(database, 6, true, PROFILE_MIGRATION_5_6).use { db ->
            db.query("SELECT id, databaseName, name FROM budget_profiles").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("local", cursor.getString(0))
                assertEquals("local.db", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
            db.query("SELECT profileId FROM budget_profile_selection").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("local", cursor.getString(0))
            }
        }
    }
}

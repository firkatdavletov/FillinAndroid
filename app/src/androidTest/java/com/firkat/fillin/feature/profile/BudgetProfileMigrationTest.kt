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
    @get:Rule val migration =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BudgetProfileDatabase::class.java)

    @Test fun versionSixProfilesSurviveManagementMigration() {
        val name = "profile-migration-6-7"
        migration.createDatabase(name, 6).use { db ->
            db.execSQL(
                "INSERT INTO budget_profiles (id, databaseName, kind, firebaseUid, budgetId, role, activeCloudKey, name) VALUES ('local', 'local.db', 'LOCAL', NULL, NULL, NULL, NULL, 'Семья')"
            )
            db.execSQL("INSERT INTO budget_profile_selection (accountKey, profileId) VALUES ('local', 'local')")
        }
        migration.runMigrationsAndValidate(
            name,
            7,
            true,
            com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_6_7
        ).use { db ->
            db.query("SELECT name FROM budget_profiles WHERE id='local'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Семья", cursor.getString(0))
            }
            db.query("SELECT count(*) FROM budget_management").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun pendingCreationAndCatalogDefaultToTengeOnUpgrade() {
        val name = "profile-migration-7-8"
        migration.createDatabase(name, 7).use { db ->
            db.execSQL("INSERT INTO pending_budget_creation VALUES ('owner', 'request', 'Дом', '10.00', '2026-10-01')")
            db.execSQL("INSERT INTO cloud_budget_catalog VALUES ('owner', 'budget', 'Дом', 'member', 'OWNER')")
        }
        migration.runMigrationsAndValidate(
            name,
            8,
            true,
            com.firkat.fillin.feature.budgetprofile.data.PROFILE_MIGRATION_7_8
        ).use { db ->
            listOf("pending_budget_creation", "cloud_budget_catalog").forEach { table ->
                db.query("SELECT currency FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("KZT", cursor.getString(0))
                }
            }
        }
    }

    @Test fun versionFiveProfilesKeepTheirDataAndHaveNoName() {
        val database = "profile-migration-5-6"
        migration.createDatabase(database, 5).use { db ->
            db.execSQL(
                "INSERT INTO budget_profiles (id, databaseName, kind, firebaseUid, budgetId, role, activeCloudKey) VALUES ('local', 'local.db', 'LOCAL', NULL, NULL, NULL, NULL)"
            )
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

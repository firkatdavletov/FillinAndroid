package com.firkat.fillin.feature.budgetprofile.data

import androidx.room.*
import com.firkat.fillin.feature.budgetconnection.data.BudgetCatalogDao
import com.firkat.fillin.feature.budgetconnection.data.CachedCloudBudget
import com.firkat.fillin.feature.budgetupload.data.LocalBudgetUploadDao
import com.firkat.fillin.feature.budgetupload.data.LocalBudgetUploadEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.firkat.fillin.feature.budgetconnection.data.BudgetCreationDao
import com.firkat.fillin.feature.budgetconnection.data.PendingBudgetCreationEntity

@Entity(tableName = "budget_profiles", indices = [Index("databaseName", unique = true), Index(value = ["firebaseUid", "activeCloudKey"], unique = true)])
data class BudgetProfileEntity(
    @PrimaryKey val id: String,
    val databaseName: String,
    val kind: String,
    val firebaseUid: String?,
    val budgetId: String?,
    val role: String?,
    val activeCloudKey: String? = budgetId,
    val name: String? = null,
)

@Entity(tableName = "budget_profile_selection")
data class BudgetProfileSelection(@PrimaryKey val accountKey: String, val profileId: String)

@Dao
interface BudgetProfileDao {
    @Query("SELECT * FROM budget_profiles WHERE firebaseUid = :uid")
    suspend fun allCloud(uid: String): List<BudgetProfileEntity>
    @Query("DELETE FROM local_budget_upload WHERE profileId = :id")
    suspend fun deleteUpload(id: String)
    @Query("UPDATE budget_profile_selection SET profileId = 'choose-budget' WHERE profileId = :id")
    suspend fun markSelectionRequired(id: String)

    @Query("DELETE FROM budget_profile_selection WHERE profileId = :id")
    suspend fun deleteSelection(id: String)
    @Query("DELETE FROM budget_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
    @Query("DELETE FROM pending_budget_creation WHERE firebaseUid = :uid")
    suspend fun deleteCreation(uid: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: BudgetProfileEntity)

    @Update
    suspend fun update(profile: BudgetProfileEntity)

    @Query("SELECT * FROM budget_profiles WHERE id = :id")
    suspend fun find(id: String): BudgetProfileEntity?

    @Query("SELECT * FROM budget_profiles WHERE firebaseUid = :uid AND activeCloudKey = :budgetId")
    suspend fun cloud(uid: String, budgetId: String): BudgetProfileEntity?

    @Query("SELECT * FROM budget_profiles WHERE (firebaseUid IS NULL OR firebaseUid = :uid) AND (kind != 'CLOUD' OR activeCloudKey IS NOT NULL) ORDER BY id")
    suspend fun available(uid: String?): List<BudgetProfileEntity>

    @Query("SELECT profileId FROM budget_profile_selection WHERE accountKey = :accountKey")
    suspend fun selection(accountKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun select(selection: BudgetProfileSelection)
}

@Database(entities = [BudgetProfileEntity::class, BudgetProfileSelection::class, PendingBudgetCreationEntity::class, LocalBudgetUploadEntity::class, CachedCloudBudget::class, com.firkat.fillin.feature.budgetmanagement.data.BudgetManagementEntry::class], version = 7, exportSchema = true)
abstract class BudgetProfileDatabase : RoomDatabase() {
    abstract fun management(): com.firkat.fillin.feature.budgetmanagement.data.BudgetManagementDao
    abstract fun profiles(): BudgetProfileDao
    abstract fun creation(): BudgetCreationDao
    abstract fun uploads(): LocalBudgetUploadDao
    abstract fun catalog(): BudgetCatalogDao
}

val PROFILE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_budget_creation (firebaseUid TEXT NOT NULL PRIMARY KEY, requestId TEXT NOT NULL, name TEXT NOT NULL, openingBalance TEXT NOT NULL, accountingStartedOn TEXT NOT NULL)")
    }
}

val PROFILE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS local_budget_upload (profileId TEXT NOT NULL PRIMARY KEY, firebaseUid TEXT NOT NULL, name TEXT NOT NULL, bootstrapPayload TEXT NOT NULL, FOREIGN KEY(profileId) REFERENCES budget_profiles(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
    }
}

val PROFILE_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_budget_catalog (firebaseUid TEXT NOT NULL, budgetId TEXT NOT NULL, name TEXT NOT NULL, memberId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(firebaseUid, budgetId))")
    }
}

val PROFILE_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budget_profiles ADD COLUMN activeCloudKey TEXT")
        db.execSQL("UPDATE budget_profiles SET activeCloudKey = budgetId WHERE kind = 'CLOUD'")
        db.execSQL("DROP INDEX index_budget_profiles_firebaseUid_budgetId")
        db.execSQL("CREATE UNIQUE INDEX index_budget_profiles_firebaseUid_activeCloudKey ON budget_profiles(firebaseUid, activeCloudKey)")
    }
}

val PROFILE_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budget_profiles ADD COLUMN name TEXT")
    }
}

val PROFILE_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS budget_management (requestId TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, uid TEXT, budgetId TEXT, name TEXT NOT NULL, deleting INTEGER NOT NULL, createdAt INTEGER NOT NULL, error TEXT)")
    }
}

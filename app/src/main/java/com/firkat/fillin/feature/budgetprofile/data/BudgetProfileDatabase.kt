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
)

@Entity(tableName = "budget_profile_selection")
data class BudgetProfileSelection(@PrimaryKey val accountKey: String, val profileId: String)

@Dao
interface BudgetProfileDao {
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

@Database(entities = [BudgetProfileEntity::class, BudgetProfileSelection::class, PendingBudgetCreationEntity::class, LocalBudgetUploadEntity::class, CachedCloudBudget::class], version = 5, exportSchema = true)
abstract class BudgetProfileDatabase : RoomDatabase() {
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

package com.firkat.fillin.feature.budgetprofile.data

import androidx.room.*

@Entity(tableName = "budget_profiles", indices = [Index("databaseName", unique = true), Index(value = ["firebaseUid", "budgetId"], unique = true)])
data class BudgetProfileEntity(
    @PrimaryKey val id: String,
    val databaseName: String,
    val kind: String,
    val firebaseUid: String?,
    val budgetId: String?,
    val role: String?,
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

    @Query("SELECT * FROM budget_profiles WHERE firebaseUid = :uid AND budgetId = :budgetId")
    suspend fun cloud(uid: String, budgetId: String): BudgetProfileEntity?

    @Query("SELECT * FROM budget_profiles WHERE firebaseUid IS NULL OR firebaseUid = :uid ORDER BY id")
    suspend fun available(uid: String?): List<BudgetProfileEntity>

    @Query("SELECT profileId FROM budget_profile_selection WHERE accountKey = :accountKey")
    suspend fun selection(accountKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun select(selection: BudgetProfileSelection)
}

@Database(entities = [BudgetProfileEntity::class, BudgetProfileSelection::class], version = 1, exportSchema = true)
abstract class BudgetProfileDatabase : RoomDatabase() {
    abstract fun profiles(): BudgetProfileDao
}

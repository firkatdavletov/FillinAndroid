package com.firkat.fillin.feature.budgetupload.data

import androidx.room.*
import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.budgetupload.domain.*

@Entity(tableName = "local_budget_upload", foreignKeys = [ForeignKey(entity = BudgetProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"])])
data class LocalBudgetUploadEntity(@PrimaryKey val profileId: String, val firebaseUid: String, val name: String, val bootstrapPayload: String)

@Dao
interface LocalBudgetUploadDao {
    @Query("SELECT * FROM local_budget_upload WHERE profileId = :profileId")
    suspend fun find(profileId: String): LocalBudgetUploadEntity?
    @Insert
    suspend fun insert(row: LocalBudgetUploadEntity)
}

/** Ownership and the immutable bootstrap commit in the same registry transaction, before any HTTP. */
class RoomLocalBudgetUploadStore(private val database: BudgetProfileDatabase) : LocalBudgetUploadStore {
    override suspend fun find(profileId: String) = database.uploads().find(profileId)?.let { LocalBudgetUpload(it.profileId, it.firebaseUid, it.name, it.bootstrapPayload) }
    override suspend fun bind(profile: BudgetProfile, uid: String, budgetId: String, name: String, bootstrapPayload: String) {
        database.withTransaction {
            val original = requireNotNull(database.profiles().find(profile.id))
            require(original.kind != BudgetProfileKind.CLOUD.name && original.databaseName == profile.databaseName)
            require(database.profiles().cloud(uid, budgetId) == null)
            database.uploads().insert(LocalBudgetUploadEntity(profile.id, uid, name, bootstrapPayload))
            database.profiles().update(original.copy(kind = BudgetProfileKind.CLOUD.name, firebaseUid = uid, budgetId = budgetId, role = "OWNER"))
            database.profiles().select(BudgetProfileSelection("uid:$uid", profile.id))
        }
    }
}

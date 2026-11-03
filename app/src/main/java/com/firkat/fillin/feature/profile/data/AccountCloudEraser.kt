package com.firkat.fillin.feature.profile.data

import android.content.Context
import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetprofile.data.*

class AccountCloudEraser(private val context: Context, private val database: BudgetProfileDatabase, private val active: ActiveBudgetManager) {
    suspend fun pause(uid: String) {
        database.profiles().allCloud(uid).forEach { row ->
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(BudgetWorkIdentity.uniqueName(com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity(row.id, uid)))
        }
        active.quiesce(uid)
    }
    suspend fun erase(uid: String, removedBudgetIds: Set<String>? = null) {
        val rows = database.profiles().allCloud(uid).filter { removedBudgetIds == null || it.budgetId in removedBudgetIds }
        if (rows.any { it.id == active.active.value?.profile?.id }) {
            RoomBudgetProfileRegistry(database, com.firkat.fillin.BuildConfig.FIREBASE_API_BASE_URL).deselect(uid)
        }
        rows.forEach { database.profiles().markSelectionRequired(it.id); active.detach(it.id) }
        for (row in rows) {
            check(!context.getDatabasePath(row.databaseName).exists() || context.deleteDatabase(row.databaseName))
            database.withTransaction {
                database.profiles().deleteUpload(row.id)
                database.profiles().deleteSelection(row.id)
                database.profiles().deleteProfile(row.id)
                database.management().removeProfile(row.id)
            }
        }
        if (removedBudgetIds == null) database.withTransaction {
            database.catalog().clear(uid)
            database.profiles().deleteCreation(uid)
        }
    }
}

package com.firkat.fillin.feature.budgetprofile.data

import android.content.Context
import androidx.room.Room
import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase

class BudgetDatabaseFactory(private val context: Context) {
    /** Only an unregistered replacement cache may be discarded; registered financial files are retained. */
    fun discardUnregisteredCache(profile: BudgetProfile) { context.deleteDatabase(profile.databaseName) }
    fun open(profile: BudgetProfile): OperationDatabase = Room.databaseBuilder(context, OperationDatabase::class.java, profile.databaseName)
        .addMigrations(BUDGET_MIGRATION_1_2, BUDGET_MIGRATION_2_3, BUDGET_MIGRATION_3_4)
        .build()
}

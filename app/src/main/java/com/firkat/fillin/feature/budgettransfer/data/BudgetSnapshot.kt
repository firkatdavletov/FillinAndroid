package com.firkat.fillin.feature.budgettransfer.data

import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.core.database.OperationPlanProposal
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationOutbox
import kotlinx.serialization.Serializable

@Serializable
internal data class SnapshotMetadata(
    val formatVersion: Int,
    val name: String?,
    val currency: BudgetCurrency,
    val accountingStartedAt: String,
    val lastRevision: Long,
    val protocolVersion: Int
)

internal data class BudgetSnapshot(
    val metadata: SnapshotMetadata,
    val operations: List<LocalOperation>,
    val categories: List<LocalCategory>,
    val records: List<LocalSyncRecord>,
    val commands: List<BudgetOutbox>,
    val legacyCommands: List<OperationOutbox>,
    val proposals: List<OperationPlanProposal>,
    val usageEvents: List<CategoryUsageEvent>
)

internal typealias SnapshotRow = Pair<String, String>

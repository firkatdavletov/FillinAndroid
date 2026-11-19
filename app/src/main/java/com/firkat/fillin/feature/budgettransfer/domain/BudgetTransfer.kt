package com.firkat.fillin.feature.budgettransfer.domain

import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import kotlinx.coroutines.flow.Flow

internal interface BudgetTransfer {
    val exportSource: Flow<BudgetSyncIdentity?>

    suspend fun export(
        source: BudgetSyncIdentity,
        document: String
    )

    suspend fun import(
        document: String,
        fallbackName: String,
        nameSuffix: String
    ): Boolean
}

internal enum class TransferFailure { InvalidFile, UnsupportedVersion, FileAccess, LocalLimit, SourceUnavailable }

internal class BudgetTransferException(val reason: TransferFailure, val row: Int? = null) : Exception(reason.name)

package com.firkat.fillin.feature.operationhistory.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Operation(
    val id: String,
    val categoryName: String,
    val description: String?,
    val amount: BigDecimal,
    val currency: String,
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val occurredOn: LocalDate,
    val status: OperationStatus,
    val version: Long,
    val type: OperationType = OperationType.Expense,
    val categoryId: String = "",
    val syncStatus: OperationSyncStatus = OperationSyncStatus.Synced,
    val syncError: String? = null,
    val refundedAmount: BigDecimal = BigDecimal.ZERO,
    val legacyConversion: Boolean = false,
    val rubKztRate: BigDecimal? = null,
    val categoryIconCode: String = "",
) {
    val hasOriginalValuation: Boolean get() = legacyConversion || originalCurrency != currency
}

enum class OperationSyncStatus { Synced, Pending, Rejected }

enum class OperationType(
    val apiValue: String,
) {
    Expense("EXPENSE"),
    Income("INCOME"),
}

enum class OperationStatus {
    Posted,
    Voided,
    Unknown,
}

data class OperationPage(
    val items: List<Operation>,
    val nextCursor: Long?,
    val totals: OperationPeriodTotals = OperationPeriodTotals(),
    val isComplete: Boolean = true,
)

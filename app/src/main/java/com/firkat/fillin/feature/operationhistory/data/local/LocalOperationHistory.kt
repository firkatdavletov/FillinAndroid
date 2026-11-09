package com.firkat.fillin.feature.operationhistory.data.local

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Aggregate exact decimal values across the period, independently of page and type filters. */
internal fun localHistory(
    content: LocalHistoryContent?,
    filter: OperationHistoryFilter,
    limit: Int,
): OperationPage {
    require(limit > 0)
    val categories = content?.categories.orEmpty().associateBy { it.id }
    val period =
        content?.operations.orEmpty().filter {
            it.occurredOn >= filter.dateFrom.toString() &&
                it.occurredOn <= filter.dateTo.toString()
        }
    var expenses = BigDecimal.ZERO
    var incomes = BigDecimal.ZERO
    period.filter { it.status == "POSTED" && it.syncStatus != "REJECTED" }.forEach {
        when (it.type) {
            "EXPENSE" -> expenses += it.accountingAmount.toBigDecimal()
            "EXPENSE_REFUND" -> expenses -= it.accountingAmount.toBigDecimal()
            "INCOME" -> incomes += it.accountingAmount.toBigDecimal()
        }
    }
    val rows =
        period
            .filter {
                it.type != "EXPENSE_REFUND" &&
                    (filter.type == null || it.type == filter.type.apiValue) &&
                    (filter.categoryId == null || it.categoryId == filter.categoryId)
            }.sortedWith(
                compareByDescending<LocalOperation> { it.occurredOn }
                    .thenByDescending { Instant.parse(it.createdAt) }
                    .thenByDescending { it.sequence }
                    .thenByDescending { it.id },
            )
    return OperationPage(
        rows.take(limit).map { operationToDomain(it, content, categories) },
        if (rows.size >
            limit
        ) {
            limit.toLong()
        } else {
            null
        },
        OperationPeriodTotals(expenses, incomes, content?.sync?.currency ?: "KZT"),
        content?.sync?.let { it.localReady || it.initialSyncComplete } == true,
    )
}

/** Resolves an operation independently of the history's date/type filters and page size. */
internal fun localOperation(content: LocalHistoryContent?, id: String): Operation? =
    content?.operations?.firstOrNull { it.id == id && it.type != "EXPENSE_REFUND" }?.let {
        operationToDomain(it, content, content.categories.associateBy { category -> category.id })
    }

private fun operationToDomain(
    record: LocalOperation,
    content: LocalHistoryContent?,
    categories: Map<String, LocalCategory>,
): Operation = record.let {
    val budgetCurrency = content?.sync?.currency ?: "KZT"
    val historicalValuation = it.hasOriginalValuation
    val category = categories[it.categoryId]
    Operation(
        id = it.id,
        categoryName = category?.name ?: "Категория недоступна",
        categoryIconCode = category?.iconCode.orEmpty(),
        description = it.description,
        amount = it.accountingAmount.toBigDecimal(),
        currency = budgetCurrency,
        originalAmount = it.originalAmount.toBigDecimal(),
        originalCurrency = if (historicalValuation) it.originalCurrency else budgetCurrency,
        legacyConversion = historicalValuation,
        rubKztRate = it.rubKztRate?.toBigDecimal(),
        occurredOn = LocalDate.parse(it.occurredOn),
        status = if (it.status == "POSTED") OperationStatus.Posted else OperationStatus.Voided,
        version = it.version,
        type = if (it.type == "INCOME") OperationType.Income else OperationType.Expense,
        categoryId = it.categoryId,
        syncStatus =
            when (it.syncStatus) {
                "PENDING" -> OperationSyncStatus.Pending
                "REJECTED" -> OperationSyncStatus.Rejected
                else -> OperationSyncStatus.Synced
            },
        syncError = it.syncError,
        refundedAmount =
            content
                ?.operations
                .orEmpty()
                .filter { refund -> refund.refundOf == it.id && refund.status == "POSTED" && refund.syncStatus != "REJECTED" }
                .sumOf { refund -> refund.originalAmount.toBigDecimal() },
    )

}

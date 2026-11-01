package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationUpdate
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate

internal class FakeOfflineHistory :
    OperationHistoryStore,
    OperationHistoryRepository {
    override val context =
        MutableStateFlow(
            OfflineHistoryContext(
                categories =
                    listOf(
                        Category("expense-category", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "🍔")),
                        Category("income-category", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰")),
                    ),
                initialSyncComplete = true,
                isOnline = true,
                accountingStartedOn = LocalDate.parse("2026-01-01"),
            ),
        )
    val operations = MutableStateFlow(listOf(purchase("first")))
    val totals = MutableStateFlow(OperationPeriodTotals(BigDecimal("10.00"), BigDecimal("100.00")))
    val subscriptions = mutableListOf<Pair<OperationHistoryFilter, Int>>()
    var syncRequests = 0
    var created: NewOperation? = null
    var replaced: String? = null
    var creationError: Exception? = null
    var edits = 0
    var refunds = 0

    override fun observeOperation(id: String) = operations.map { rows -> rows.firstOrNull { it.id == id } }

    override fun observeHistory(
        filter: OperationHistoryFilter,
        limit: Int,
    ) = combine(operations, totals) { rows, sums ->
        val filtered =
            rows.filter {
                (filter.type == null || it.type == filter.type) && it.occurredOn >= filter.dateFrom &&
                    it.occurredOn <= filter.dateTo
            }
        OperationPage(filtered.take(limit), if (filtered.size > limit) limit.toLong() else null, sums, context.value.initialSyncComplete)
    }.also { subscriptions += filter to limit }

    override fun requestSync() {
        syncRequests++
    }

    override suspend fun create(
        operation: NewOperation,
        replacesRejectedId: String?,
    ): com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult {
        creationError?.let { throw it }
        created = operation
        replaced = replacesRejectedId
        operations.value = operations.value.filterNot { it.id == replacesRejectedId } +
            purchase(
                "created",
                operation.type,
            ).copy(amount = operation.amount, originalAmount = operation.amount, syncStatus = OperationSyncStatus.Pending)
        return com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("created")
    }

    override suspend fun getOperations(
        limit: Int,
        cursor: Long?,
        filter: OperationHistoryFilter,
    ): OperationPage = error("UI must observe local data")

    override suspend fun updateOperation(id: String, version: Long, update: OperationUpdate): Operation {
        val before = operations.value.first { it.id == id }
        val amountUpdate = updateOperationAmount(id, version, update.amount)
        val updated = amountUpdate.copy(categoryId = update.categoryId, description = update.description,
            categoryName = if (before.categoryId == update.categoryId) before.categoryName else context.value.categories.first { it.id == update.categoryId }.name)
        operations.value = operations.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun updateOperationAmount(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Operation {
        edits++
        val updated = operations.value.first { it.id == id }.copy(amount = amount, originalAmount = amount, version = version + 1)
        operations.value = operations.value.map { if (it.id == id) updated else it }
        totals.value = if (updated.type == OperationType.Expense) totals.value.copy(expenses = amount) else totals.value.copy(incomes = amount)
        return updated
    }

    override suspend fun refundPurchase(
        id: String,
        version: Long,
    ) {
        refunds++
        totals.value = totals.value.copy(expenses = BigDecimal.ZERO)
    }

    companion object {
        fun purchase(
            id: String,
            type: OperationType = OperationType.Expense,
        ) = Operation(
            id,
            "Категория",
            null,
            BigDecimal("10.00"),
            "KZT",
            BigDecimal("10.00"),
            "KZT",
            LocalDate.parse("2026-10-18"),
            OperationStatus.Posted,
            0,
            type,
            if (type == OperationType.Expense) "expense-category" else "income-category",
        )
    }
}

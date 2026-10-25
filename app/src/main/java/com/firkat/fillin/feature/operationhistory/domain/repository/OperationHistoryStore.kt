package com.firkat.fillin.feature.operationhistory.domain.repository

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class OfflineHistoryContext(
    val categories: List<Category> = emptyList(),
    val initialSyncComplete: Boolean = false,
    val isSyncing: Boolean = false,
    val isOnline: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val accountingStartedOn: LocalDate? = null,
    val syncError: String? = null,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
)

data class NewOperation(
    val type: OperationType,
    val categoryId: String,
    val amount: BigDecimal,
    val occurredOn: LocalDate,
    val description: String?,
)

interface OperationHistoryStore {
    fun observeOperation(id: String): Flow<Operation?>

    val context: Flow<OfflineHistoryContext>

    fun observeHistory(
        filter: OperationHistoryFilter,
        limit: Int,
    ): Flow<OperationPage>

    fun requestSync()

    suspend fun create(
        operation: NewOperation,
        replacesRejectedId: String? = null,
    )
}

package com.firkat.fillin.feature.categories.data.sync

import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetconnection.data.budgetBody
import com.firkat.fillin.feature.categories.data.local.CategoryUsageCursor
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CategoryUsageEventDto(
    val id: String,
    val operationId: String,
    val categoryId: String,
    val usedAtMillis: Long,
)

@Serializable
data class CategoryUsageBatch(val events: List<CategoryUsageEventDto>)

@Serializable
data class CategoryUsageReceipt(val scopeKey: String, val acceptedIds: List<String>)

@Serializable
data class CategoryUsagePage(
    val scopeKey: String,
    val events: List<CategoryUsageEventDto>,
    val nextCursor: String,
    val hasMore: Boolean,
)

class CategoryUsageSync(
    private val database: OperationDatabase,
    private val uid: String,
    private val budgetId: String,
    private val requests: CategoryUsageRequests,
) {
    private val dao get() = database.categoryUsage()

    suspend fun exchange(scopeKey: String, checkAccess: suspend () -> Unit) {
        require(scopeKey.substringBefore(':') == budgetId)
        while (true) {
            val pending = dao.pending(scopeKey)
            if (pending.isEmpty()) break
            checkAccess()
            val batch = CategoryUsageBatch(pending.map { it.toDto() })
            val receipt = requests.push(uid, budgetId, batch).budgetBody()
            require(receipt.scopeKey == scopeKey && receipt.acceptedIds.size == pending.size &&
                receipt.acceptedIds.toSet() == pending.map { it.id }.toSet())
            checkAccess()
            dao.acknowledge(scopeKey, receipt.acceptedIds)
        }

        while (true) {
            val cursor = dao.cursor(scopeKey)
            checkAccess()
            val response = requests.pull(uid, budgetId, cursor)
            if (response.code() == 410 && cursor != null) {
                response.errorBody()?.close()
                checkAccess()
                dao.resetCursor(scopeKey)
                continue
            }
            val page = response.budgetBody()
            require(page.scopeKey == scopeKey && page.nextCursor.isNotBlank() &&
                (!page.hasMore || page.nextCursor != cursor))
            checkAccess()
            database.withTransaction {
                for (event in page.events) {
                    require(isCanonicalUuid(event.id) && isCanonicalUuid(event.operationId) &&
                        isCanonicalUuid(event.categoryId) && event.usedAtMillis >= 0)
                    val row = CategoryUsageEvent(scopeKey, event.id, event.operationId, event.categoryId, event.usedAtMillis, true)
                    if (dao.insertRemote(row) == -1L) {
                        require(dao.forOperation(scopeKey, row.operationId)?.toDto() == event)
                    }
                }
                dao.saveCursor(CategoryUsageCursor(scopeKey, page.nextCursor))
            }
            if (!page.hasMore) break
        }
    }

    private fun CategoryUsageEvent.toDto() = CategoryUsageEventDto(id, operationId, categoryId, usedAtMillis)

    private fun isCanonicalUuid(value: String) = UUID.fromString(value).toString() == value
}

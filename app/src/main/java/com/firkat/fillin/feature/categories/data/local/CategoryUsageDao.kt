package com.firkat.fillin.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "category_usage_events",
    primaryKeys = ["scopeKey", "id"],
    indices = [
        Index(value = ["scopeKey", "operationId"], unique = true),
        Index(value = ["scopeKey", "categoryId", "usedAtMillis", "id"])
    ]
)
data class CategoryUsageEvent(
    val scopeKey: String,
    val id: String,
    val operationId: String,
    val categoryId: String,
    val usedAtMillis: Long,
    val delivered: Boolean = false
)

@Entity(tableName = "category_usage_cursors", primaryKeys = ["scopeKey"])
data class CategoryUsageCursor(val scopeKey: String, val cursor: String)

data class CategoryUsageTotal(val categoryId: String, val uses: Long, val lastUsedAt: Long, val lastEventId: String)

@Dao
interface CategoryUsageDao {
    @Query(
        """
        SELECT totals.categoryId, totals.uses, totals.lastUsedAt,
            (SELECT MAX(e.id) FROM category_usage_events e
             WHERE e.scopeKey = totals.scopeKey AND e.categoryId = totals.categoryId
               AND e.usedAtMillis = totals.lastUsedAt) AS lastEventId
        FROM (
            SELECT scopeKey, categoryId, COUNT(*) AS uses, MAX(usedAtMillis) AS lastUsedAt
            FROM category_usage_events
            WHERE scopeKey = (SELECT scopeKey FROM operation_sync_state WHERE id = 1)
            GROUP BY scopeKey, categoryId
        ) totals
    """
    )
    fun observeTotals(): Flow<List<CategoryUsageTotal>>

    @Query("SELECT * FROM category_usage_events WHERE scopeKey = :scopeKey ORDER BY usedAtMillis, id")
    suspend fun events(
        scopeKey: String
    ): List<CategoryUsageEvent>

    @Insert
    suspend fun record(
        event: CategoryUsageEvent
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemote(
        event: CategoryUsageEvent
    ): Long

    @Query("SELECT * FROM category_usage_events WHERE scopeKey = :scopeKey AND operationId = :operationId")
    suspend fun forOperation(
        scopeKey: String,
        operationId: String
    ): CategoryUsageEvent?

    @Query(
        "SELECT * FROM category_usage_events WHERE scopeKey = :scopeKey AND delivered = 0 ORDER BY usedAtMillis, id LIMIT 100"
    )
    suspend fun pending(
        scopeKey: String
    ): List<CategoryUsageEvent>

    @Query("UPDATE category_usage_events SET delivered = 1 WHERE scopeKey = :scopeKey AND id IN (:ids)")
    suspend fun acknowledge(
        scopeKey: String,
        ids: List<String>
    )

    @Query("SELECT cursor FROM category_usage_cursors WHERE scopeKey = :scopeKey")
    suspend fun cursor(
        scopeKey: String
    ): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(
        cursor: CategoryUsageCursor
    )

    @Query("DELETE FROM category_usage_cursors WHERE scopeKey = :scopeKey")
    suspend fun resetCursor(
        scopeKey: String
    )
}

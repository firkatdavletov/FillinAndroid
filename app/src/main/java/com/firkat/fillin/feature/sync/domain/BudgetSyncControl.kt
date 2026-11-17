package com.firkat.fillin.feature.sync.domain

import kotlinx.coroutines.flow.Flow

data class BudgetSyncIssue(
    val requestId: String,
    val title: String,
    val local: String,
    val server: String,
    val status: String,
    val canReapply: Boolean
)

data class BudgetSyncContext(
    val ready: Boolean = false,
    val canWrite: Boolean = false,
    val syncing: Boolean = false,
    val pending: Int = 0,
    val error: String? = null,
    val issues: List<BudgetSyncIssue> = emptyList(),
    val pendingActions: List<String> = emptyList(),
    val cloudEnabled: Boolean = false,
    val lastSyncedAt: String? = null,
    val keepLocalAccess: Boolean = false,
    val importedLocalOnly: Boolean = false
)

interface BudgetSyncControl {
    val context: Flow<BudgetSyncContext>

    fun requestSync()

    /** Explicit user retry; background synchronization must not revive rejected commands. */
    suspend fun retryRejectedCommands() = requestSync()

    suspend fun resolve(
        id: String,
        reapply: Boolean
    )
}

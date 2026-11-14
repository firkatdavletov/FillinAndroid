package com.firkat.fillin.feature.appaccess.domain

import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.flow.StateFlow

enum class AppMode { OfflineFree, InternalFull }

interface AppAccess {
    val mode: StateFlow<AppMode>
    val checking: StateFlow<Boolean>
    fun installationId(): String
    suspend fun refresh(
        manual: Boolean = false
    ): Boolean
    fun revoke()

    val cloudAllowed: Boolean get() = mode.value == AppMode.InternalFull

    fun requireCloud() {
        if (!cloudAllowed) throw BudgetConnectionFailure("device_access_denied")
    }
}

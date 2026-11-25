package com.firkat.fillin.feature.appaccess.domain

import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppMode { OfflineFree, InternalFull }

private val initializedAccess = MutableStateFlow(true).asStateFlow()

interface AppAccess {
    val mode: StateFlow<AppMode>
    val initialized: StateFlow<Boolean> get() = initializedAccess
    val cloudAllowed: Boolean get() = mode.value == AppMode.InternalFull

    fun requireCloud() {
        if (!cloudAllowed) throw BudgetConnectionFailure("device_access_denied")
    }
}

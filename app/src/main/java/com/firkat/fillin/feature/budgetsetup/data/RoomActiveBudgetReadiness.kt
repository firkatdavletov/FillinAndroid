package com.firkat.fillin.feature.budgetsetup.data

import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

class RoomActiveBudgetReadiness(
    private val manager: ActiveBudgetManager,
) : ActiveBudgetReadiness {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<Boolean> = flow {
        // The account observer and navigation may run in either order after sign-in.
        manager.restore()
        check(!manager.failed.value) { "Could not restore the active budget" }
        emitAll(manager.active.transformLatest { runtime ->
            if (runtime == null || !manager.isCurrent(runtime.profile)) return@transformLatest
            val lease = runtime.retain() ?: return@transformLatest
            try {
                emitAll(lease.scope.get<BudgetSetupRepository>().ready)
            } finally {
                lease.close()
            }
        })
    }
}

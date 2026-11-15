package com.firkat.fillin.feature.launch.data

import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomLocalBudgetRestorer(
    private val registry: BudgetProfileRegistry,
    private val databases: BudgetDatabaseFactory,
    private val manager: ActiveBudgetManager
) : LocalBudgetRestorer {
    override suspend fun restoreCreatedBudget(): Boolean = withContext(Dispatchers.IO) {
        if (registry is com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry &&
            registry.requiresSelection(null)
        ) {
            return@withContext false
        }
        val selected = registry.selected(null)
        val candidates = (listOf(selected) + registry.available(null)).distinctBy { it.id }
        for (profile in candidates) {
            if (profile.kind == BudgetProfileKind.CLOUD) continue
            val database = databases.open(profile)
            val ready = try {
                database.operations().state()?.let { it.localReady || it.initialSyncComplete } == true
            } finally {
                database.close()
            }
            if (ready) {
                registry.select(profile.id, null)
                manager.restore()
                return@withContext true
            }
        }
        false
    }
}

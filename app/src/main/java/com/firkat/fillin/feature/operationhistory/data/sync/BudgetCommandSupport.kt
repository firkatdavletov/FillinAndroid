package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.sync.data.BudgetCapabilities
import com.firkat.fillin.feature.sync.data.BudgetProjection
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand

/** Capability checks share one scope-validated budget response per synchronization. */
internal class BudgetCommandSupport(
    private val readCapabilities: suspend () -> Set<String>,
) {
    private var capabilities: Set<String>? = null

    fun reset() {
        capabilities = null
    }

    suspend fun ensure(command: BudgetSyncCommand) {
        val linking = command.planMatchingMode == "CONFIRM_REQUIRED" ||
            command.type in BudgetProjection.LINK_COMMANDS || command.planTarget != null
        val incomeEditing = command.type in INCOME_RULE_COMMANDS ||
            command.incomeName != null || command.incomeFrequency != null || command.incomeActive != null
        val remainder = command.type == "CONFIRM_REMAINDER_INCOME" ||
            command.remainderAmount != null || command.remainderDueOn != null || command.remainderReceived != null
        if (!linking && !incomeEditing && !remainder) return

        val supported = capabilities ?: readCapabilities().also { capabilities = it }
        val dailyPlanning = BudgetCapabilities.DAILY_LIMIT_PLANNING in supported
        if (linking && BudgetCapabilities.CONFIRMED_PLAN_LINKING !in supported ||
            incomeEditing && BudgetCapabilities.INCOME_RULE_EDITING !in supported && !dailyPlanning ||
            remainder && !dailyPlanning
        ) {
            throw SyncBlockedException("backend_update_required")
        }
    }

    private companion object {
        val INCOME_RULE_COMMANDS = setOf("CREATE_INCOME_RULE", "UPDATE_INCOME_RULE", "DELETE_INCOME_RULE")
    }
}

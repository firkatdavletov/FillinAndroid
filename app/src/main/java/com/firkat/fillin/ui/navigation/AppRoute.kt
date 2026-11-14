package com.firkat.fillin.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Launch : AppRoute

    @Serializable data class DailyLimitDetails(val budgetScopeId: String) : AppRoute

    @Serializable data class ForecastDetails(val budgetScopeId: String) : AppRoute

    @Serializable data class PlanningRuleEditor(
        val budgetScopeId: String,
        val type: PlannedOperationType = PlannedOperationType.Expense,
        val ruleId: String? = null,
        val requestId: String = UUID.randomUUID().toString()
    ) : AppRoute

    @Serializable data object Main : AppRoute

    @Serializable data object OperationCreate : AppRoute

    @Serializable data object Auth : AppRoute

    @Serializable data object AccountMethods : AppRoute

    @Serializable data object Categories : AppRoute

    @Serializable data class EditCategory(val budgetScopeId: String, val categoryId: String?) : AppRoute

    @Serializable data object FamilyAccess : AppRoute

    @Serializable data object CloudSync : AppRoute

    @Serializable data class BudgetSetup(val mode: BudgetSetupMode) : AppRoute

    @Serializable data object BudgetSetupList : AppRoute

    @Serializable data object BudgetSetupJoin : AppRoute

    @Serializable data class OperationDetails(
        val operationId: String,
        val resultKey: String,
        val budgetScopeId: String,
        val requestId: String = UUID.randomUUID().toString()
    ) : AppRoute

    @Serializable data class OperationPlanPicker(val operationId: String, val budgetScopeId: String) : AppRoute

    @Serializable data class OperationPlanLinkBottomSheet(
        val operationId: String,
        val budgetScopeId: String,
        val requestId: String = UUID.randomUUID().toString()
    ) : AppRoute

    @Serializable data class PlannedOperationBottomSheet(
        val type: PlannedOperationType,
        val budgetScopeId: String,
        val requestId: String = UUID.randomUUID().toString()
    ) : AppRoute

    @Serializable data object Profile : AppRoute
}

internal fun AppRoute.isBudgetSetup() =
    this is AppRoute.BudgetSetup || this == AppRoute.BudgetSetupList || this == AppRoute.BudgetSetupJoin

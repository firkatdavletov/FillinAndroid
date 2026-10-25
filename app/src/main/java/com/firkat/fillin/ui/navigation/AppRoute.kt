package com.firkat.fillin.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.firkat.fillin.feature.bottomsheets.presentation.TestBottomSheetAction
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Launch : AppRoute
    @Serializable data object Main : AppRoute
    @Serializable data object OperationCreate : AppRoute
    @Serializable data object Auth : AppRoute
    @Serializable data object FamilyAccess : AppRoute
    @Serializable data object CloudSync : AppRoute
    @Serializable data class BudgetSetup(val mode: BudgetSetupMode) : AppRoute
    @Serializable data class TestBottomSheet(
        val operationId: String,
        val action: TestBottomSheetAction,
        val resultKey: String,
        val budgetScopeId: String,
        val requestId: String = UUID.randomUUID().toString(),
    ) : AppRoute
}

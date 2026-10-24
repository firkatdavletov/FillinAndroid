package com.firkat.fillin.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Launch : AppRoute
    @Serializable data object Main : AppRoute
    @Serializable data object Auth : AppRoute
    @Serializable data object FamilyAccess : AppRoute
    @Serializable data object CloudSync : AppRoute
    @Serializable data class BudgetSetup(val mode: BudgetSetupMode) : AppRoute
}

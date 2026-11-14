package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage

@Composable
fun BudgetSetupRoute(
    viewModel: BudgetSetupViewModel,
    onBack: () -> Unit,
    onSignIn: (SetupAuthTarget) -> Unit,
    onComplete: () -> Unit,
    onPageChange: (SetupPage) -> Unit,
    fromProfile: Boolean = false,
    page: SetupPage? = null,
    isActive: Boolean = true
) {
    Surface(Modifier.fillMaxSize().testTag("budget_setup_screen"), color = MaterialTheme.colorScheme.background) {
        BudgetSetupScreen(viewModel, onBack, onSignIn, onComplete, onPageChange, fromProfile, page, isActive)
    }
}

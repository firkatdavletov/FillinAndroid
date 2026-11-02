package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode

@Composable
fun BudgetSetupRoute(
    mode: BudgetSetupMode,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budget_setup_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        BudgetSetupScreen(mode = mode, onBack = onBack)
    }
}

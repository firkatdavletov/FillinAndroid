package com.firkat.fillin.feature.budgetsetup.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.ui.theme.FillinTheme

@Composable private fun SetupPreview(
    state: BudgetSetupState = BudgetSetupState(),
    fromProfile: Boolean = false
) {
    FillinTheme {
        BudgetSetupContent(
            state.copy(
                initialized = true,
                session = com.firkat.fillin.feature.auth.domain.AuthSession.Anonymous
            ),
            {
            },
            remember { SnackbarHostState() },
            fromProfile = fromProfile
        )
    }
}

@Preview(widthDp = 393, heightDp = 900)
@Preview(widthDp = 393, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LocalPreview() {
    SetupPreview()
}

@Preview(widthDp = 393, heightDp = 900)
@Preview(widthDp = 393, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloudPreview() {
    SetupPreview(BudgetSetupState(mode = BudgetSetupMode.Cloud, loaded = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SavingPreview() {
    SetupPreview(BudgetSetupState(busy = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ErrorPreview() {
    SetupPreview(BudgetSetupState(name = "", balance = "abc", nameInvalid = true, balanceInvalid = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PendingPreview() {
    SetupPreview(
        BudgetSetupState(
            mode = BudgetSetupMode.Cloud,
            pending = PendingBudgetCreation("saved", BudgetCreationDraft("Личный бюджет", "0", "2026-11-02"))
        )
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ListPreview() {
    SetupPreview(
        BudgetSetupState(
            page = SetupPage.List,
            budgets = listOf(AvailableBudget("one", "Семейный бюджет", "member", "OWNER"))
        )
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun JoinPreview() {
    SetupPreview(BudgetSetupState(page = SetupPage.Join, code = "12345", codeInvalid = true))
}

@Preview(widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(widthDp = 840, heightDp = 900)
@Composable
private fun LongPreview() {
    SetupPreview(BudgetSetupState(name = "Бюджет на большую семейную поездку"))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun JoinEmptyPreview() {
    SetupPreview(BudgetSetupState(page = SetupPage.Join))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun JoinBusyPreview() {
    SetupPreview(BudgetSetupState(page = SetupPage.Join, code = "12345-ABCDE-12345-ABCDE", busy = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CatalogLoadingPreview() {
    SetupPreview(BudgetSetupState(page = SetupPage.List, loading = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSetupPreview() {
    SetupPreview(BudgetSetupState(cloudAllowed = false))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSetupLimitPreview() {
    SetupPreview(BudgetSetupState(cloudAllowed = false, localLimitReached = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSetupFromProfilePreview() {
    SetupPreview(BudgetSetupState(cloudAllowed = false), fromProfile = true)
}

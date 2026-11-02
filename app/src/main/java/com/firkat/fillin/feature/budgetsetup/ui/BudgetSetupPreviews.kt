package com.firkat.fillin.feature.budgetsetup.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.interactor.BudgetConnectionState
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
private fun LocalPreview(busy: Boolean = false, error: String? = null) {
    FillinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box {
                Image(painterResource(R.drawable.saldomi_auth_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                LocalBudgetSetupContent(busy, error, {}, "123,45", {}, "2026-10-21", {}, {})
            }
        }
    }
}
@Preview(name = "Local", heightDp = 800)
@Preview(name = "Local dark", heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LocalIdlePreview() { LocalPreview() }
@Preview(name = "Local saving", heightDp = 800)
@Composable
private fun LocalBusyPreview() { LocalPreview(busy = true) }
@Preview(name = "Local error", heightDp = 800)
@Composable
private fun LocalErrorPreview() { LocalPreview(error = "Проверьте баланс и дату начала учёта.") }
@Preview(name = "Local large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun LocalLargeTextPreview() { LocalPreview() }
@Preview(name = "Local wide", widthDp = 840, heightDp = 800)
@Composable
private fun LocalWidePreview() { LocalPreview() }

private val previewSession = AuthSession.Authenticated(AuthUser("preview", "Анна", null))
private val previewDraft = BudgetCreationDraft("Семейный бюджет", "125,50", "2026-10-21")
private val previewBudget = AvailableBudget("family", "Большой семейный бюджет на каждый день", "owner", "OWNER")
@Composable
private fun CloudPreview(state: BudgetConnectionState, joinCode: String = "", joinBusy: Boolean = false, joinError: String? = null, joinedName: String? = null) {
    FillinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CloudBudgetSetupContent(
                state.session, state.budgets, state.busy, state.loaded, state.draft, state.pending,
                state.activeBudgetId, state.showingSaved, state.error, state.joiningFamily,
                state.session, joinCode, joinBusy, joinError, joinedName,
                {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
@Preview(name = "Cloud loading", heightDp = 800)
@Preview(name = "Cloud loading dark", heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloudLoadingPreview() { CloudPreview(BudgetConnectionState()) }
@Preview(name = "Cloud draft", heightDp = 800)
@Preview(name = "Cloud draft dark", heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloudDraftPreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, draft = previewDraft.copy(name = ""))) }
@Preview(name = "Cloud selected budget", heightDp = 800)
@Composable
private fun CloudListPreview() { CloudPreview(BudgetConnectionState(session = previewSession, budgets = listOf(previewBudget), loaded = true, activeBudgetId = previewBudget.id)) }
@Preview(name = "Cloud saved list error", heightDp = 800)
@Composable
private fun CloudSavedPreview() { CloudPreview(BudgetConnectionState(session = previewSession, budgets = listOf(previewBudget), loaded = true, showingSaved = true, error = "Проверьте интернет и повторите попытку.")) }
@Preview(name = "Cloud creating", heightDp = 800)
@Composable
private fun CloudCreatingPreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, draft = previewDraft, creating = true)) }
@Preview(name = "Cloud connecting", heightDp = 800)
@Composable
private fun CloudConnectingPreview() { CloudPreview(BudgetConnectionState(session = previewSession, budgets = listOf(previewBudget), loaded = true, connecting = previewBudget.id)) }
@Preview(name = "Cloud pending", heightDp = 800)
@Composable
private fun CloudPendingPreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, draft = previewDraft, pending = PendingBudgetCreation("saved", previewDraft))) }
@Preview(name = "Cloud pending busy", heightDp = 800)
@Composable
private fun CloudPendingBusyPreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, draft = previewDraft, pending = PendingBudgetCreation("saved", previewDraft), creating = true)) }
@Preview(name = "Cloud guest", heightDp = 800)
@Composable
private fun CloudGuestPreview() { CloudPreview(BudgetConnectionState(session = AuthSession.Anonymous)) }
@Preview(name = "Cloud large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun CloudLargePreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, draft = previewDraft)) }
@Preview(name = "Cloud wide", widthDp = 840, heightDp = 800)
@Composable
private fun CloudWidePreview() { CloudPreview(BudgetConnectionState(session = previewSession, budgets = listOf(previewBudget), loaded = true)) }
@Preview(name = "Cloud family join", heightDp = 800)
@Composable
private fun CloudJoinPreview() { CloudPreview(BudgetConnectionState(session = previewSession, loaded = true, joiningFamily = true)) }

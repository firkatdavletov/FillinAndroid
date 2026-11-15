package com.firkat.fillin.feature.budgetconnection

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceViewModel
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel

class BudgetRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun logoutImmediatelyHidesCloudBalanceAndReturningRestoresOnlyItsAccountsQueue() {
        BudgetConnectionDeviceTest.Environment().use { env ->
            val cloud = runBlocking {
                env.manager.restore()
                env.manager.active.value!!.scope.get<CloudBudgetSession>().let {
                    it.createLocal("900.00".toBigDecimal(), it.today)
                }
                env.auth.login("owner")
                env.manager.restore()
                env.connection.connect("owner", env.connection.budgets("owner").single())
                BudgetRecoveryDeviceTest.expense(env, "12.34")
                env.manager.active.value!!.profile
            }
            val queue = runBlocking { env.database(cloud) { it.budget().content()!!.commands } }
            compose.setContent {
                ActiveBudgetContent(env.manager, env.auth) {
                    val model: TotalBalanceViewModel = koinViewModel()
                    val state by model.uiState.collectAsStateWithLifecycle()
                    Text(state.amount?.amount?.toPlainString() ?: "loading")
                }
            }
            waitForAmount("87.66")
            runBlocking { env.auth.signOut() }
            // The auth gate hides the old data before asynchronous profile restoration completes.
            compose.waitUntil(5000) { compose.onAllNodesWithText("87.66").fetchSemanticsNodes().isEmpty() }
            runBlocking { env.manager.restore() }
            waitForAmount("900.00")
            runBlocking {
                env.auth.login("other")
                env.manager.restore()
            }
            compose.onNodeWithText("87.66").assertDoesNotExist()
            assertFalse(env.manager.isCurrent(cloud))
            runBlocking {
                env.auth.login("owner")
                env.manager.restore()
            }
            waitForAmount("87.66")
            assertEquals(queue, runBlocking { env.database(cloud) { it.budget().content()!!.commands } })
            compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
            compose.waitForIdle()
        }
    }

    private fun waitForAmount(
        amount: String
    ) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(amount).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(amount).assertExists()
    }
}

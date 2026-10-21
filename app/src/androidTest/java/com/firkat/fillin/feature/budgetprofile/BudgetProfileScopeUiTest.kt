package com.firkat.fillin.feature.budgetprofile

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupGate
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.ConcurrentHashMap

class BudgetProfileScopeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchingProfilesReplacesViewModelAndDisplayedBalance() {
        BudgetProfilesDeviceTest.Environment().use { env ->
            val first = runBlocking {
                env.manager.restore()
                env.manager.active.value!!.profile.also { env.seed(it, "100.00", "11.25") }
            }
            val second = runBlocking { env.registry.createLocal().also { env.seed(it, "900.00", "22.50") } }
            runBlocking { env.manager.select(first.id) }
            val models = ConcurrentHashMap<String, TotalBalanceViewModel>()
            compose.setContent {
                ActiveBudgetContent(env.manager, env.auth) {
                    BudgetSetupGate {
                        val model: TotalBalanceViewModel = koinViewModel()
                        val state by model.uiState.collectAsStateWithLifecycle()
                        val profile = env.manager.active.value!!.profile.id
                        models[profile] = model
                        Text(state.amount?.amount?.toPlainString() ?: "loading")
                    }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("88.75")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("88.75").assertExists()
            runBlocking { env.manager.select(second.id) }
            compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("877.50")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("877.50").assertExists()
            assertNotSame(models[first.id], models[second.id])
            runBlocking { env.manager.select(first.id) }
            compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("88.75")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("88.75").assertExists()
            // Release Compose's lease before the isolated Koin application is shut down.
            compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
            compose.waitForIdle()
        }
    }
}

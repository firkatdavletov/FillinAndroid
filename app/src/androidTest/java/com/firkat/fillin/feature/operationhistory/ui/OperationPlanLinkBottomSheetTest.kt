package com.firkat.fillin.feature.operationhistory.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavEntry
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.ui.navigation.BottomSheetScene
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class OperationPlanLinkBottomSheetTest {
    private val models = ViewModelStore()

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val plan =
        SuggestedPlan(
            PlanLinkTarget("INCOME", "plan", dueOn = "2026-10-30"),
            "category",
            "Зарплата",
            "100.00",
            "80.00",
            0
        )

    @Test fun savingBlocksBackScrimAndSwipeUntilTheLinkIsDurable() {
        val links = GatedLinks()
        val history = SheetHistory()
        val model = OperationPlanLinkViewModel("operation", history, links)
        models.put("link", model)
        var closes = 0
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            FillinTheme {
                Text("History")
                if (visible) {
                    val entry = remember {
                        NavEntry("link") {
                            OperationPlanLinkBottomSheet("operation", {
                                closes++
                                visible = false
                            }, {}, model)
                        }
                    }
                    val scene =
                        remember {
                            BottomSheetScene(
                                entry,
                                listOf(
                                    NavEntry("history") {
                                        Text("History")
                                    }
                                ),
                                ModalBottomSheetProperties(),
                                {
                                    visible =
                                        false
                                }
                            )
                        }
                    scene.content()
                }
            }
        }
        compose.onNodeWithText("Связать").performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("operation_plan_link_sheet").performTouchInput { swipeDown() }
        compose.onNodeWithTag("operation_plan_link_sheet").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, links.sets)
            assertEquals(0, links.dismisses)
            assertEquals(0, closes)
            links.gate.complete(Unit)
        }
        compose.waitUntil { closes == 1 }
        assertEquals(0, history.creates)
    }

    @Test fun confirmationDisplaysPartialRemainderAndLeavesOperationOnRefusal() {
        var confirms = 0
        var refusals = 0
        compose.setContent {
            FillinTheme { OperationPlanLinkContent(plan, "30.00", false, true, null, { confirms++ }, { refusals++ }) }
        }
        compose.onNodeWithTag("operation_plan_link_sheet").assertExists()
        compose.onNodeWithText("Остаток после привязки: 50.00 ₸").assertExists()
        compose.onNodeWithText("Оставить без связи").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, refusals)
            assertEquals(0, confirms)
        }
    }

    @Test fun refusingReplacementKeepsTheExistingLink() {
        var refusals = 0
        compose.setContent {
            FillinTheme {
                OperationPlanLinkContent(plan, "30.00", false, true, null, {}, { refusals++ }, isReplacing = true)
            }
        }
        compose.onNodeWithText("Оставить без связи").assertDoesNotExist()
        compose.onNodeWithText("Сохранить текущую связь").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refusals) }
    }

    @Test fun savingBlocksBothConfirmationAndRefusal() {
        compose.setContent { FillinTheme { OperationPlanLinkContent(plan, "30.00", true, true, null, {}, {}) } }
        compose.onNodeWithText("Оставить без связи").performScrollTo().assertIsNotEnabled()
    }

    @Test fun stalePlanRequiresNewChoiceAndGenericFailureAllowsRetry() {
        var choices = 0
        compose.setContent {
            FillinTheme {
                OperationPlanLinkContent(plan, "30.00", false, true, "plan_changed", {
                }, {}, onChooseAnother = { choices++ })
            }
        }
        compose.onNodeWithText("Связать").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Выбрать другой план").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, choices) }
    }
    private inner class GatedLinks : OperationPlanLinkStore {
        val gate = CompletableDeferred<Unit>()
        var sets = 0
        var dismisses = 0
        override fun observeLinks(
            operationId: String
        ): Flow<OperationPlanLinks> = MutableStateFlow(OperationPlanLinks(listOf(plan)))
        override val pendingProposal = MutableStateFlow<String?>("operation")
        override suspend fun proposal(
            operationId: String
        ) = plan
        override suspend fun selectProposal(
            operationId: String,
            plan: SuggestedPlan
        ) = Unit
        override suspend fun dismissProposal(
            operationId: String
        ) {
            dismisses++
        }
        override suspend fun setLink(
            operationId: String,
            plan: SuggestedPlan,
            operationVersion: Long
        ) {
            sets++
            gate.await()
        }
        override suspend fun removeLink(
            operationId: String,
            operationVersion: Long
        ) = Unit
    }
    private class SheetHistory : OperationHistoryStore {
        var creates = 0
        override val context = MutableStateFlow(OfflineHistoryContext(initialSyncComplete = true))
        override fun observeOperation(
            id: String
        ): Flow<Operation?> = MutableStateFlow(
            Operation(
                id, "Зарплата", null,
                BigDecimal(
                    "30.00"
                ),
                "KZT",
                BigDecimal(
                    "30.00"
                ),
                "KZT", LocalDate.parse("2026-10-30"), OperationStatus.Posted, 0, OperationType.Income, "category"
            )
        )
        override fun observeHistory(
            filter: OperationHistoryFilter,
            limit: Int
        ): Flow<OperationPage> = MutableStateFlow(OperationPage(emptyList(), null))
        override fun requestSync() = Unit
        override suspend fun create(
            operation: NewOperation,
            replacesRejectedId: String?
        ): CreateOperationResult {
            creates++
            return CreateOperationResult("unexpected")
        }
    }
}

package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOffer
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOfferStore
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanPaymentRemainder
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanPaymentRemainderStore
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.ui.bottomsheets.OperationPlanLinkBottomSheet
import com.firkat.fillin.feature.operationhistory.ui.bottomsheets.PlanPaymentRemainderBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OperationPlanLinkBottomSheetTest {
    @get:Rule val compose = createComposeRule()

    private val plan = SuggestedPlan(
        PlanLinkTarget("EXPENSE", ruleId = "internet", dueOn = "2026-11-16"),
        "communication",
        "Связь",
        "5000",
        "5000",
        0,
        name = "Интернет",
        currency = "KZT"
    )
    private val operation = PlanLinkOperation(
        0, "EXPENSE", "communication", "Связь", "COMMUNICATION", "KZT", "2026-11-15", "4850", "4850"
    )

    @Test fun exactReviewShowsSavedRecordAndExplainsRemainder() {
        val store = Offers()
        var result: String? = null
        setContent(store) { result = it }
        compose.onNodeWithText("Расход записан").assertExists()
        compose.onNodeWithText("Останется оплатить", substring = true).assertExists()
        compose.onNodeWithText("Связать с планом").performClick()
        compose.onNodeWithText("Оставить без связи").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, store.confirms)
            store.gate.complete(Unit)
        }
        compose.runOnIdle { assertEquals("Интернет", result) }
    }

    @Test fun ambiguousOfferStartsUnselectedAndManualChoiceStaysInSheet() {
        val store = Offers(PlanSuggestion.Choice(listOf(plan), true))
        setContent(store)
        compose.onNodeWithText("Продолжить").assertIsNotEnabled()
        compose.onNodeWithText("Интернет").performClick()
        compose.onNodeWithText("Продолжить").assertIsEnabled().performClick()
        compose.onNodeWithText("Связать с планом").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, store.confirms) }
        compose.onNodeWithTag("operation_plan_link_sheet").assertExists()
        compose.onNodeWithTag("operation_plan_link_body").performScrollToNode(hasText("Выбрать другой план"))
        compose.onNodeWithText("Выбрать другой план").performClick()
        compose.onNodeWithText("Выберите другой план").assertExists()
        compose.onNodeWithText("Интернет").performClick()
        compose.onNodeWithText("Интернет").performClick()
        compose.onNodeWithText("Продолжить").performClick()
        compose.onNodeWithText("Связать с планом").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, store.confirms) }
    }

    @Test fun changedPaymentDisablesConfirmationAndAllowsSkip() {
        val store = Offers()
        setContent(store)
        compose.runOnIdle {
            store.context.value =
                OperationPlanLinkContext(operation, OperationPlanLinks(listOf(plan.copy(remainingAmount = "100"))))
        }
        compose.onNodeWithText("Данные изменились").assertExists()
        compose.onNodeWithText("Связать с планом").assertIsNotEnabled()
        compose.onNodeWithText("Оставить без связи").performClick()
        compose.runOnIdle {
            assertEquals(0, store.confirms)
            assertEquals(1, store.dismisses)
        }
    }

    @Test fun remainderSheetShowsTheActualPaymentAndClosesOnlyOnConfirmation() {
        val remainder = PlanPaymentRemainder(
            plan.copy(plannedAmount = "320000", remainingAmount = "12000"),
            BigDecimal("308000")
        )
        val store = Remainders(remainder)
        var closed = false
        setRemainderContent(store) { closed = true }
        compose.onNodeWithText("Оплачено", substring = true).assertExists()
        compose.onNodeWithText("Оставить", substring = true).assertIsEnabled()
        compose.runOnIdle { assertTrue(store.actions.isEmpty()) }
        compose.onNodeWithText("Закрыть платёж").performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), store.actions)
            assertTrue(closed)
        }
    }

    @Test fun keepingIncomeShowsForecastDateAndUsesTheSeparateAction() {
        val remainder = PlanPaymentRemainder(
            plan.copy(target = PlanLinkTarget("INCOME", "income", dueOn = "2026-11-16"), remainingAmount = "150"),
            BigDecimal("4850"),
            LocalDate.parse("2026-11-18")
        )
        val store = Remainders(remainder)
        setRemainderContent(store) {}
        compose.onNodeWithText("Ожидаемая дата в прогнозе", substring = true).assertExists()
        compose.onNodeWithText("Ожидаю ещё", substring = true).performClick()
        compose.runOnIdle { assertEquals(listOf(false), store.actions) }
    }

    private fun setRemainderContent(store: Remainders, onClose: () -> Unit) {
        val key = store.remainder.plan.target.stableKey
        val model = PlanPaymentRemainderViewModel("operation", key, store)
        compose.setContent {
            FillinTheme {
                PlanPaymentRemainderBottomSheet("operation", key, onClose, viewModel = model)
            }
        }
    }

    private class Remainders(val remainder: PlanPaymentRemainder) : PlanPaymentRemainderStore {
        val actions = mutableListOf<Boolean>()
        override fun observeLinks(operationId: String) = MutableStateFlow(OperationPlanLinks(remainder = remainder))
        override suspend fun decide(operationId: String, shown: PlanPaymentRemainder, close: Boolean) {
            assertEquals(remainder, shown)
            actions.add(close)
        }
    }

    private fun setContent(
        store: Offers,
        onClose: (String?) -> Unit = {}
    ) {
        val model = OperationPlanLinkViewModel("operation", store)
        compose.setContent { Content(model, onClose) }
    }

    @Composable
    private fun Content(
        model: OperationPlanLinkViewModel,
        onClose: (String?) -> Unit
    ) {
        FillinTheme { OperationPlanLinkBottomSheet("operation", onClose, viewModel = model) }
    }

    private inner class Offers(private val suggestion: PlanSuggestion = PlanSuggestion.Recommended(plan)) :
        OperationPlanOfferStore {
        val context = MutableStateFlow(OperationPlanLinkContext(operation, OperationPlanLinks(listOf(plan))))
        val gate = CompletableDeferred<Unit>()
        var confirms = 0
        var dismisses = 0
        override suspend fun prepareOffer(
            operationId: String
        ) = true
        override suspend fun proposal(
            operationId: String
        ) = OperationPlanOffer(operation, suggestion)
        override fun observeOfferContext(
            operationId: String
        ) = context
        override suspend fun confirmOffer(
            operationId: String,
            operation: PlanLinkOperation,
            plan: SuggestedPlan
        ) {
            confirms++
            gate.await()
        }
        override suspend fun dismissProposal(
            operationId: String
        ) {
            dismisses++
        }
    }
}

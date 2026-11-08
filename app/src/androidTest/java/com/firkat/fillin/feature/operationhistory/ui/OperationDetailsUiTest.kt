package com.firkat.fillin.feature.operationhistory.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.operationhistory.domain.usecase.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class OperationDetailsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private val store = EditorStore()
    private var closes = 0
    private val results = mutableListOf<EditOperationResult>()

    @After fun cleanup() { compose.runOnIdle { models.clear() } }

    @Test fun savingBlocksBackAndReturnsSuccess() {
        show()
        compose.onNodeWithTag("operation_details_date").assertHasNoClickAction()
        compose.onNodeWithTag("edit_operation_input").performScrollTo().performTextReplacement("55,25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit_operation_save").performClick()
        compose.onNodeWithTag("edit_operation_close").assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertEquals(0, closes); assertEquals(1, store.attempts); store.gate.complete(Unit) }
        compose.waitUntil { results.size == 1 }
        assertEquals(BigDecimal("55.25"), store.operation.value!!.originalAmount)
    }

    @Test fun categoryPickerFiltersAndClosesAfterSelection() {
        show()
        compose.onNodeWithTag("operation_details_category").performScrollTo().performClick()
        compose.onNodeWithText("Дом").performClick()
        compose.onNodeWithTag("operation_details_category").assertTextContains("Дом")
        compose.onNodeWithText("Зарплата").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_save").performClick()
        compose.runOnIdle { store.gate.complete(Unit) }
        compose.waitUntil { results.size == 1 }
        assertEquals("home", store.operation.value!!.categoryId)
    }

    @Test fun dirtyBackRequiresConfirmationAndKeepsDraftWhenCancelled() {
        show()
        compose.onNodeWithTag("edit_operation_input").performScrollTo().performTextReplacement("55")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.onNodeWithText(compose.activity.getString(com.firkat.fillin.R.string.rule_discard_question)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.firkat.fillin.R.string.rule_cancel)).performClick()
        compose.onNodeWithTag("edit_operation_input").assertTextEquals("55")
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.onNodeWithText(compose.activity.getString(com.firkat.fillin.R.string.rule_confirm)).performClick()
        compose.runOnIdle { assertEquals(1, closes); assertTrue(results.isEmpty()) }
    }

    @Test fun unsupportedIncomeHasNoSaveOrRefund() {
        store.operation.value = store.operation.value!!.copy(type = OperationType.Income, originalCurrency = "RUB")
        show()
        compose.onNodeWithTag("edit_operation_save").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_refund").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    private fun show() {
        val model = EditOperationViewModel("operation", store, UpdateOperation(store), RefundPurchase(store))
        models.put("editor", model)
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            FillinTheme {
                if (visible) {
                    OperationDetailsRoute("operation", { closes++; visible = false }, { results += it; visible = false }, {}, { _, _ -> }, model)
                }
            }
        }
        compose.waitUntil { compose.onAllNodesWithTag("edit_operation_loading").fetchSemanticsNodes().isEmpty() }
    }

    private class EditorStore : OperationHistoryStore, OperationHistoryRepository {
        val operation = MutableStateFlow<Operation?>(Operation("operation", "Продукты", "Супермаркет", BigDecimal.TEN, "KZT", BigDecimal.TEN, "KZT", LocalDate.of(2026, 10, 24), OperationStatus.Posted, 0, categoryId = "food"))
        override val context = MutableStateFlow(OfflineHistoryContext(initialSyncComplete = true, categories = listOf(
            Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "")),
            Category("home", "Дом", CategoryKind.Expense, CategoryIcon("HOME", "")),
            Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("SALARY", "")),
        )))
        val gate = CompletableDeferred<Unit>()
        var failure: Exception? = null
        var attempts = 0
        override fun observeOperation(id: String) = operation
        override fun observeHistory(filter: OperationHistoryFilter, limit: Int) = operation.map { OperationPage(listOfNotNull(it), null) }
        override fun requestSync() = Unit
        override suspend fun create(operation: NewOperation, replacesRejectedId: String?) = com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("created")
        override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage = error("Unexpected network read")
        override suspend fun updateOperation(id: String, version: Long, update: OperationUpdate): Operation {
            attempts++
            gate.await()
            failure?.let { throw it }
            return operation.value!!.copy(originalAmount = update.amount, amount = update.amount, categoryId = update.categoryId, description = update.description, version = version + 1).also { operation.value = it }
        }
        override suspend fun updateOperationAmount(id: String, version: Long, amount: BigDecimal): Operation = error("Unexpected amount-only edit")
        override suspend fun refundPurchase(id: String, version: Long) = Unit
    }
}

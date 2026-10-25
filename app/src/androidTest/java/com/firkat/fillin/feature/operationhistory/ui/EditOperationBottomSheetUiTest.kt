package com.firkat.fillin.feature.operationhistory.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavEntry
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.operationhistory.domain.usecase.*
import com.firkat.fillin.ui.navigation.BottomSheetScene
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
class EditOperationBottomSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private val store = EditorStore()
    private var closes = 0
    private val results = mutableListOf<EditOperationResult>()

    @After fun cleanup() { compose.runOnIdle { models.clear() } }

    @Test fun savingBlocksControlsBackScrimAndSwipeThenReturnsSuccess() {
        show()
        compose.onNodeWithTag("edit_operation_input").performTextReplacement("55,25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit_operation_save").performScrollTo().performClick()
        compose.onNodeWithTag("edit_operation_input").assertIsNotEnabled()
        compose.onNodeWithTag("edit_operation_save").assertIsNotEnabled()
        compose.onNodeWithTag("edit_operation_close").assertIsNotEnabled()
        compose.onNodeWithTag("edit_operation_refund").assertIsNotEnabled()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("edit_operation_bottom_sheet").performTouchInput { swipeDown() }
        compose.onNodeWithTag("edit_operation_bottom_sheet").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, closes)
            assertTrue(results.isEmpty())
            assertEquals(1, store.attempts)
            store.gate.complete(Unit)
        }
        compose.waitUntil { results.size == 1 }
        assertEquals(EditOperationResult("operation", EditOperationOutcome.Updated), results.single())
        assertEquals(BigDecimal("55.25"), store.operation.value!!.originalAmount)
    }

    @Test fun failureKeepsInputAndReenablesControlsWithoutReturningSuccess() {
        store.operation.value = store.operation.value!!.copy(type = OperationType.Income)
        store.failure = OperationHistoryException.Network
        show()
        compose.onNodeWithTag("edit_operation_input").performTextReplacement("55.25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit_operation_save").performScrollTo().performClick()
        compose.runOnIdle { store.gate.complete(Unit) }
        compose.onNodeWithTag("edit_operation_error").assertIsDisplayed()
        compose.onNodeWithTag("edit_operation_input").assertIsEnabled().assertTextEquals("55.25")
        compose.onNodeWithTag("edit_operation_close").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, closes); assertTrue(results.isEmpty()) }
    }

    @Test fun unsupportedOperationShowsDetailsAndOnlyClosing() {
        store.operation.value = store.operation.value!!.copy(type = OperationType.Income, originalCurrency = "RUB")
        show()
        compose.onNodeWithTag("edit_operation_input").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_save").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_refund").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.runOnIdle { assertEquals(1, closes); assertTrue(results.isEmpty()) }
    }

    @Test fun incomeHasEditableAmountWithoutRefundAndBlocksDismissalUntilSaved() {
        val income = store.operation.value!!.copy(type = OperationType.Income, categoryName = "Зарплата", description = "Аванс")
        store.operation.value = income
        show()
        compose.onNodeWithTag("edit_operation_input").assertTextEquals(income.originalAmount.toPlainString())
        compose.onNodeWithTag("edit_operation_save").assertIsNotEnabled()
        compose.onNodeWithTag("edit_operation_refund").assertDoesNotExist()
        compose.onNodeWithTag("edit_operation_input").performTextReplacement("150,25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit_operation_save").performScrollTo().performClick()
        compose.onNodeWithTag("edit_operation_input").assertIsNotEnabled()
        compose.onNodeWithTag("edit_operation_close").assertIsNotEnabled()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("edit_operation_bottom_sheet").performTouchInput { swipeDown() }
        compose.onNodeWithTag("edit_operation_bottom_sheet").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, closes)
            assertTrue(results.isEmpty())
            store.gate.complete(Unit)
        }
        compose.waitUntil { results.size == 1 }
        assertEquals(EditOperationResult("operation", EditOperationOutcome.Updated), results.single())
        assertEquals(income.copy(amount = BigDecimal("150.25"), originalAmount = BigDecimal("150.25"), version = 1), store.operation.value)
    }

    private fun show() {
        val model = EditOperationViewModel("operation", store, UpdateOperationAmount(store), RefundPurchase(store))
        models.put("editor", model)
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            FillinTheme {
                Text("History")
                if (visible) {
                    val entry = remember {
                        NavEntry("editor") {
                            EditOperationBottomSheet("operation", { closes++; visible = false }, { results += it; visible = false }, model)
                        }
                    }
                    val scene = remember { BottomSheetScene(entry, listOf(NavEntry("history") {}), ModalBottomSheetProperties(), { closes++; visible = false }) }
                    scene.content()
                }
            }
        }
        compose.waitUntil { compose.onAllNodesWithTag("edit_operation_loading").fetchSemanticsNodes().isEmpty() }
    }

    private class EditorStore : OperationHistoryStore, OperationHistoryRepository {
        val operation = MutableStateFlow<Operation?>(Operation("operation", "Продукты", "Супермаркет", BigDecimal.TEN, "KZT", BigDecimal.TEN, "KZT", LocalDate.of(2026, 10, 24), OperationStatus.Posted, 0))
        override val context = MutableStateFlow(OfflineHistoryContext(initialSyncComplete = true))
        val gate = CompletableDeferred<Unit>()
        var failure: Exception? = null
        var attempts = 0
        override fun observeOperation(id: String) = operation
        override fun observeHistory(filter: OperationHistoryFilter, limit: Int) = operation.map { OperationPage(listOfNotNull(it), null) }
        override fun requestSync() = Unit
        override suspend fun create(operation: NewOperation, replacesRejectedId: String?) = Unit
        override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage = error("Unexpected network read")
        override suspend fun updateOperationAmount(id: String, version: Long, amount: BigDecimal): Operation {
            attempts++
            gate.await()
            failure?.let { throw it }
            return operation.value!!.copy(originalAmount = amount, amount = amount, version = version + 1).also { operation.value = it }
        }
        override suspend fun refundPurchase(id: String, version: Long) = Unit
    }
}

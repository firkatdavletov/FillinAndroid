package com.firkat.fillin.feature.bottomsheets.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TestBottomSheetUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun correctionUsesTheRouteEventFlow() {
        var actions = 0
        var closes = 0
        val model = TestBottomSheetViewModel("rejected-operation", TestBottomSheetAction.Correct)
        compose.setContent {
            FillinTheme {
                Surface {
                    TestBottomSheetRoute(
                        operationId = "rejected-operation", action = TestBottomSheetAction.Correct,
                        onClose = { closes++ }, onOperationAction = { actions++ }, viewModel = model,
                    )
                }
            }
        }
        compose.onNodeWithTag("test_bottom_sheet_action").assertTextEquals("Исправить").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(0, closes) }
    }

    @Test fun readOnlyOperationOffersOnlyClosing() {
        var closes = 0
        compose.setContent {
            FillinTheme {
                Surface {
                    TestBottomSheetContent(
                        operationId = "read-only", action = TestBottomSheetAction.None,
                        onCloseClick = { closes++ }, onActionClick = { error("Unavailable action") },
                    )
                }
            }
        }
        compose.onNodeWithTag("test_bottom_sheet_operation_id").assertTextEquals("ID операции: read-only")
        compose.onNodeWithTag("test_bottom_sheet_action").assertDoesNotExist()
        compose.onNodeWithTag("test_bottom_sheet_close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }
}

package com.firkat.fillin.feature.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.profile.ui.ProfileBudgetCreationBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileBudgetCreationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var submitted: BudgetCreationDraft? = null
    private var cloudSubmitted = false

    private fun show(signedIn: Boolean) {
        compose.setContent {
            var draft by remember { mutableStateOf(BudgetCreationDraft("Личный бюджет", "0", "2026-10-01")) }
            var mode by remember { mutableStateOf(BudgetSetupMode.Local) }
            FillinTheme(dynamicColor = false) {
                ProfileBudgetCreationBottomSheet(
                    draft = draft, busy = false, pending = false, error = null,
                    onDraftChange = { draft = it },
                    onCreateCloudClick = { submitted = draft; cloudSubmitted = true },
                    onCreateLocalClick = { submitted = draft },
                    onDismiss = {}, signedIn = signedIn, mode = mode, onModeChange = { mode = it },
                )
            }
        }
    }

    @Test fun guestCanNameBudgetAndReachCreateWithKeyboard() {
        show(signedIn = false)
        compose.onNodeWithTag("setup_cloud").assertDoesNotExist()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Отпуск")
        compose.onNodeWithTag("budget_setup_date").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Отпуск", submitted?.name)
            assertFalse(cloudSubmitted)
        }
        closeSoftKeyboard()
    }

    @Test fun storageSwitchKeepsNameAndUsesSingleCreateButton() {
        show(signedIn = true)
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Семья")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Семья", submitted?.name)
            assertTrue(cloudSubmitted)
        }
    }
}

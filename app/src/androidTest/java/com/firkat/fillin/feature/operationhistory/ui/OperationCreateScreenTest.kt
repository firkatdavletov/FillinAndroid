package com.firkat.fillin.feature.operationhistory.ui

import android.view.WindowManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationCreateScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private val history = RecordingHistory()
    private lateinit var model: OperationCreateViewModel

    @Test fun openingFocusesInputAndPositionsCategoriesAboveVisibleKeyboard() {
        show()
        assertKeyboardVisible()
        compose.onNodeWithTag("operation-create-input").assertIsFocused()
        compose.onNodeWithTag("operation-create-category-expense-0").performScrollTo().assertIsDisplayed()
        screenshot("operation-create-keyboard.png")
    }

    @Test fun switchingTypesFiltersCategoriesAndResetsHorizontalScrollWithoutLosingFocus() {
        val context = history.context.value
        history.context.value = context.copy(categories =
            context.categories.filter { it.kind == CategoryKind.Expense } +
                context.categories.filter { it.kind == CategoryKind.Income }.take(7),
        )
        show()
        assertKeyboardVisible()
        compose.onNodeWithTag("operation-create-category-income-0").assertDoesNotExist()
        compose.onNodeWithTag("operation-create-category-expense-6").assertExists()
        compose.onNodeWithTag("operation-create-category-expense-7").assertDoesNotExist()
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertExists()
        compose.onNodeWithTag("operation-create-type-INCOME").performClick().assertIsSelected()
        compose.onNodeWithTag("operation-create-category-income-0").assertIsDisplayed()
        compose.onNodeWithTag("operation-create-category-income-6").assertExists()
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertDoesNotExist()
        compose.onNodeWithTag("operation-create-category-expense-9").assertDoesNotExist()
        compose.onNodeWithTag("operation-create-type-EXPENSE").performClick()
        compose.onNodeWithTag("operation-create-category-expense-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("operation-create-input").assertIsFocused()
        assertKeyboardVisible()
    }

    @Test fun invalidClickShowsErrorSnackbarAboveCategoriesAndCanBeRetried() {
        show()
        compose.onNodeWithTag("operation-create-category-expense-0").performClick()
        val message = compose.activity.getString(R.string.operation_create_invalid_input)
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag("operation-create-input").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        screenshot("operation-create-error.png")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("operation-create-category-expense-0").performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        assertTrue(history.created.isEmpty())
        compose.onNodeWithTag("operation-create-input").performTextReplacement("1200")
        compose.onNodeWithTag("operation-create-input")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("operation-create-category-expense-0").performClick()
        compose.waitUntil { model.uiState.value.createEditor == null }
        assertNull(history.created.single().description)
        assertEquals("1200.00", history.created.single().amount.toPlainString())
    }

    @Test fun emptyCategoriesShowExplanationAndKeepInputAvailable() {
        history.context.value = history.context.value.copy(categories = emptyList())
        show()
        compose.onNodeWithTag("operation-create-no-categories").assertIsDisplayed()
        compose.onNodeWithTag("operation-create-categories").assertDoesNotExist()
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertDoesNotExist()
        compose.onNodeWithTag("operation-create-input").assertIsFocused()
    }

    @Test fun largeFontKeepsCategoriesVisibleAboveKeyboard() {
        show(fontScale = 2f)
        assertKeyboardVisible()
        compose.onNodeWithTag("operation-create-input").assertIsDisplayed()
        compose.onNodeWithTag("operation-create-category-expense-0").performScrollTo().assertIsDisplayed()
        screenshot("operation-create-large-font.png")
    }

    @Test fun dismissedKeyboardDoesNotReopenWhenSwitchingType() {
        show()
        assertKeyboardVisible()
        closeSoftKeyboard()
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        screenshot("operation-create.png")
        compose.onNodeWithTag("operation-create-type-INCOME").performClick()
        compose.waitForIdle()
        assertFalse(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
    }

    @Test fun moreAppearsAtExactlyEightCategoriesButNotForASingleCategory() {
        val context = history.context.value
        history.context.value = context.copy(categories =
            context.categories.filter { it.kind == CategoryKind.Expense }.take(8) +
                context.categories.filter { it.kind == CategoryKind.Income }.take(1),
        )
        show()
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertExists()
        compose.onNodeWithTag("operation-create-category-expense-7").assertDoesNotExist()
        compose.onNodeWithTag("operation-create-type-INCOME").performClick()
        compose.onNodeWithTag("operation-create-category-income-0").assertIsDisplayed()
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertDoesNotExist()
    }

    @Test fun moreIsDisabledWhileSaving() {
        compose.setContent {
            FillinTheme {
                OperationCreateScreen(
                    editor = OperationCreateUiState(occurredOn = LocalDate.now(CLOCK), isSaving = true),
                    context = history.context.value,
                    onEvent = {},
                )
            }
        }
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).assertIsNotEnabled()
    }

    @Test fun moreCanBeDismissedOrUsedToCreateOperationWithHiddenCategory() {
        show()
        compose.onNodeWithTag("operation-create-input").performTextReplacement("12,50")
        compose.onNodeWithTag(MORE_CATEGORIES_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Категория 7").assertExists()
        compose.onNodeWithText("Зарплата").assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Категория 7").assertDoesNotExist()
        compose.onNodeWithTag("operation-create-input").assertTextContains("12,50")
        compose.onNodeWithTag("operation-create-input").assertIsFocused()
        assertKeyboardVisible()
        assertTrue(history.created.isEmpty())

        compose.onNodeWithTag(MORE_CATEGORIES_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Категория 9").performScrollTo().performClick()
        compose.waitUntil { model.uiState.value.createEditor == null }
        assertEquals(1, history.created.size)
        assertEquals("expense-9", history.created.single().categoryId)
        assertEquals("12.50", history.created.single().amount.toPlainString())
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun show(fontScale: Float = 1f) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        model = OperationCreateViewModel(history, CLOCK)
        models.put("creator", model)
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FillinTheme { OperationCreateScreen(state.createEditor, state.offline, model::onEvent) }
            }
        }
        compose.waitUntil { model.uiState.value.offline.initialSyncComplete }
    }

    private fun assertKeyboardVisible() {
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
    }

    @After fun cleanup() { compose.runOnIdle { models.clear() } }

    private class RecordingHistory : OperationHistoryStore, OperationHistoryRepository {
        override fun observeOperation(id: String) = flowOf<Operation?>(null)
        override val context = MutableStateFlow(OfflineHistoryContext(
            initialSyncComplete = true,
            accountingStartedOn = LocalDate.of(2026, 1, 1),
            categories = CategoryKind.entries.flatMap { kind -> (0..11).map {
                val expense = kind == CategoryKind.Expense
                val examples = previewOperationCategories().filter { category -> category.kind == kind }
                val example = examples.getOrNull(it)
                Category("${if (expense) "expense" else "income"}-$it", example?.name ?: "Категория $it", kind,
                    example?.icon ?: CategoryIcon(if (expense) "OTHER" else "INCOME", if (expense) "📦" else "💰"))
            } },
        ))
        val created = mutableListOf<NewOperation>()
        override fun observeHistory(filter: OperationHistoryFilter, limit: Int) = flowOf(OperationPage(emptyList(), null))
        override fun requestSync() = Unit
        override suspend fun create(operation: NewOperation, replacesRejectedId: String?): com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult { created += operation; return com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("created") }
        override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage = error("Unused")
        override suspend fun updateOperationAmount(id: String, version: Long, amount: java.math.BigDecimal): Operation = error("Unused")
        override suspend fun refundPurchase(id: String, version: Long) = Unit
    }

    companion object {
        private const val MORE_CATEGORIES_TAG = "operation-create-more-categories"
        private val CLOCK = Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"), ZoneId.of("Asia/Almaty"))
    }
}

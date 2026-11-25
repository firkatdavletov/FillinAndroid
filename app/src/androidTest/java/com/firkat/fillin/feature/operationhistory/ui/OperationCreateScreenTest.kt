package com.firkat.fillin.feature.operationhistory.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoryUsageRepository
import com.firkat.fillin.feature.categories.domain.usecase.GetFrequentlyUsedCategories
import com.firkat.fillin.feature.categories.domain.usecase.GetRecentlyUsedCategories
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    @Test fun switchingTypesFiltersCategoriesAndKeepsFocus() {
        show(withUsage = true)
        assertKeyboardVisible()
        compose.onNodeWithTag("operation-create-frequent-expense-0").assertExists()
        compose.onNodeWithTag("operation-create-frequent-income-0").assertDoesNotExist()
        compose.onNodeWithTag("operation-create-type-INCOME").performClick().assertIsSelected()
        compose.onNodeWithTag("operation-create-frequent-income-0").assertExists()
        compose.onNodeWithTag("operation-create-frequent-expense-0").assertDoesNotExist()
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
        compose.onNodeWithText("Все").assertExists()
        compose.onNodeWithText("Часто используемые").assertDoesNotExist()
        compose.onNodeWithText("Последние").assertDoesNotExist()
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
            ViewCompat.getRootWindowInsets(
                compose.activity.window.decorView
            )?.isVisible(WindowInsetsCompat.Type.ime()) ==
                false
        }
        screenshot("operation-create.png")
        compose.onNodeWithTag("operation-create-type-INCOME").performClick()
        compose.waitForIdle()
        assertFalse(
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime())
        )
    }

    @Test fun usedSectionsAppearInOrderAndAllContainsTheLastCategory() {
        show(withUsage = true)
        closeSoftKeyboard()
        val list = compose.onNodeWithTag("operation-create-category-list")
        list.performScrollToKey("section-frequent")
        val frequent = compose.onNodeWithText("Часто используемые").getBoundsInRoot()
        val recent = compose.onNodeWithText("Последние").getBoundsInRoot()
        val all = compose.onNodeWithText("Все").getBoundsInRoot()
        assertTrue(frequent.top < recent.top && recent.top < all.top)
        list.performScrollToKey("all-expense-11")
        compose.onNodeWithTag("operation-create-category-expense-11").assertIsDisplayed()
    }

    @Test fun categoriesAreDisabledWhileSaving() {
        val context = history.context.value
        val used = context.categories.filter { it.kind == CategoryKind.Expense }.take(1)
        compose.setContent {
            FillinTheme {
                OperationCreateScreen(
                    editor = OperationCreateUiState(
                        occurredOn = LocalDate.now(CLOCK),
                        isSaving = true,
                        frequentlyUsed = used,
                        recentlyUsed = used
                    ),
                    context = context,
                    onEvent = {},
                    onCreateCategory = {}
                )
            }
        }
        compose.onNodeWithTag("operation-create-frequent-expense-0").assertIsNotEnabled()
        compose.onNodeWithTag("operation-create-recent-expense-0").assertIsNotEnabled()
        compose.onNodeWithTag("operation-create-category-expense-0").assertIsNotEnabled()
        compose.onNodeWithTag("operation-create-category-list").performScrollToKey("add-category")
        compose.onNodeWithTag("operation-create-add-category").assertIsNotEnabled()
    }

    @Test fun emptyCategoryListOffersCreationWithoutAnAmount() {
        var requestedKind: CategoryKind? = null
        compose.setContent {
            FillinTheme {
                OperationCreateScreen(
                    editor = OperationCreateUiState(type = OperationType.Income, occurredOn = LocalDate.now(CLOCK)),
                    context = OfflineHistoryContext(categories = emptyList()),
                    onEvent = {},
                    onCreateCategory = { requestedKind = it }
                )
            }
        }
        closeSoftKeyboard()
        compose.onNodeWithTag("operation-create-category-list").performScrollToKey("add-category")
        compose.onNodeWithContentDescription("Создать категорию").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(CategoryKind.Income, requestedKind) }
    }

    @Test fun canSaveFromFullList() = saveFrom("all", "category", "expense-9")

    @Test fun canSaveFromFrequentlyUsed() = saveFrom("frequent", "frequent", "expense-0")

    @Test fun canSaveFromRecentlyUsed() = saveFrom("recent", "recent", "expense-0")

    private fun saveFrom(
        section: String,
        tag: String,
        id: String
    ) {
        show(withUsage = true)
        compose.onNodeWithTag("operation-create-input").performTextReplacement("12,50")
        closeSoftKeyboard()
        compose.onNodeWithTag("operation-create-category-list").performScrollToKey("$section-$id")
        compose.onNodeWithTag("operation-create-$tag-$id").performClick()
        compose.waitUntil { model.uiState.value.createEditor == null }
        assertEquals(id, history.created.single().categoryId)
        assertEquals("12.50", history.created.single().amount.toPlainString())
    }

    private fun screenshot(
        name: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun show(
        fontScale: Float = 1f,
        withUsage: Boolean = false
    ) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val usage = object : CategoryUsageRepository {
            override fun observeFrequentlyUsed(
                limit: Int,
                kind: CategoryKind?
            ) = used(kind, limit)
            override fun observeRecentlyUsed(
                limit: Int,
                kind: CategoryKind?
            ) = used(kind, limit)
            private fun used(
                kind: CategoryKind?,
                limit: Int
            ) = history.context.map { context ->
                if (withUsage) {
                    context.categories.filter { it.kind == kind }.take(minOf(limit, 1))
                } else {
                    emptyList()
                }
            }
        }
        model =
            OperationCreateViewModel(
                history,
                GetFrequentlyUsedCategories(usage),
                GetRecentlyUsedCategories(usage),
                CLOCK
            )
        models.put("creator", model)
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FillinTheme { OperationCreateScreen(state.createEditor, state.offline, model::onEvent) }
            }
        }
        compose.waitUntil {
            val state = model.uiState.value
            state.offline.initialSyncComplete && (!withUsage || state.createEditor?.recentlyUsed?.isNotEmpty() == true)
        }
    }

    private fun assertKeyboardVisible() {
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(
                compose.activity.window.decorView
            )?.isVisible(WindowInsetsCompat.Type.ime()) ==
                true
        }
    }

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
    }

    private class RecordingHistory :
        OperationHistoryStore,
        OperationHistoryRepository {
        override fun observeOperation(
            id: String
        ) = flowOf<Operation?>(null)
        override val context = MutableStateFlow(
            OfflineHistoryContext(
                initialSyncComplete = true,
                accountingStartedOn = LocalDate.of(2026, 1, 1),
                categories = CategoryKind.entries.flatMap { kind ->
                    (0..11).map {
                        val expense = kind == CategoryKind.Expense
                        val examples = previewOperationCategories().filter { category -> category.kind == kind }
                        val example = examples.getOrNull(it)
                        Category(
                            "${if (expense) "expense" else "income"}-$it",
                            example?.name ?: "Категория $it",
                            kind,
                            example?.icon
                                ?: CategoryIcon(if (expense) "OTHER" else "INCOME", if (expense) "📦" else "💰")
                        )
                    }
                }
            )
        )
        val created = mutableListOf<NewOperation>()
        override fun observeHistory(
            filter: OperationHistoryFilter,
            limit: Int
        ) = flowOf(OperationPage(emptyList(), null))
        override fun requestSync() = Unit
        override suspend fun create(
            operation: NewOperation,
            replacesRejectedId: String?
        ): com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult {
            created +=
                operation
            return com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("created")
        }
        override suspend fun getOperations(
            limit: Int,
            cursor: Long?,
            filter: OperationHistoryFilter
        ): OperationPage = error("Unused")
        override suspend fun updateOperationAmount(
            id: String,
            version: Long,
            amount: java.math.BigDecimal
        ): Operation = error("Unused")
        override suspend fun refundPurchase(
            id: String,
            version: Long
        ) = Unit
    }

    companion object {
        private val CLOCK = Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"), ZoneId.of("Asia/Almaty"))
    }
}

package com.firkat.fillin.feature.editcategory.ui

import com.firkat.fillin.feature.categories.domain.model.CATEGORY_INITIAL_ICON_CODE
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import com.firkat.fillin.feature.categories.ui.CategoriesError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditCategoryViewModelTest {
    private val category = Category("id", "Кафе", CategoryKind.Expense, CategoryIcon("FOOD"))

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `valid creation is submitted once and closes after the local write`() = runTest {
        val repository = TestRepository()
        val model = repository.editor()
        runCurrent()
        assertFalse(model.form.canSave)
        assertFalse(model.form.hasChanges)
        model.send(EditCategoryEvent.NameChanged("   "))
        assertFalse(model.form.canSave)
        model.send(EditCategoryEvent.NameChanged("Возврат за покупку"))
        assertFalse(model.form.canSave)
        model.send(EditCategoryEvent.NameChanged("  Зарплата  "))
        model.send(EditCategoryEvent.KindChanged(CategoryKind.Income))
        model.onEvent(EditCategoryEvent.Save, canWrite = false)
        assertFalse(model.form.isBusy)
        model.send(EditCategoryEvent.Save)
        model.send(EditCategoryEvent.Save)
        model.send(EditCategoryEvent.Back)
        model.send(EditCategoryEvent.NameChanged("Изменение во время записи"))
        runCurrent()
        assertTrue(model.form.isBusy)
        assertEquals("  Зарплата  ", model.form.name)
        assertEquals(listOf(Category("new", "Зарплата", CategoryKind.Income, CategoryIcon(CATEGORY_INITIAL_ICON_CODE))), repository.created)
        repository.finishCreate.complete(Unit)
        runCurrent()
        assertEquals(EditCategoryState.Closed, model.state.value)
    }

    @Test fun `reverting an edit disables save and Back confirms a changed draft`() = runTest {
        val model = TestRepository(listOf(category)).editor(category.id)
        runCurrent()
        model.send(EditCategoryEvent.KindChanged(CategoryKind.Income))
        assertEquals(CategoryKind.Expense, model.form.kind)
        model.send(EditCategoryEvent.NameChanged("Рестораны"))
        assertTrue(model.form.canSave)
        model.send(EditCategoryEvent.NameChanged(" Кафе "))
        assertFalse(model.form.canSave)
        model.send(EditCategoryEvent.PickIcon)
        model.send(EditCategoryEvent.IconSelected("GROCERIES"))
        assertTrue(model.form.canSave)
        model.send(EditCategoryEvent.Back)
        assertEquals(CategoryEditorOverlay.DiscardConfirmation, model.form.overlay)
        model.send(EditCategoryEvent.DismissOverlay)
        assertEquals("GROCERIES", model.form.iconCode)
        model.send(EditCategoryEvent.Back)
        model.send(EditCategoryEvent.Discard)
        assertEquals(EditCategoryState.Closed, model.state.value)
    }

    @Test fun `system category keeps its name and allows an icon change`() = runTest {
        val model = TestRepository(listOf(category.copy(dictionaryKey = "OTHER"))).editor(category.id)
        runCurrent()
        model.send(EditCategoryEvent.NameChanged("Рестораны"))
        model.send(EditCategoryEvent.RequestDelete)
        assertEquals(category.name, model.form.name)
        assertFalse(model.form.supportsDelete)
        assertNull(model.form.overlay)
        model.send(EditCategoryEvent.PickIcon)
        model.send(EditCategoryEvent.IconSelected("GROCERIES"))
        assertTrue(model.form.canSave)
    }

    @Test fun `income cannot be deleted and missing category ends loading`() = runTest {
        val repository = TestRepository(listOf(category.copy(kind = CategoryKind.Income)))
        val model = repository.editor(category.id)
        val missing = repository.editor("missing")
        runCurrent()
        model.send(EditCategoryEvent.RequestDelete)
        assertFalse(model.form.supportsDelete)
        assertNull(model.form.overlay)
        assertEquals(EditCategoryState.LoadFailed(CategoriesError.NotFound), missing.state.value)
    }

    @Test fun `failed update keeps the draft and deletion requires confirmation`() = runTest {
        val repository = TestRepository(listOf(category))
        val model = repository.editor(category.id)
        runCurrent()
        model.send(EditCategoryEvent.NameChanged("Рестораны"))
        model.send(EditCategoryEvent.Save)
        runCurrent()
        assertEquals(CategoriesError.Conflict, model.form.error)
        assertEquals("Рестораны", model.form.name)
        assertTrue(model.form.canSave)

        model.send(EditCategoryEvent.ConfirmDelete)
        assertFalse(model.form.isBusy)
        model.send(EditCategoryEvent.RequestDelete)
        model.send(EditCategoryEvent.ConfirmDelete)
        runCurrent()
        assertEquals(listOf(category.id), repository.deleted)
        assertEquals(EditCategoryState.Closed, model.state.value)
    }

    private fun EditCategoryViewModel.send(event: EditCategoryEvent) = onEvent(event, canWrite = true)
    private val EditCategoryViewModel.form get() = (state.value as EditCategoryState.Ready).form

    private class TestRepository(private val categories: List<Category> = emptyList()) : CategoriesRepository {
        val created = mutableListOf<Category>()
        val deleted = mutableListOf<String>()
        val finishCreate = CompletableDeferred<Unit>()

        fun editor(id: String? = null) = EditCategoryViewModel(
            id, GetCategories(this), CreateCategory(this), UpdateCategory(this), DeleteCategory(this),
        )

        override suspend fun getCategories() = categories

        override suspend fun createCategory(name: String, kind: CategoryKind, iconCode: String): Category {
            val category = Category("new", name, kind, CategoryIcon(iconCode))
            created += category
            finishCreate.await()
            return category
        }

        override suspend fun updateCategory(id: String, name: String?, iconCode: String?): Category =
            throw CategoriesException.Conflict

        override suspend fun deleteCategory(id: String) {
            deleted += id
        }
    }
}

package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryCommandsTest {
    @Test
    fun `create trims name and preserves opaque icon code`() = runBlocking {
        val repository = RecordingCategoriesRepository()

        CreateCategory(repository)(
            name = "  Кафе  ",
            kind = CategoryKind.Expense,
            iconCode = " custom.icon-v2 "
        )

        assertEquals("Кафе", repository.createdName)
        assertEquals(CategoryKind.Expense, repository.createdKind)
        assertEquals(" custom.icon-v2 ", repository.createdIcon)
    }

    @Test
    fun `update sends only changed mutable fields`() = runBlocking {
        val repository = RecordingCategoriesRepository()
        val category = category(name = "Кафе", iconCode = "custom.icon-v2")

        UpdateCategory(repository)(
            category = category,
            name = "Рестораны",
            iconCode = "custom.icon-v2"
        )

        assertEquals("Рестораны", repository.updatedName)
        assertNull(repository.updatedIcon)
    }

    @Test
    fun `category with other icon can be changed`() = runBlocking {
        val repository = RecordingCategoriesRepository()
        val other = category(name = "Другое", iconCode = "OTHER")

        UpdateCategory(repository)(
            category = other,
            name = "Прочее",
            iconCode = "food"
        )

        assertEquals("Прочее", repository.updatedName)
        assertEquals("food", repository.updatedIcon)
    }

    @Test
    fun `other is accepted as a regular icon when creating`() = runBlocking {
        val repository = RecordingCategoriesRepository()

        CreateCategory(repository)(
            name = "Прочее",
            kind = CategoryKind.Expense,
            iconCode = "other"
        )

        assertEquals("other", repository.createdIcon)
    }

    @Test
    fun `changing only icon code case is a real change`() = runBlocking {
        val repository = RecordingCategoriesRepository()
        UpdateCategory(repository)(category("Кафе", "custom.icon-v2"), "Кафе", "CUSTOM.ICON-V2")
        assertEquals("CUSTOM.ICON-V2", repository.updatedIcon)
    }

    @Test
    fun `blank icon code cannot be created`() {
        val repository = RecordingCategoriesRepository()
        assertThrows(CategoriesException.InvalidRequest::class.java) {
            runBlocking { CreateCategory(repository)("Кафе", CategoryKind.Expense, "  ") }
        }
        assertNull(repository.createdIcon)
    }

    @Test
    fun `expense category with other icon can be deleted`() = runBlocking {
        val repository = RecordingCategoriesRepository()
        val category = category(name = "Прочее", iconCode = "OTHER")

        DeleteCategory(repository)(category)

        assertEquals(category.id, repository.deletedId)
    }

    @Test
    fun `only expense categories can be deleted`() {
        val repository = RecordingCategoriesRepository()
        val income = category(
            name = "Зарплата",
            iconCode = "SALARY",
            kind = CategoryKind.Income
        )

        val exception = assertThrows(CategoriesException.InvalidRequest::class.java) {
            runBlocking { DeleteCategory(repository)(income) }
        }

        assertSame(CategoriesException.InvalidRequest, exception)
        assertNull(repository.deletedId)
    }

    private fun category(
        name: String,
        iconCode: String,
        kind: CategoryKind = CategoryKind.Expense
    ) = Category(
        id = "category-id",
        name = name,
        kind = kind,
        icon = CategoryIcon(code = iconCode, emoji = "📦")
    )
}

private class RecordingCategoriesRepository : CategoriesRepository {
    var createdName: String? = null
    var createdKind: CategoryKind? = null
    var createdIcon: String? = null
    var updatedName: String? = null
    var updatedIcon: String? = null
    var deletedId: String? = null

    override suspend fun getCategories(): List<Category> = emptyList()

    override suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        iconCode: String
    ): Category {
        createdName = name
        createdKind = kind
        createdIcon = iconCode
        return category(name = name, kind = kind, iconCode = iconCode)
    }

    override suspend fun updateCategory(
        id: String,
        name: String?,
        iconCode: String?
    ): Category {
        updatedName = name
        updatedIcon = iconCode
        return category(
            name = name ?: "Кафе",
            iconCode = iconCode ?: "FOOD"
        )
    }

    override suspend fun deleteCategory(
        id: String
    ) {
        deletedId = id
    }

    private fun category(
        name: String,
        kind: CategoryKind = CategoryKind.Expense,
        iconCode: String
    ) = Category(
        id = "category-id",
        name = name,
        kind = kind,
        icon = CategoryIcon(code = iconCode, emoji = "📦")
    )
}

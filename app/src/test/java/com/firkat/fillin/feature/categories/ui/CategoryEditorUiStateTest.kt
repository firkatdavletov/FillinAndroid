package com.firkat.fillin.feature.categories.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEditorUiStateTest {
    @Test
    fun `unknown icon is retained until explicitly changed`() {
        val category = Category("id", "Кафе", CategoryKind.Expense, CategoryIcon("custom.icon-v2"))
        val state = CategoryEditorUiState(original = category, name = category.name, iconCode = category.icon.code)
        assertFalse(state.hasChanges)
        assertFalse(state.canSave)
        assertTrue(state.copy(name = "Рестораны").canSave)
        assertTrue(state.copy(iconCode = "CUSTOM.ICON-V2").hasChanges)
    }

    @Test
    fun `other is a valid icon for a new category`() {
        val state = CategoryEditorUiState(
            name = "Прочее",
            iconCode = "OTHER",
        )

        assertTrue(state.canSave)
    }

    @Test
    fun `category with other icon can select another icon`() {
        val category = Category(
            id = "category-id",
            name = "Прочее",
            kind = CategoryKind.Expense,
            icon = CategoryIcon(code = "OTHER", emoji = "📦"),
        )
        val state = CategoryEditorUiState(
            original = category,
            name = category.name,
            kind = category.kind,
            iconCode = "FOOD",
        )

        assertTrue(state.canSave)
    }
}

package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.local.CategoryUsageTotal
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryUsageRankingTest {
    private val categories = listOf(
        category("a"),
        category("b"),
        category("c"),
        category("unused"),
        category("income", CategoryKind.Income),
    )
    private val totals = listOf(
        CategoryUsageTotal("b", 5, 100, "z"),
        CategoryUsageTotal("a", 5, 100, "a"),
        CategoryUsageTotal("c", 1, 200, "b"),
        CategoryUsageTotal("income", 8, 150, "y"),
        CategoryUsageTotal("archived", 20, 300, "x"),
    )

    @Test fun frequencyUsesCountThenTimeThenCategoryId() {
        val data = totals + CategoryUsageTotal("d", 5, 110, "c")
        assertEquals(listOf("income", "d", "a", "b"),
            rankUsedCategories(data, categories + category("d"), 4, null, FREQUENT_ORDER).map { it.id })
    }

    @Test fun recentUsesTimeThenEventIdAndFiltersBeforeLimiting() {
        assertEquals(listOf("c", "b", "a"),
            rankUsedCategories(totals, categories, 4, CategoryKind.Expense, RECENT_ORDER).map { it.id })
        assertEquals(listOf("c", "income"),
            rankUsedCategories(totals, categories, 2, null, RECENT_ORDER).map { it.id })
        assertEquals(emptyList<Category>(), rankUsedCategories(totals, categories, 0, null, RECENT_ORDER))
    }

    private fun category(id: String, kind: CategoryKind = CategoryKind.Expense) =
        Category(id, id, kind, CategoryIcon("OTHER"))
}

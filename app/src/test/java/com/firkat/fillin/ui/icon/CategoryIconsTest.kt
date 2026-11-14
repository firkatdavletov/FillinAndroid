package com.firkat.fillin.ui.icon

import com.firkat.fillin.feature.categories.domain.model.CATEGORY_INITIAL_ICON_CODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryIconsTest {
    @Test
    fun `legacy codes resolve to current artwork without rewriting the code`() {
        mapOf(
            "FOOD" to "GROCERIES",
            "TRANSPORT" to "CAR",
            "CLOTHES" to "CLOTHING",
            "EDUCATION" to "BOOKS_EDUCATION",
            "INCOME" to "SALARY"
        ).forEach { (legacy, current) ->
            assertEquals(CategoryIcons.find(current), CategoryIcons.find(legacy))
        }
    }

    @Test
    fun `all twenty three PNG options are reachable by distinct codes`() {
        assertEquals(23, CategoryIcons.options.map { it.drawableResource }.toSet().size)
        assertEquals(23, CategoryIcons.options.map { it.code }.toSet().size)
        CategoryIcons.options.forEach { assertEquals(it, CategoryIcons.find(it.code)) }
    }

    @Test
    fun `unknown retired and automatic codes have no PNG`() {
        listOf(
            "custom.icon-v2", "BICYCLE", "MORTGAGE", "SPORT", "SUBSCRIPTIONS",
            "SERVICE_FEES", "BONUS", "OTHER", "", CATEGORY_INITIAL_ICON_CODE
        ).forEach { assertNull(CategoryIcons.find(it)) }
    }

    @Test
    fun `letter and color are derived from the displayed name`() {
        val placeholder = "Новая категория"
        listOf(
            "" to "Н",
            "   " to "Н",
            "  ёлка  " to "Ё",
            "cafe" to "C",
            "𐐨abc" to "𐐀",
            "🐈 Кот" to "🐈"
        ).forEach { (input, expected) ->
            assertEquals(expected, categoryInitialLetter(categoryInitialName(input, placeholder)))
        }
        val name = categoryInitialName("  Кафе ", placeholder)
        assertEquals(categoryInitialColorIndex("кафе", 8), categoryInitialColorIndex(name, 8))
        assertEquals(Math.floorMod("кафе".hashCode(), 8), categoryInitialColorIndex(name, 8))
    }
}

package com.firkat.fillin.ui.icon

import com.firkat.fillin.R
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
            "INCOME" to "SALARY",
        ).forEach { (legacy, current) ->
            assertEquals(CategoryIcons.find(current), CategoryIcons.find(legacy))
        }
    }

    @Test
    fun `unknown codes use default artwork without selecting household goods`() {
        listOf("custom.icon-v2", "TRAVEL", "OTHER", "").forEach { code ->
            assertEquals(R.drawable.ic_category_household_goods, CategoryIcons.drawableResource(code))
            assertNull(CategoryIcons.find(code))
        }
    }
}

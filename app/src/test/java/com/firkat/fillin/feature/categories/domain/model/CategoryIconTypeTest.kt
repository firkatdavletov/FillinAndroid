package com.firkat.fillin.feature.categories.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryIconTypeTest {
    @Test
    fun `contains all supported icon codes and emoji`() {
        val icons = CategoryIconType.entries.associate { it.name to it.emoji }

        assertEquals(
            linkedMapOf(
                "FOOD" to "🍔",
                "TRANSPORT" to "🚕",
                "HOME" to "🏠",
                "CLOTHES" to "👕",
                "HEALTH" to "💊",
                "ENTERTAINMENT" to "🎬",
                "SPORT" to "⚽",
                "EDUCATION" to "🎓",
                "TRAVEL" to "✈️",
                "PETS" to "🐾",
                "INCOME" to "💰",
                "OTHER" to "📦",
            ),
            icons,
        )
    }

    @Test
    fun `finds icon type by normalized code`() {
        assertEquals(CategoryIconType.OTHER, CategoryIconType.fromCode(" other "))
    }
}

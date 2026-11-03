package com.firkat.fillin.feature.budgetmanagement

import com.firkat.fillin.feature.budgetmanagement.domain.validBudgetName
import org.junit.Assert.*
import org.junit.Test

class BudgetNameTest {
    @Test fun acceptsTrimmedNamesAndRejectsInvalidNames() {
        assertTrue(validBudgetName("  Семейный бюджет  "))
        assertTrue(validBudgetName("А".repeat(80)))
        assertFalse(validBudgetName(" "))
        assertFalse(validBudgetName("А".repeat(81)))
        assertFalse(validBudgetName("Бюджет\u0000"))
    }
}

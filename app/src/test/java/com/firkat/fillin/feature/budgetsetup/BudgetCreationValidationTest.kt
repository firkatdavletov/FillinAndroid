package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.domain.validationErrors
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetCreationValidationTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val draft = BudgetCreationDraft("Личный бюджет", "0", "2026-11-11")

    @Test fun nameBoundariesAndNormalization() {
        listOf("", " ", "a".repeat(81), "a\nb").forEach {
            assertTrue(draft.copy(name = it).validationErrors(today).name)
        }
        listOf("a", "a".repeat(80), "  Поездка  ").forEach {
            assertFalse(draft.copy(name = it).validationErrors(today).name)
        }
        assertEquals("Поездка", draft.copy(name = " Поездка ").validated(today).name)
    }

    @Test fun amountAndDateUseDomainLimits() {
        listOf("1.234", "abc", "100000000000000000.00").forEach {
            assertTrue(draft.copy(openingBalance = it).validationErrors(today).balance)
        }
        assertEquals("-1.50", draft.copy(openingBalance = "-1,50").validated(today).openingBalance)
        listOf("2026-11-12", "2026-02-30", "0000-01-01", "abc").forEach {
            assertTrue(draft.copy(accountingStartedOn = it).validationErrors(today).date)
        }
        assertTrue(draft.validationErrors(today).isEmpty)
    }
}

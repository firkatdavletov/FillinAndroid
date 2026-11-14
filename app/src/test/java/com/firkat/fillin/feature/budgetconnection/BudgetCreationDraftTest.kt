package com.firkat.fillin.feature.budgetconnection

import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetCreationDraftTest {
    private val today = LocalDate.parse("2026-10-21")
    private val draft = BudgetCreationDraft("  Семейный бюджет  ", "-123,45", "2026-10-20")

    @Test fun `input is normalized without rounding and zero and negative opening balance are allowed`() {
        assertEquals(BudgetCreationDraft("Семейный бюджет", "-123.45", "2026-10-20"), draft.validated(today))
        assertEquals("0.00", draft.copy(openingBalance = "0").validated(today).openingBalance)
        assertEquals(
            "99999999999999999.99",
            draft.copy(openingBalance = "99999999999999999.99").validated(today).openingBalance
        )
    }

    @Test fun `invalid money name and dates cannot produce a creation request`() {
        listOf(
            draft.copy(name = " "),
            draft.copy(name = "a".repeat(81)),
            draft.copy(name = "a\nb"),
            draft.copy(openingBalance = "1.001"),
            draft.copy(openingBalance = "100000000000000000"),
            draft.copy(accountingStartedOn = "2026-10-22"),
            draft.copy(accountingStartedOn = "0000-01-01"),
            draft.copy(accountingStartedOn = "2026-02-30")
        ).forEach {
            assertTrue("Accepted invalid draft", runCatching { it.validated(today) }.isFailure)
        }
    }
}

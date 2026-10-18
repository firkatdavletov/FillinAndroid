package com.firkat.fillin.feature.operationhistory.data.local

import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LocalOperationHistoryTest {
    @Test
    fun `totals include refunds by refund date and pending operations but exclude rejected and voided`() {
        val content =
            content(
                listOf(
                    row("expense", "EXPENSE", "10.01"),
                    row("income", "INCOME", "100.02"),
                    row("refund", "EXPENSE_REFUND", "2.03"),
                    row("void", "EXPENSE", "5.00").copy(status = "VOIDED"),
                    row("pending", "EXPENSE", "1.01").copy(syncStatus = "PENDING"),
                    row("rejected", "EXPENSE", "50.00").copy(syncStatus = "REJECTED"),
                    row("old-refund", "EXPENSE_REFUND", "9.00").copy(occurredOn = "2026-09-30"),
                ),
            )
        val page = localHistory(content, FILTER.copy(type = OperationType.Income), 1)
        assertEquals(BigDecimal("8.99"), page.totals.expenses)
        assertEquals(BigDecimal("100.02"), page.totals.incomes)
        assertEquals(listOf("income"), page.items.map { it.id })
        assertNull(page.nextCursor)
        val all = localHistory(content, FILTER, 1)
        assertEquals(page.totals, all.totals)
        assertNotNull(all.nextCursor)
        assertFalse(localHistory(content.copy(sync = content.sync.copy(initialSyncComplete = false)), FILTER, 1).isComplete)
    }

    @Test
    fun `large decimal values are exact and RUB uses saved KZT valuation`() {
        val max = row("max", "EXPENSE", "99999999999999999.99")
        val rub = row("rub", "EXPENSE", "6.01").copy(originalCurrency = "RUB", originalAmount = "1.00")
        val refund = row("refund", "EXPENSE_REFUND", "7.03")
        val page = localHistory(content(listOf(max, rub, refund)), FILTER, 20)
        assertEquals(BigDecimal("99999999999999998.97"), page.totals.expenses)
        assertEquals("RUB", page.items.first { it.id == "rub" }.originalCurrency)
        assertEquals(BigDecimal("-7.03"), localHistory(content(listOf(refund)), FILTER, 20).totals.expenses)
    }

    companion object {
        private val FILTER = OperationHistoryFilter(dateFrom = LocalDate.parse("2026-10-01"), dateTo = LocalDate.parse("2026-10-31"))

        private fun content(rows: List<LocalOperation>) =
            LocalHistoryContent(
                OperationSyncState(scopeKey = "scope", initialSyncComplete = true),
                rows,
                listOf(LocalCategory("scope", "category", "Категория", "EXPENSE", "OTHER", "📦", true, 1)),
            )

        private fun row(
            id: String,
            type: String,
            amount: String,
        ) = LocalOperation(
            id,
            "scope",
            "category",
            type,
            amount,
            "KZT",
            amount,
            "2026-10-18",
            "2026-10-18T00:00:00Z",
            "POSTED",
            0,
            1,
            null,
            null,
            null,
        )
    }
}

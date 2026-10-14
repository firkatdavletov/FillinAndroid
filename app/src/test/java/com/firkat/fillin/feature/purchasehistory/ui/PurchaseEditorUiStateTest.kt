package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEditorUiStateTest {
    @Test
    fun `editor starts with amount in original currency`() {
        val editor = PurchaseEditorUiState(original = purchase())

        assertEquals("30.00", editor.amount)
        assertEquals(BigDecimal("30.00"), editor.parsedAmount)
        assertFalse(editor.canSave)
    }

    @Test
    fun `editor accepts changed amount with comma and two fraction digits`() {
        val editor = PurchaseEditorUiState(
            original = purchase(),
            amount = "12000,50",
        )

        assertEquals(BigDecimal("12000.50"), editor.parsedAmount)
        assertTrue(editor.canSave)
    }

    @Test
    fun `editor disables mutations for returned purchase`() {
        val editor = PurchaseEditorUiState(
            original = purchase(status = PurchaseStatus.Voided),
            amount = "40.00",
        )

        assertFalse(editor.canSave)
        assertFalse(editor.canRefund)
    }

    @Test
    fun `editor rejects invalid amount`() {
        val editor = PurchaseEditorUiState(
            original = purchase(),
            amount = "12.345",
        )

        assertEquals(null, editor.parsedAmount)
        assertFalse(editor.canSave)
    }

    private fun purchase(status: PurchaseStatus = PurchaseStatus.Posted) = Purchase(
        id = "purchase-id",
        categoryName = "Продукты",
        description = null,
        amount = BigDecimal("15000.00"),
        currency = "KZT",
        originalAmount = BigDecimal("30.00"),
        originalCurrency = "USD",
        occurredOn = LocalDate.of(2026, 10, 6),
        status = status,
        version = 0,
    )
}

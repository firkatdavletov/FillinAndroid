package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedIncomeEditorUiStateTest {
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `create requires exact amount and today or future date`() {
        val valid = PlannedIncomeEditorUiState(
            today = today,
            name = "Зарплата",
            categoryId = "category-id",
            amount = "1300000,00",
            dueOn = today.toString(),
        )

        assertTrue(valid.canSave)
        assertFalse(valid.copy(amount = "1300000.001").canSave)
        assertFalse(valid.copy(dueOn = today.minusDays(1).toString()).canSave)
    }

    @Test
    fun `edit permits unchanged overdue date when another field changes`() {
        val original = income(dueOn = today.minusDays(4))
        val editor = PlannedIncomeEditorUiState(
            original = original,
            today = today,
            name = "Зарплата",
            categoryId = original.categoryId,
            amount = "1350000.00",
            dueOn = original.dueOn.toString(),
        )

        assertTrue(editor.canSave)
        assertFalse(editor.copy(dueOn = today.minusDays(1).toString()).canSave)
    }

    @Test
    fun `edit requires at least one effective change`() {
        val original = income(dueOn = today.plusDays(1))

        val editor = PlannedIncomeEditorUiState(
            original = original,
            today = today,
            name = "Зарплата",
            categoryId = original.categoryId,
            amount = original.plannedAmount.toPlainString(),
            dueOn = original.dueOn.toString(),
        )

        assertFalse(editor.canSave)
    }

    private fun income(dueOn: LocalDate) = PlannedIncome(
        id = "income-id",
        categoryId = "category-id",
        categoryName = "Зарплата",
        plannedAmount = BigDecimal("1300000.00"),
        currency = "KZT",
        dueOn = dueOn,
        overdue = dueOn.isBefore(today),
        status = PlannedIncomeStatus.Scheduled,
        version = 2,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
}

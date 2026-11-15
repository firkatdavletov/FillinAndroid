package com.firkat.fillin.feature.operationhistory.domain.usecase

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseOperationInputTest {
    @Test fun `all requested formats preserve the amount and description`() {
        listOf(
            Triple("1200", "1200.00", null),
            Triple("1200.00", "1200.00", null),
            Triple("1200,0", "1200.00", null),
            Triple("продукты 1200", "1200.00", "продукты"),
            Triple("1200 продукты", "1200.00", "продукты"),
            Triple("подставка для ног 5000", "5000.00", "подставка для ног"),
            Triple("1300 нож для резки офощей", "1300.00", "нож для резки офощей")
        ).forEach { (input, amount, description) ->
            assertEquals(input, OperationInputResult.Valid(BigDecimal(amount), description), parseOperationInput(input))
        }
    }

    @Test fun `trim surrounding whitespace and keep description spacing`() {
        assertEquals(
            OperationInputResult.Valid(BigDecimal("12.50"), "кофе  с молоком"),
            parseOperationInput(" \tкофе  с молоком 12,50\n")
        )
        assertEquals(OperationInputResult.Valid(BigDecimal("12.50"), "кофе"), parseOperationInput("кофе\u00a012,50"))
    }

    @Test fun `reject empty missing misplaced and malformed amounts`() {
        listOf("", "  \n", "продукты", "0", "0.00", "-1200", "+1200", "12.345", "12,345", "12.", ".50", "1e3", "12,3.4", "кофе 12.50 сегодня", "1200продукты").forEach {
            assertTrue(it, parseOperationInput(it) is OperationInputResult.Invalid)
        }
    }

    @Test fun `multiple numbers including malformed signed numbers are ambiguous`() {
        listOf("такси 2 поездки 1200", "1200 продукты 300", "12 000", "1200 -5", "1200 12.345").forEach {
            assertEquals(it, OperationInputResult.Invalid(OperationInputError.AmbiguousAmount), parseOperationInput(it))
        }
    }

    @Test fun `amount and description limits are enforced without rounding`() {
        assertEquals(
            OperationInputResult.Valid(BigDecimal("99999999999999999.99"), null),
            parseOperationInput("99999999999999999.99")
        )
        assertTrue(parseOperationInput("100000000000000000") is OperationInputResult.Invalid)
        assertEquals(
            OperationInputResult.Valid(BigDecimal("0.01"), "а".repeat(160)),
            parseOperationInput(
                "а".repeat(160) + " 0.01"
            )
        )
        assertEquals(
            OperationInputResult.Invalid(OperationInputError.DescriptionTooLong),
            parseOperationInput(
                "а".repeat(161) + " 1"
            )
        )
    }
}

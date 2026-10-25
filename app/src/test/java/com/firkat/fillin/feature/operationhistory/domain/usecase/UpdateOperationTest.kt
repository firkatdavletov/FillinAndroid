package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.operationhistory.ui.FakeOfflineHistory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class UpdateOperationTest {
    @Test fun `metadata only edit preserves money and trims description for both operation types`() = runTest {
        for (type in OperationType.entries) {
            val operation = FakeOfflineHistory.purchase("first", type)
            val repository = FakeOfflineHistory().apply { operations.value = listOf(operation) }
            val result = UpdateOperation(repository)(operation, OperationUpdate(operation.originalAmount, operation.categoryId, "  покупка 2 шт  "))
            assertEquals("покупка 2 шт", result.description)
            assertEquals(operation.originalAmount, result.originalAmount)
            assertEquals(operation.type, result.type)
            assertEquals(operation.occurredOn, result.occurredOn)
            assertEquals(1, repository.edits)
        }
    }

    @Test fun `empty description explicitly clears it`() = runTest {
        val operation = FakeOfflineHistory.purchase("first").copy(description = "старое")
        val repository = FakeOfflineHistory().apply { operations.value = listOf(operation) }
        val result = UpdateOperation(repository)(operation, OperationUpdate(operation.originalAmount, operation.categoryId, "  "))
        assertNull(result.description)
    }

    @Test fun `unchanged and invalid drafts never reach persistence`() = runTest {
        val operation = FakeOfflineHistory.purchase("first").copy(description = "старое")
        val repository = FakeOfflineHistory().apply { operations.value = listOf(operation) }
        val valid = OperationUpdate(BigDecimal("20.00"), operation.categoryId, "новое")
        val invalid = listOf(
            valid.copy(amount = BigDecimal("10.0"), description = "  старое  "),
            valid.copy(amount = BigDecimal.ZERO), valid.copy(amount = BigDecimal("1.001")),
            valid.copy(amount = BigDecimal("100000000000000000")), valid.copy(categoryId = " "),
            valid.copy(description = "я".repeat(161)),
        )
        for (update in invalid) {
            try { UpdateOperation(repository)(operation, update); fail("Expected invalid request") }
            catch (failure: OperationHistoryException) { assertEquals(OperationHistoryException.InvalidRequest, failure) }
        }
        assertEquals(0, repository.edits)
    }

    @Test fun `refunds rejected records and historical currencies cannot be edited`() = runTest {
        val original = FakeOfflineHistory.purchase("first")
        val repository = FakeOfflineHistory()
        for (operation in listOf(original.copy(refundedAmount = BigDecimal.ONE), original.copy(syncStatus = OperationSyncStatus.Rejected),
            original.copy(originalCurrency = "RUB"), original.copy(status = OperationStatus.Voided))) {
            try { UpdateOperation(repository)(operation, OperationUpdate(BigDecimal("20.00"), operation.categoryId, "новое")); fail("Expected invalid request") }
            catch (failure: OperationHistoryException) { assertEquals(OperationHistoryException.InvalidRequest, failure) }
        }
        assertEquals(0, repository.edits)
    }
}

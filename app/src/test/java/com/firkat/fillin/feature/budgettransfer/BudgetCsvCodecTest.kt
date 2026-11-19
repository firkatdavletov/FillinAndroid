package com.firkat.fillin.feature.budgettransfer

import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgettransfer.data.BudgetCsvCodec
import com.firkat.fillin.feature.budgettransfer.data.BudgetSnapshot
import com.firkat.fillin.feature.budgettransfer.data.BudgetSnapshotValidator
import com.firkat.fillin.feature.budgettransfer.data.SnapshotMetadata
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.sync.data.SyncBudgetSettings
import java.io.StringWriter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCsvCodecTest {
    private val codec = BudgetCsvCodec(BudgetSnapshotValidator(Json))

    @Test
    fun preservesQuotedTextAndDistinguishesNullFromEmpty() {
        val snapshot = fixture()
        val csv = encode(snapshot)

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("'=SUM(1,2)"))
        assertEquals(snapshot, codec.read(csv.reader()))
        assertEquals(snapshot, codec.read(csv.removePrefix("\uFEFF").reader()))
    }

    @Test
    fun rejectsTruncatedFilesDuplicateFieldsAndBrokenReferences() {
        val csv = encode(fixture())
        assertInvalid(csv.substringBefore("\"end\","))
        assertInvalid(csv.replaceFirst("\"formatVersion\"", "\"currency\""))
        assertInvalid(encode(fixture().copy(categories = emptyList())))
    }

    @Test
    fun reportsUnknownVersion() {
        val csv = encode(fixture().copy(metadata = fixture().metadata.copy(formatVersion = 2)))
        val error = assertThrows(BudgetTransferException::class.java) { codec.read(csv.reader()) }
        assertEquals(TransferFailure.UnsupportedVersion, error.reason)
    }

    private fun assertInvalid(
        csv: String
    ) {
        val error = assertThrows(BudgetTransferException::class.java) { codec.read(csv.reader()) }
        assertEquals(TransferFailure.InvalidFile, error.reason)
        assertTrue(requireNotNull(error.row) > 0)
    }

    private fun encode(
        snapshot: BudgetSnapshot
    ) = StringWriter().apply { codec.write(snapshot, this) }.toString()

    private fun fixture() = BudgetSnapshot(
        SnapshotMetadata(1, "Квартира \"Дом\", 🏠", BudgetCurrency.RUB, "2025-01-01T00:00:00Z", 0, 4),
        listOf(
            operation("abc", "=SUM(1,2)\r\nКофе, \"чай\" ☕"),
            operation("def", ""),
            operation("ghi", null),
            operation("jkl", "'Начало")
        ),
        listOf(LocalCategory("", "food", "Еда", "EXPENSE", "FOOD", "☕", true, 0)),
        listOf(
            LocalSyncRecord(
                "",
                "BUDGET_SETTINGS",
                "budget",
                Json.encodeToString(SyncBudgetSettings("budget", "-5.00", "0.00")),
                0
            )
        ),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
    )

    private fun operation(
        id: String,
        description: String?
    ) = LocalOperation(
        id, "", "food", "EXPENSE", "1.50", "KZT", "1.50", "2025-01-02",
        "2025-01-02T00:00:00Z", "POSTED", 0, 0, description, null, null
    )
}

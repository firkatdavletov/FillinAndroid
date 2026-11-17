package com.firkat.fillin.feature.budgettransfer.data

import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.core.database.OperationPlanProposal
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationOutbox
import java.io.Reader
import java.io.Writer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val BUDGET_CSV_VERSION = 1

internal class BudgetCsvCodec(private val validator: BudgetSnapshotValidator) {
    fun write(
        snapshot: BudgetSnapshot,
        output: Writer
    ) {
        output.write(0xFEFF)
        output.writeCsvRow(*HEADER)
        var count = 0
        fun <T> record(
            type: String,
            id: String,
            serializer: KSerializer<T>,
            value: T
        ) {
            val fields = csvJson.encodeToJsonElement(serializer, value).jsonObject
            fields.forEach { (field, content) ->
                if (field != "scopeKey" && content != JsonNull) {
                    output.writeCsvRow(type, escape(id), field, escape(content.jsonPrimitive.content))
                    count++
                }
            }
        }
        record("budget", "1", SnapshotMetadata.serializer(), snapshot.metadata)
        snapshot.operations.forEach { record("operation", it.id, LocalOperation.serializer(), it) }
        snapshot.categories.forEach { record("category", it.id, LocalCategory.serializer(), it) }
        snapshot.records.forEach { record("record:${it.kind}", it.id, LocalSyncRecord.serializer(), it) }
        snapshot.commands.forEach { record("command", it.requestId, BudgetOutbox.serializer(), it) }
        snapshot.legacyCommands.forEach { record("legacy_command", it.requestId, OperationOutbox.serializer(), it) }
        snapshot.proposals.forEach { record("proposal", it.operationId, OperationPlanProposal.serializer(), it) }
        snapshot.usageEvents.forEach { record("category_usage", it.id, CategoryUsageEvent.serializer(), it) }
        output.writeCsvRow("end", "1", "rows", count.toString())
    }

    fun read(
        input: Reader
    ): BudgetSnapshot {
        val rows = CsvRows(input)
        if (rows.next() != HEADER.toList()) invalid(1)
        val records = readRecords(rows)
        val rowNumbers = records.mapValues { it.value.row }
        val metadataKey = "budget" to "1"
        val metadataRecord = records.remove(metadataKey) ?: invalid(2)
        val version = metadataRecord.fields["formatVersion"]?.toIntOrNull() ?: invalid(metadataRecord.row)
        if (version != BUDGET_CSV_VERSION) {
            throw BudgetTransferException(TransferFailure.UnsupportedVersion, metadataRecord.row)
        }

        fun <T> readAll(
            type: String,
            serializer: KSerializer<T>,
            id: (T) -> String
        ): List<T> = records.filterKeys { it.first == type }.map { (key, row) ->
            records.remove(key)
            decode(row, serializer).also { if (id(it) != key.second) invalid(row.row) }
        }

        val snapshot = BudgetSnapshot(
            metadata = decode(metadataRecord, SnapshotMetadata.serializer()),
            operations = readAll("operation", LocalOperation.serializer(), LocalOperation::id),
            categories = readAll("category", LocalCategory.serializer(), LocalCategory::id),
            records = records.filterKeys { it.first.startsWith("record:") }.map { (key, row) ->
                records.remove(key)
                decode(row, LocalSyncRecord.serializer()).also {
                    if (it.id != key.second || "record:${it.kind}" != key.first) invalid(row.row)
                }
            },
            commands = readAll("command", BudgetOutbox.serializer(), BudgetOutbox::requestId),
            legacyCommands = readAll("legacy_command", OperationOutbox.serializer(), OperationOutbox::requestId),
            proposals = readAll("proposal", OperationPlanProposal.serializer(), OperationPlanProposal::operationId),
            usageEvents = readAll("category_usage", CategoryUsageEvent.serializer(), CategoryUsageEvent::id)
        )
        records.values.firstOrNull()?.let { invalid(it.row) }
        return snapshot.also {
            validator.validate(it) { key -> invalid(rowNumbers[key] ?: metadataRecord.row) }
        }
    }

    private fun readRecords(
        rows: CsvRows
    ): LinkedHashMap<SnapshotRow, CsvRecord> {
        val records = linkedMapOf<SnapshotRow, CsvRecord>()
        var fieldCount = 0
        while (true) {
            val (type, id, field, value) = rows.next() ?: invalid(rows.row + 1)
            if (type == "end") {
                if (id != "1" || field != "rows" || value.toIntOrNull() != fieldCount || rows.next() != null) {
                    invalid(rows.row)
                }
                return records
            }
            val record = records.getOrPut(type to id.removePrefix("'")) { CsvRecord(rows.row) }
            if (record.fields.put(field, value.removePrefix("'")) != null) invalid(rows.row)
            fieldCount++
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> decode(
        row: CsvRecord,
        serializer: KSerializer<T>
    ): T {
        val descriptor = serializer.descriptor
        val fields = row.fields.toMutableMap()
        val values = buildMap {
            repeat(descriptor.elementsCount) { index ->
                val field = descriptor.getElementName(index)
                val type = descriptor.getElementDescriptor(index)
                val value = fields.remove(field)
                put(
                    field,
                    when {
                        field == "scopeKey" && value == null -> JsonPrimitive("")
                        field == "scopeKey" -> invalid(row.row)
                        value == null && type.isNullable -> JsonNull
                        value == null -> invalid(row.row)
                        type.kind == PrimitiveKind.BOOLEAN -> JsonPrimitive(
                            value.toBooleanStrictOrNull() ?: invalid(row.row)
                        )
                        type.kind == PrimitiveKind.INT -> JsonPrimitive(value.toIntOrNull() ?: invalid(row.row))
                        type.kind == PrimitiveKind.LONG -> JsonPrimitive(value.toLongOrNull() ?: invalid(row.row))
                        else -> JsonPrimitive(value)
                    }
                )
            }
        }
        if (fields.isNotEmpty()) invalid(row.row)
        return try {
            csvJson.decodeFromJsonElement(serializer, JsonObject(values))
        } catch (_: SerializationException) {
            invalid(row.row)
        }
    }

    private data class CsvRecord(val row: Int, val fields: MutableMap<String, String> = linkedMapOf())

    private companion object {
        val HEADER = arrayOf("record_type", "record_id", "field", "value")
        val csvJson = Json { encodeDefaults = true }

        // Keep user-entered text literal if the file is opened in a spreadsheet.
        fun escape(
            value: String
        ): String = if (value.startsWith("'") || value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) {
            "'$value"
        } else {
            value
        }

        fun invalid(
            row: Int
        ): Nothing = throw BudgetTransferException(TransferFailure.InvalidFile, row)
    }
}

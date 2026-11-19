package com.firkat.fillin.feature.budgettransfer.data

import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import java.io.PushbackReader
import java.io.Reader
import java.io.Writer

internal fun Writer.writeCsvRow(
    vararg values: String
) {
    values.forEachIndexed { index, value ->
        if (index > 0) write(",")
        write("\"")
        write(value.replace("\"", "\"\""))
        write("\"")
    }
    write("\r\n")
}

/** Reads four-column CSV records, including quoted line endings and escaped quotes. */
internal class CsvRows(reader: Reader) {
    private val input = PushbackReader(reader.buffered(), 1)
    var row = 0
        private set

    init {
        val first = input.read()
        if (first != -1 && first != BOM) input.unread(first)
    }

    fun next(): List<String>? {
        val first = input.read()
        if (first == -1) return null
        input.unread(first)
        row++
        val fields = ArrayList<String>(4)
        var separator: Int
        do {
            if (fields.size == 4) invalid()
            val start = input.read()
            val (value, next) = if (start == QUOTE) quoted() else unquoted(start)
            fields += value
            separator = next
        } while (separator == COMMA)
        if (separator == CR) {
            val next = input.read()
            if (next != LF && next != -1) input.unread(next)
        }
        if (fields.size != 4) invalid()
        return fields
    }

    private fun quoted(): Pair<String, Int> {
        val value = StringBuilder()
        while (true) {
            val current = input.read()
            if (current == -1) invalid()
            if (current == QUOTE) {
                val afterQuote = input.read()
                if (isSeparator(afterQuote)) return value.toString() to afterQuote
                if (afterQuote != QUOTE) invalid()
            }
            append(value, current)
        }
    }

    private fun unquoted(
        first: Int
    ): Pair<String, Int> {
        val value = StringBuilder()
        var current = first
        while (!isSeparator(current)) {
            if (current == QUOTE) invalid()
            append(value, current)
            current = input.read()
        }
        return value.toString() to current
    }

    private fun append(
        value: StringBuilder,
        character: Int
    ) {
        if (value.length >= MAX_FIELD_LENGTH) invalid()
        value.append(character.toChar())
    }

    private fun isSeparator(
        value: Int
    ) = value == -1 || value == COMMA || value == LF || value == CR

    private fun invalid(): Nothing = throw BudgetTransferException(TransferFailure.InvalidFile, row)

    private companion object {
        const val BOM = 0xFEFF
        const val QUOTE = 34
        const val COMMA = 44
        const val LF = 10
        const val CR = 13
        const val MAX_FIELD_LENGTH = 2 * 1024 * 1024
    }
}

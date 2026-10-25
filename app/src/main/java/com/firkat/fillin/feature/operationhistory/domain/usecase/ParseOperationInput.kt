package com.firkat.fillin.feature.operationhistory.domain.usecase

import java.math.BigDecimal

sealed interface OperationInputResult {
    data class Valid(val amount: BigDecimal, val description: String?) : OperationInputResult
    data class Invalid(val reason: OperationInputError) : OperationInputResult
}

enum class OperationInputError { Empty, InvalidAmount, AmbiguousAmount, DescriptionTooLong }

/** A single, separate amount at either edge; the remaining text is the description. */
fun parseOperationInput(input: String): OperationInputResult {
    val text = input.trim()
    if (text.isEmpty()) return OperationInputResult.Invalid(OperationInputError.Empty)
    val tokens = TOKEN.findAll(text).toList()
    // Include malformed decimal and signed tokens so they cannot become a second description number.
    val numbers = tokens.withIndex().filter { NUMBER_TOKEN.matches(it.value.value) }
    if (numbers.size > 1) return OperationInputResult.Invalid(OperationInputError.AmbiguousAmount)
    val number = numbers.singleOrNull()
        ?: return OperationInputResult.Invalid(OperationInputError.InvalidAmount)
    if (number.index != 0 && number.index != tokens.lastIndex || !AMOUNT.matches(number.value.value)) {
        return OperationInputResult.Invalid(OperationInputError.InvalidAmount)
    }
    val amount = number.value.value.replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it <= MAX_AMOUNT }
        ?: return OperationInputResult.Invalid(OperationInputError.InvalidAmount)
    val description = text.removeRange(number.value.range).trim().takeIf(String::isNotEmpty)
    if (description != null && description.length > 160) {
        return OperationInputResult.Invalid(OperationInputError.DescriptionTooLong)
    }
    return OperationInputResult.Valid(amount.setScale(2), description)
}

private val TOKEN = Regex("[^\\s\\p{Z}]+")
private val NUMBER_TOKEN = Regex("[+-]?[0-9][0-9.,]*")
private val AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")
private val MAX_AMOUNT = BigDecimal("99999999999999999.99")

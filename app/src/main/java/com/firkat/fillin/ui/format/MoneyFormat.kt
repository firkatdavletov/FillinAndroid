package com.firkat.fillin.ui.format

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatMoney(amount: BigDecimal, currencyCode: String): String = runCatching {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-KZ")).apply {
        currency = Currency.getInstance(currencyCode)
        val symbol = BudgetCurrency.entries.firstOrNull { it.name == currencyCode }?.symbol
        if (this is DecimalFormat && symbol != null) {
            decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = symbol }
        }
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}.getOrElse {
    "${amount.setScale(2).toPlainString()} $currencyCode"
}

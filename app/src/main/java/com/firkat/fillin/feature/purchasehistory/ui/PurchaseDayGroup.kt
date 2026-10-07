package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import java.math.BigDecimal
import java.time.LocalDate

internal data class PurchaseDayGroup(
    val date: LocalDate,
    val purchases: List<Purchase>,
    val totals: List<PurchaseDayTotal>,
)

internal data class PurchaseDayTotal(
    val amount: BigDecimal,
    val currency: String,
)

internal fun groupPurchasesByDate(purchases: List<Purchase>): List<PurchaseDayGroup> =
    purchases
        .groupBy(Purchase::occurredOn)
        .map { (date, purchasesForDate) ->
            val postedPurchases = purchasesForDate.filter { it.status == PurchaseStatus.Posted }
            val totals = postedPurchases
                .groupBy(Purchase::currency)
                .map { (currency, purchasesForCurrency) ->
                    PurchaseDayTotal(
                        amount = purchasesForCurrency.fold(BigDecimal.ZERO) { total, purchase ->
                            total + purchase.amount
                        },
                        currency = currency,
                    )
                }
                .ifEmpty {
                    listOf(
                        PurchaseDayTotal(
                            amount = BigDecimal.ZERO,
                            currency = purchasesForDate.first().currency,
                        ),
                    )
                }

            PurchaseDayGroup(
                date = date,
                purchases = purchasesForDate,
                totals = totals,
            )
        }

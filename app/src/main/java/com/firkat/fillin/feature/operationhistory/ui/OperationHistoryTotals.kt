package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal

internal const val HistoryFadeOutMillis = 150
internal const val HistoryFadeInMillis = 200
private val TotalsMinHeight = 88.dp

@Composable
internal fun OperationHistoryTotals(
    totals: OperationPeriodTotals?,
    selectedType: OperationType?,
    isLoading: Boolean,
    onTypeSelected: (OperationType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = totals?.currency ?: "KZT"
    AnimatedContent(
        targetState = selectedType,
        modifier = modifier.fillMaxWidth().heightIn(min = TotalsMinHeight).testTag("history-type-totals"),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            (
                fadeIn(tween(HistoryFadeInMillis, delayMillis = HistoryFadeOutMillis)) togetherWith
                    fadeOut(tween(HistoryFadeOutMillis))
                ).using(
                SizeTransform { _, _ -> tween(HistoryFadeOutMillis + HistoryFadeInMillis) }
            )
        },
        label = "History type totals"
    ) { type ->
        if (type == null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OperationType.entries.forEach { cardType ->
                    OperationTotalCard(
                        type = cardType,
                        amount = if (cardType == OperationType.Expense) totals?.expenses else totals?.incomes,
                        currency = currency,
                        isLoading = isLoading,
                        enabled = selectedType == null,
                        onClick = { onTypeSelected(cardType) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            SelectedOperationTotal(
                type = type,
                amount = if (type == OperationType.Expense) totals?.expenses else totals?.incomes,
                currency = currency,
                isLoading = isLoading,
                enabled = selectedType == type,
                onReset = { onTypeSelected(null) }
            )
        }
    }
}

@Composable
private fun OperationTotalCard(
    type: OperationType,
    amount: BigDecimal?,
    currency: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("history-type-${type.apiValue}")
            .clip(shape)
            .selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape,
        color = colors.surfaceContainer,
        contentColor = colors.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.heightIn(min = TotalsMinHeight).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    if (type ==
                        OperationType.Expense
                    ) {
                        R.string.operation_totals_expenses
                    } else {
                        R.string.operation_totals_incomes
                    }
                ),
                style = MaterialTheme.typography.labelLarge
            )
            TotalAmount(type, amount, currency, isLoading, MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SelectedOperationTotal(
    type: OperationType,
    amount: BigDecimal?,
    currency: String,
    isLoading: Boolean,
    enabled: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = TotalsMinHeight).testTag("history-selected-total"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TotalAmount(type, amount, currency, isLoading, MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(
                    if (type ==
                        OperationType.Expense
                    ) {
                        R.string.categories_kind_expense
                    } else {
                        R.string.categories_kind_income
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onReset,
            enabled = enabled,
            modifier = Modifier.testTag("history-type-reset")
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.operation_totals_reset_type)
            )
        }
    }
}

@Composable
private fun TotalAmount(
    type: OperationType,
    amount: BigDecimal?,
    currency: String,
    isLoading: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    if (isLoading && amount == null) {
        CircularProgressIndicator(
            modifier = modifier.size(24.dp).testTag("history-totals-loading-${type.apiValue}"),
            strokeWidth = 2.dp
        )
    } else {
        Text(
            text = amount?.let { formatMoney(it, currency) } ?: "—",
            modifier = modifier.testTag("history-total-${type.apiValue}"),
            style = style,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val PreviewTotals = OperationPeriodTotals(BigDecimal("15000.12"), BigDecimal("1500000.34"))

@Preview(name = "Both types")
@Preview(name = "Both types, dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Both types, large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Both types, wide", widthDp = 840)
@Composable
private fun OperationHistoryTotalsPreview() {
    FillinTheme { Surface { OperationHistoryTotals(PreviewTotals, null, false, {}) } }
}

@Preview(name = "Selected expense")
@Preview(name = "Selected expense, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedExpenseTotalPreview() {
    FillinTheme { Surface { OperationHistoryTotals(PreviewTotals, OperationType.Expense, false, {}) } }
}

@Preview(name = "Selected income")
@Preview(name = "Selected income, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedIncomeTotalPreview() {
    FillinTheme { Surface { OperationHistoryTotals(PreviewTotals, OperationType.Income, false, {}) } }
}

@Preview(name = "Loading totals")
@Preview(name = "Loading totals, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoadingOperationHistoryTotalsPreview() {
    FillinTheme { Surface { OperationHistoryTotals(null, null, true, {}) } }
}

@Preview(name = "Loading selected total")
@Preview(name = "Loading selected total, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoadingSelectedOperationHistoryTotalPreview() {
    FillinTheme { Surface { OperationHistoryTotals(null, OperationType.Expense, true, {}) } }
}

@Preview(name = "Unavailable totals")
@Preview(name = "Unavailable totals, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnavailableOperationHistoryTotalsPreview() {
    FillinTheme { Surface { OperationHistoryTotals(null, null, false, {}) } }
}

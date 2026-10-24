package com.firkat.fillin.ui.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.format.formatMoney
import java.math.BigDecimal

enum class OperationTotalsType(val tagValue: String) {
    Expense("EXPENSE"),
    Income("INCOME"),
}

@Composable
fun OperationTotalsRow(
    expenses: BigDecimal?,
    incomes: BigDecimal?,
    selectedType: OperationTotalsType?,
    isLoading: Boolean,
    onTypeSelected: (OperationTotalsType?) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "operation",
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("$testTagPrefix-type-totals"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OperationTotalsType.entries.filter { selectedType == null || selectedType == it }.forEach { type ->
            OperationTotalBlock(
                type = type,
                amount = if (type == OperationTotalsType.Expense) expenses else incomes,
                selected = selectedType == type,
                isLoading = isLoading,
                onSelect = { onTypeSelected(type) },
                onReset = { onTypeSelected(null) },
                testTagPrefix = testTagPrefix,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OperationTotalBlock(
    type: OperationTotalsType,
    amount: BigDecimal?,
    selected: Boolean,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("$testTagPrefix-type-${type.tagValue}")
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Button,
                onClick = onSelect,
            ),
        shape = shape,
        color = colors.primary,
        contentColor = colors.onPrimary,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 88.dp).padding(start = 12.dp, end = if (selected) 0.dp else 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (type == OperationTotalsType.Expense) R.string.operation_totals_expenses
                        else R.string.operation_totals_incomes,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                when {
                    isLoading && amount == null -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).testTag("$testTagPrefix-totals-loading-${type.tagValue}"),
                        strokeWidth = 2.dp,
                    )
                    else -> Text(
                        text = amount?.let { formatMoney(it, "KZT") } ?: "—",
                        modifier = Modifier.testTag("$testTagPrefix-total-${type.tagValue}"),
                        style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (selected) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("$testTagPrefix-type-reset"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.operation_totals_reset_type),
                    )
                }
            }
        }
    }
}

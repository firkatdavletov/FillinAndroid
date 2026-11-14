package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiColors
import java.math.BigDecimal
import java.time.LocalDate

@Composable
internal fun OperationItem(
    operation: Operation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isIncome = operation.type == OperationType.Income
    val hasRefund = operation.type == OperationType.Expense && operation.refundedAmount.signum() > 0
    val isFullyRefunded = hasRefund && operation.refundedAmount >= operation.originalAmount
    val amount = formatOperationAmount(operation.amount, operation.currency, operation.type)
    val amountColor = when {
        isFullyRefunded -> MaterialTheme.colorScheme.onSurfaceVariant
        isIncome -> MaterialTheme.saldomiColors.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    val amountStyle = MaterialTheme.typography.titleMedium.copy(
        color = amountColor,
        fontWeight = FontWeight.Bold,
        textDecoration = if (isFullyRefunded) TextDecoration.LineThrough else TextDecoration.None
    )

    BoxWithConstraints(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isIncome) MaterialTheme.saldomiColors.incomeContainer else MaterialTheme.saldomiColors.expenseContainer
            ) {
                CategoryIconImage(
                    iconCode = operation.categoryIconCode,
                    categoryName = operation.categoryName,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = operation.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                operation.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasRefund) {
                    Text(
                        text = stringResource(
                            if (isFullyRefunded) {
                                R.string.operation_history_fully_refunded
                            } else {
                                R.string.operation_history_partially_refunded
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (compact) Text(amount, style = amountStyle)
            }
            if (!compact) {
                Text(amount, modifier = Modifier.widthIn(max = 160.dp), style = amountStyle)
            }
        }
    }
}

@Preview(name = "Fully refunded")
@Preview(name = "Fully refunded, dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Fully refunded, compact", widthDp = 320)
@Preview(name = "Fully refunded, large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationItemFullyRefundedPreview() {
    FillinTheme {
        Surface { OperationItem(previewOperation.copy(refundedAmount = previewOperation.originalAmount), onClick = {}) }
    }
}

@Preview(name = "Partially refunded")
@Preview(name = "Partially refunded, dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Partially refunded, compact", widthDp = 320)
@Preview(name = "Partially refunded, large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationItemPartiallyRefundedPreview() {
    FillinTheme {
        Surface { OperationItem(previewOperation.copy(refundedAmount = BigDecimal("300.00")), onClick = {}) }
    }
}

private val previewOperation = Operation(
    id = "preview",
    categoryName = "Продукты",
    description = "Покупки для семейного ужина и завтрака на выходных",
    amount = BigDecimal("1200.00"),
    currency = "KZT",
    originalAmount = BigDecimal("1200.00"),
    originalCurrency = "KZT",
    occurredOn = LocalDate.of(2026, 10, 25),
    status = OperationStatus.Posted,
    version = 0,
    categoryIconCode = "FOOD"
)

@Preview(name = "Expense with long description")
@Preview(name = "Expense with long description, dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Compact", widthDp = 320)
@Preview(name = "Large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Wide", widthDp = 840)
@Composable
private fun OperationItemExpensePreview() {
    FillinTheme { Surface { OperationItem(previewOperation, onClick = {}) } }
}

@Preview(name = "Income with unknown icon and no description")
@Preview(name = "Income with unknown icon and no description, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemIncomePreview() {
    FillinTheme {
        Surface {
            OperationItem(
                previewOperation.copy(
                    type = OperationType.Income,
                    categoryName = "Зарплата",
                    categoryIconCode = "",
                    description = null
                ),
                onClick = {}
            )
        }
    }
}

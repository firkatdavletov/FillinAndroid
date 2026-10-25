package com.firkat.fillin.feature.plannedoperations.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.ui.formatOperationAmount
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun OperationItem(
    purchase: Operation,
    onClick: () -> Unit,
    onCorrectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = purchase.categoryName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatOperationAmount(purchase.amount, purchase.currency, purchase.type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (purchase.type == OperationType.Income) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        if (!purchase.description.isNullOrBlank()) {
            Text(
                text = purchase.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (purchase.syncStatus) {
            OperationSyncStatus.Pending -> {
                Text(
                    text = stringResource(R.string.operation_sync_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OperationSyncStatus.Rejected -> {
                Text(
                    text = stringResource(
                        when (purchase.syncError) {
                            "category_unavailable" -> R.string.operation_sync_invalid_category
                            "invalid_amount" -> R.string.operation_sync_invalid_amount
                            "invalid_date" -> R.string.operation_sync_invalid_date
                            "invalid_description" -> R.string.operation_sync_invalid_description
                            else -> R.string.operation_sync_rejected
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onCorrectClick) { Text(stringResource(R.string.operation_sync_correct)) }
            }

            OperationSyncStatus.Synced -> {
            }
        }
    }
}

private val previewOperation = Operation(
    id = "operation-42", categoryName = "Продукты", description = null,
    amount = BigDecimal("1200.00"), currency = "KZT", originalAmount = BigDecimal("1200.00"),
    originalCurrency = "KZT", occurredOn = LocalDate.of(2026, 10, 25),
    status = OperationStatus.Posted, version = 0,
)

@Preview(name = "Expense")
@Preview(name = "Expense dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemExpensePreview() {
    FillinTheme { Surface { OperationItem(previewOperation, {}, {}) } }
}

@Preview(name = "Income")
@Preview(name = "Income dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemIncomePreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(type = OperationType.Income), {}, {}) } }
}

@Preview(name = "Pending")
@Preview(name = "Pending dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemPendingPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Pending), {}, {}) } }
}

@Preview(name = "Rejected")
@Preview(name = "Rejected dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemRejectedPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Rejected), {}, {}) } }
}

@Preview(name = "Category unavailable")
@Preview(name = "Category unavailable dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemCategoryErrorPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Rejected, syncError = "category_unavailable"), {}, {}) } }
}

@Preview(name = "Invalid amount")
@Preview(name = "Invalid amount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemAmountErrorPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Rejected, syncError = "invalid_amount"), {}, {}) } }
}

@Preview(name = "Invalid date")
@Preview(name = "Invalid date dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemDateErrorPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Rejected, syncError = "invalid_date"), {}, {}) } }
}

@Preview(name = "Invalid description")
@Preview(name = "Invalid description dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationItemDescriptionErrorPreview() {
    FillinTheme { Surface { OperationItem(previewOperation.copy(syncStatus = OperationSyncStatus.Rejected, syncError = "invalid_description"), {}, {}) } }
}

@Preview(name = "Description, compact", widthDp = 320)
@Preview(name = "Description, large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Description, wide", widthDp = 840)
@Composable
private fun OperationItemDescriptionPreview() {
    FillinTheme {
        Surface { OperationItem(previewOperation.copy(description = "Продукты для семейного ужина и покупка на выходные"), {}, {}) }
    }
}

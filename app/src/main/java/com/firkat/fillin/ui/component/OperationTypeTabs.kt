package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun OperationTypeTabs(
    type: OperationType,
    enabled: Boolean,
    onTypeClick: (OperationType) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "operation-type"
) {
    val scheme = MaterialTheme.colorScheme
    PrimaryTabRow(
        selectedTabIndex = type.ordinal,
        modifier = modifier.fillMaxWidth().clip(CircleShape).background(scheme.surfaceVariant),
        containerColor = scheme.surfaceVariant,
        indicator = {},
        divider = {}
    ) {
        OperationType.entries.forEach { item ->
            val selected = item == type
            Tab(
                selected = selected,
                enabled = enabled,
                onClick = { onTypeClick(item) },
                selectedContentColor = scheme.onPrimary,
                unselectedContentColor = scheme.onSurface,
                modifier = Modifier.clip(CircleShape)
                    .background(if (selected) scheme.primary else scheme.surfaceVariant)
                    .testTag("$testTagPrefix-${item.apiValue}"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item == OperationType.Expense) {
                            Icon(
                                Icons.Outlined.RemoveCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_add_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            stringResource(
                                if (item == OperationType.Expense) {
                                    R.string.planned_operations_expense
                                } else {
                                    R.string.planned_operations_income
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}

@Preview(name = "Expense")
@Preview(name = "Expense dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Expense large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationTypeExpensePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Expense, true, {}) }
}

@Preview(name = "Income")
@Preview(name = "Income dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Income large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationTypeIncomePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Income, true, {}) }
}

@Preview(name = "Disabled expense")
@Preview(name = "Disabled expense dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationTypeDisabledExpensePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Expense, false, {}) }
}

@Preview(name = "Disabled income")
@Preview(name = "Disabled income dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationTypeDisabledIncomePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Income, false, {}) }
}

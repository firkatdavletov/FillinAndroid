package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.theme.FillinTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.filled.MoreHoriz

@Composable
internal fun OperationAmountInput(value: String, enabled: Boolean, isError: Boolean,
    onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value, onValueChange = onValueChange, enabled = enabled, isError = isError,
        modifier = modifier, singleLine = true,
        placeholder = {
            Text(
                "0",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        },
        textStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text(com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency.current.symbol, fontSize = 28.sp) },
        trailingIcon = { IconButton(onClick = { onValueChange("") }, enabled = enabled && value.isNotEmpty()) {
            Icon(Icons.Outlined.Backspace, stringResource(R.string.operation_create_clear))
        } },
        colors = TextFieldDefaults.colors(
            cursorColor = if (value.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.primary,
            errorCursorColor = if (value.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.error,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = MaterialTheme.colorScheme.background,
            errorContainerColor = MaterialTheme.colorScheme.background,
            focusedTextColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
internal fun OperationCategoryGrid(
    categories: List<Category>,
    enabled: Boolean,
    onCategoryClick: (String) -> Unit,
    showMore: Boolean,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val tileCount = categories.size + if (showMore) 1 else 0
    BoxWithConstraints(modifier.fillMaxWidth().testTag("operation-create-categories")) {
        val columns = if (maxWidth < 340.dp || fontScale > 1.3f) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until tileCount).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { index ->
                        val category = categories.getOrNull(index)
                        val tag = if (category == null) "operation-create-more-categories"
                            else "operation-create-category-${category.id}"
                        FilledTonalButton(
                            onClick = { if (category == null) onMoreClick() else onCategoryClick(category.id) },
                            enabled = enabled,
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f).heightIn(min = 100.dp).testTag(tag),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (category == null) {
                                    Icon(
                                        Icons.Filled.MoreHoriz,
                                        contentDescription = stringResource(R.string.operation_create_more_categories_description),
                                        modifier = Modifier.size(28.dp),
                                    )
                                } else {
                                    CategoryIconImage(category.icon.code, Modifier.size(28.dp))
                                }
                                Text(
                                    text = if (category == null) stringResource(R.string.operation_create_more_categories)
                                        else category.name.orEmpty(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable
private fun AmountPreview() { FillinTheme { OperationAmountInput("1200", true, false, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable
private fun EmptyAmountPreview() { FillinTheme { OperationAmountInput("", true, false, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable
private fun InvalidAmountPreview() { FillinTheme { OperationAmountInput("0", true, true, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable
private fun SavingAmountPreview() { FillinTheme { OperationAmountInput("1200", false, false, {}) } }
@Preview(widthDp = 393) @Preview(widthDp = 393, uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f) @Composable
private fun GridPreview() { FillinTheme { OperationCategoryGrid(previewOperationCategories(), true, {}, false, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable
private fun DisabledGridPreview() { FillinTheme { OperationCategoryGrid(previewOperationCategories(), false, {}, false, {}) } }

@Preview(widthDp = 393)
@Preview(widthDp = 393, uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun GridWithMorePreview() {
    FillinTheme { OperationCategoryGrid(previewCategoriesWithMore(), true, {}, true, {}) }
}

@Preview(widthDp = 393)
@Preview(widthDp = 393, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DisabledGridWithMorePreview() {
    FillinTheme { OperationCategoryGrid(previewCategoriesWithMore(), false, {}, true, {}) }
}

private fun previewCategoriesWithMore() = previewOperationCategories().let { categories ->
    (categories + categories).take(7)
}

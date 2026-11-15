package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun OperationCategoryRow(
    categories: List<Category>,
    enabled: Boolean,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedCategoryId: String? = null
) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(selectedCategoryId) {
        val index = categories.indexOfFirst {
            it.id == selectedCategoryId
        }.coerceAtLeast(0)
        scrollState.scrollToItem(index)
    }
    LazyRow(
        state = scrollState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth().testTag("operation-create-categories")
    ) {
        items(categories, key = { it.id }) { category ->
            OperationCategoryCard(
                name = category.name,
                iconCode = category.icon.code,
                enabled = enabled,
                onClick = { onCategoryClick(category.id) },
                modifier = Modifier
                    .testTag("operation-create-category-" + category.id),
                selected = category.id == selectedCategoryId
            )
        }
    }
}

@Composable
private fun OperationCategoryCard(
    name: String?,
    iconCode: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.semantics {
            this.selected = selected
        }.widthIn(min = 80.dp, max = 128.dp).heightIn(min = 88.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIconImage(iconCode, name.orEmpty(), Modifier.size(26.dp).alpha(if (enabled) 1f else 0.38f))
            if (!name.isNullOrBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "Category")
@Preview(name = "Category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "FOOD", true, {}) }
}

@Preview(name = "Disabled category")
@Preview(name = "Disabled category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryDisabledPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "FOOD", false, {}) }
}

@Preview(name = "Long category", fontScale = 2f)
@Preview(name = "Long category dark", fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryLongPreview() {
    FillinTheme { OperationCategoryCard("Обслуживание автомобиля", "TRANSPORT", true, {}) }
}

@Preview(name = "Category row", widthDp = 320)
@Preview(name = "Category row dark", widthDp = 320, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowPreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories(), true, {}) }
}

@Preview(name = "Single category")
@Preview(name = "Single category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowSinglePreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories().take(1), true, {}) }
}

@Preview(name = "Disabled row")
@Preview(name = "Disabled row dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowDisabledPreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories(), false, {}) }
}

internal fun previewOperationCategories() = listOf(
    Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "🍔")),
    Category("transport", "Транспорт", CategoryKind.Expense, CategoryIcon("TRANSPORT", "🚕")),
    Category("home", "Дом", CategoryKind.Expense, CategoryIcon("HOME", "🏠")),
    Category("health", "Здоровье", CategoryKind.Expense, CategoryIcon("HEALTH", "💊")),
    Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰"))
)

@Preview(name = "Selected category")
@Preview(name = "Selected category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedOperationCategoryPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "FOOD", true, {}, selected = true) }
}

@Preview(name = "Disabled selected category")
@Preview(name = "Disabled selected category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DisabledSelectedOperationCategoryPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "FOOD", false, {}, selected = true) }
}

@Preview(name = "Selected category row")
@Preview(name = "Selected category row dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedOperationCategoryRowPreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories(), true, {}, selectedCategoryId = "food") }
}

@Preview(name = "Empty category row")
@Preview(name = "Empty category row dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun EmptyOperationCategoryRowPreview() {
    FillinTheme { OperationCategoryRow(emptyList(), true, {}) }
}

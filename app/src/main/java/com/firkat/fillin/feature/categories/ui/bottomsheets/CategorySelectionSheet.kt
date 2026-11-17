package com.firkat.fillin.feature.categories.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.ui.CategoryItem
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySelectionSheet(
    categories: List<Category>,
    selectedId: String?,
    loading: Boolean,
    hasError: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.rule_category), style = MaterialTheme.typography.titleLarge)
            when {
                loading -> CircularProgressIndicator()
                hasError -> {
                    Text(stringResource(R.string.rule_error))
                    TextButton(onRetry) { Text(stringResource(R.string.planned_operations_retry)) }
                }
                else -> {
                    if (categories.isEmpty()) Text(stringResource(R.string.rule_no_categories))
                    categories.forEach { category ->
                        Surface(
                            color = if (category.id ==
                                selectedId
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            CategoryItem(
                                category,
                                {
                                    onSelect(category.id)
                                },
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics {
                                    selected =
                                        category.id == selectedId
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun EmptyCategoriesPreview() {
    FillinTheme { CategorySelectionSheet(emptyList(), null, false, false, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoadingCategoriesPreview() {
    FillinTheme { CategorySelectionSheet(emptyList(), null, true, false, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoriesErrorPreview() {
    FillinTheme { CategorySelectionSheet(emptyList(), null, false, true, {}, {}, {}) }
}

private val previewCategories = listOf(
    Category(
        "food",
        "Продукты",
        com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense,
        com.firkat.fillin.feature.categories.domain.model.CategoryIcon("FOOD")
    ),
    Category(
        "home",
        "Обслуживание семейного автомобиля",
        com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense,
        com.firkat.fillin.feature.categories.domain.model.CategoryIcon("HOME")
    )
)

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedCategoriesPreview() {
    FillinTheme { CategorySelectionSheet(previewCategories, "food", false, false, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnselectedCategoriesPreview() {
    FillinTheme { CategorySelectionSheet(previewCategories.take(1), null, false, false, {}, {}, {}) }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun LongCategoriesPreview() {
    FillinTheme { CategorySelectionSheet(previewCategories, "home", false, false, {}, {}, {}) }
}

package com.firkat.fillin.feature.categories.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesRoute(
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onEvent: (CategoriesEvent) -> Unit,
) {
    when {
        state.isLoading && state.items.isEmpty() -> LoadingContent()

        state.error != null && state.items.isEmpty() -> ErrorContent(
            error = state.error,
            onRetry = { onEvent(CategoriesEvent.Retry) },
            modifier = Modifier.fillMaxSize(),
        )

        state.items.isEmpty() -> EmptyContent()

        else -> CategoriesList(items = state.items)
    }
}

@Composable
private fun CategoriesList(items: List<Category>) {
    val expenseCategories = remember(items) {
        items.filter { it.kind == CategoryKind.Expense }
    }
    val incomeCategories = remember(items) {
        items.filter { it.kind == CategoryKind.Income }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
    ) {
        categorySection(
            title = R.string.categories_expenses_section,
            categories = expenseCategories,
        )
        categorySection(
            title = R.string.categories_incomes_section,
            categories = incomeCategories,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categorySection(
    @StringRes title: Int,
    categories: List<Category>,
) {
    if (categories.isEmpty()) return

    item(key = "section-$title") {
        Text(
            text = stringResource(title),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(
        items = categories,
        key = Category::id,
    ) { category ->
        CategoryItem(category = category)
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun CategoryItem(category: Category) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = category.icon.emoji,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Text(
            text = category.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.categories_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    error: CategoriesError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(error.messageResource),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.categories_retry))
        }
    }
}

private val CategoriesError.messageResource: Int
    @StringRes get() = when (this) {
        CategoriesError.MissingApiToken,
        CategoriesError.Unauthorized,
        -> R.string.categories_error_unauthorized

        CategoriesError.Forbidden -> R.string.categories_error_forbidden
        CategoriesError.Network -> R.string.categories_error_network
        CategoriesError.InvalidResponse -> R.string.categories_error_invalid_response
        CategoriesError.Unknown -> R.string.categories_error_generic
    }

@Preview(showBackground = true)
@Composable
private fun CategoriesPreview() {
    FillinTheme(dynamicColor = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                items = listOf(
                    Category(
                        id = "groceries",
                        name = "Продукты",
                        kind = CategoryKind.Expense,
                        icon = CategoryIcon(code = "FOOD", emoji = "🍔"),
                    ),
                    Category(
                        id = "salary",
                        name = "Зарплата",
                        kind = CategoryKind.Income,
                        icon = CategoryIcon(code = "SALARY", emoji = "💰"),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

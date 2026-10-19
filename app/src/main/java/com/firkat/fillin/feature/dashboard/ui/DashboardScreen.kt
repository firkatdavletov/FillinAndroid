package com.firkat.fillin.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendRoute
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastRoute
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitRoute
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui.MonthlyBudgetRoute
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceRoute
import com.firkat.fillin.feature.sync.ui.BudgetSyncPanel
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.RefreshableContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = koinViewModel(),
    syncViewModel: BudgetSyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(DashboardEvent.Resume) }
    Column(Modifier.fillMaxSize()) {
        BudgetSyncPanel(syncViewModel)
        Box(Modifier.weight(1f)) {
            DashboardScreen(state, viewModel::onEvent) { widget ->
                when (widget.kind) {
                    DashboardWidgetKind.TotalBalance -> TotalBalanceRoute()
                    DashboardWidgetKind.AvailableToSpend -> AvailableToSpendRoute()
                    DashboardWidgetKind.DailyLimit -> DailyLimitRoute()
                    DashboardWidgetKind.BalanceForecast -> BalanceForecastRoute()
                    DashboardWidgetKind.MonthlyBudget -> MonthlyBudgetRoute()
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    widgetContent: @Composable (DashboardWidget) -> Unit,
) {
    RefreshableContent(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(DashboardEvent.Refresh) },
        hasScrollableContent = state.status == DashboardStatus.Ready,
    ) {
        when (state.status) {
            DashboardStatus.Ready -> DashboardGrid(
                header = { DashboardHeader(state.date) }, widgetContent = widgetContent,
            )
            DashboardStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(when (state.status) {
                        DashboardStatus.Forbidden -> R.string.dashboard_error_forbidden
                        DashboardStatus.LocalError -> R.string.dashboard_error_local_data
                        else -> R.string.dashboard_initial_sync
                    }), textAlign = TextAlign.Center,
                )
                if (state.status != DashboardStatus.WaitingForBudget) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onEvent(DashboardEvent.Retry) }) { Text(stringResource(R.string.dashboard_retry)) }
                }
            }
        }
    }
}

@Composable
fun DashboardGrid(
    modifier: Modifier = Modifier,
    widgets: List<DashboardWidget> = DefaultDashboardWidgets,
    header: (@Composable () -> Unit)? = null,
    widgetContent: @Composable (DashboardWidget) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val cellWidth = (maxWidth - 32.dp - 12.dp) / 2
        // Keep two columns while allowing enlarged labels to wrap above the values.
        val rowHeight = cellWidth * fontScale + 32.dp * (fontScale - 1f)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().testTag("dashboard-grid"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (header != null) item(key = "dashboard-header", span = { GridItemSpan(2) }) { header() }
            items(widgets, key = { it.id }, span = { GridItemSpan(it.size.columns) }) { widget ->
                Box(
                    Modifier.fillMaxWidth()
                        .height(rowHeight * widget.size.rows + 12.dp * (widget.size.rows - 1))
                        .testTag("dashboard-widget-${widget.id}"),
                    propagateMinConstraints = true,
                ) { widgetContent(widget) }
            }
        }
    }
}

@Composable
private fun DashboardHeader(date: LocalDate?) {
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        date?.let {
            val locale = Locale.forLanguageTag("ru-KZ")
            Text(
                it.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { char -> char.titlecase(locale) },
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

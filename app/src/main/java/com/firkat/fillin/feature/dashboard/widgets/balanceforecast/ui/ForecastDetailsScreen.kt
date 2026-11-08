package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

enum class ForecastDetailsTab { Forecast, Events, Parameters }
sealed interface ForecastDetailsEvent { data class SelectTab(val tab: ForecastDetailsTab) : ForecastDetailsEvent; data object Resume : ForecastDetailsEvent }
data class ForecastDetailsUiState(val forecast: ForecastUiState = ForecastUiState(), val tab: ForecastDetailsTab = ForecastDetailsTab.Forecast)
class ForecastDetailsViewModel(private val observe: ObserveBalanceForecast, private val savedState: SavedStateHandle) : ViewModel() {
    val uiState = combine(observe.states(), savedState.getStateFlow("forecast_tab", ForecastDetailsTab.Forecast.name)) { data, tab ->
        ForecastDetailsUiState(data.dashboard?.balanceForecast?.let(ForecastUiState::from) ?: ForecastUiState(dataStatus = data.status), ForecastDetailsTab.valueOf(tab))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ForecastDetailsUiState())
    fun onEvent(event: ForecastDetailsEvent) { when (event) {
        is ForecastDetailsEvent.SelectTab -> savedState["forecast_tab"] = event.tab.name
        ForecastDetailsEvent.Resume -> observe.resume()
    } }
}

@Composable
fun ForecastDetailsRoute(onBack: () -> Unit, viewModel: ForecastDetailsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ForecastDetailsEvent.Resume) }
    FeatureScaffold(titleResource = R.string.forecast_details, onNavigateBack = onBack) { ForecastDetailsScreen(state, viewModel::onEvent) }
}

@Composable
internal fun ForecastDetailsScreen(state: ForecastDetailsUiState, onEvent: (ForecastDetailsEvent) -> Unit, modifier: Modifier = Modifier) {
    val forecast = state.forecast
    val projection = forecast.projection
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        @OptIn(ExperimentalMaterial3Api::class)
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            listOf(R.string.forecast_tab, R.string.forecast_events_tab, R.string.forecast_parameters_tab).forEachIndexed { index, title ->
                Tab(selected = index == state.tab.ordinal, onClick = { onEvent(ForecastDetailsEvent.SelectTab(ForecastDetailsTab.entries[index])) }, text = { Text(stringResource(title)) })
            }
        }
        if (forecast.points.isEmpty()) { ForecastUnavailable(forecast.dataStatus, Modifier.padding(20.dp)); return@Column }
        LazyColumn(modifier = Modifier.testTag("forecast-details-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.tab) {
                ForecastDetailsTab.Forecast -> {
                    item { ForecastDetailsChart(forecast) }
                    item { ForecastAdaptiveRow { forecast.currentPace?.let { ForecastScenarioDetails(R.string.forecast_current_pace, it) }; ForecastScenarioDetails(R.string.forecast_by_limit, forecast.byLimit) } }
                    if (projection?.dependsOnPlannedIncome == true) item { ForecastInfo(stringResource(R.string.forecast_income_explanation)) }
                    if (projection?.structuralDeficitDate != null) item { ForecastInfo(stringResource(R.string.forecast_replan)) }
                    else projection?.recommendedReduction?.let { reduction -> item { ForecastInfo(stringResource(R.string.forecast_reduction, formatMoney(reduction.amount, reduction.currency))) } }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.forecast_key_parameters), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { onEvent(ForecastDetailsEvent.SelectTab(ForecastDetailsTab.Parameters)) }) { ForecastParameter(R.string.forecast_daily_limit, projection?.dailyLimit) }
                                TextButton(onClick = { onEvent(ForecastDetailsEvent.SelectTab(ForecastDetailsTab.Parameters)) }) { ForecastParameter(R.string.forecast_average, projection?.averageDailyExpenses) }
                            }
                        }
                    }
                }
                ForecastDetailsTab.Events -> {
                    val events = projection?.events.orEmpty().sortedWith(compareBy({ it.dueOn }, { it.id }))
                    if (events.isEmpty()) item { Text(stringResource(R.string.forecast_no_events)) }
                    items(events, key = { it.id + ":" + it.dueOn + ":" + it.income }) { event ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(if (event.income) R.string.forecast_income else R.string.forecast_payment), style = MaterialTheme.typography.labelMedium)
                                Text(event.name, style = MaterialTheme.typography.titleMedium)
                                Text((if (event.income) "+" else "−") + formatMoney(event.amount.amount, event.amount.currency))
                                Text(stringResource(R.string.forecast_due_on, forecastDate(event.dueOn, "d MMMM yyyy")))
                                if (event.income) Text(stringResource(R.string.forecast_available_on, forecastDate(event.availableOn, "d MMMM yyyy")))
                            }
                        }
                    }
                }
                ForecastDetailsTab.Parameters -> {
                    item { ForecastHeading(forecast) }
                    item { ForecastParameter(R.string.forecast_current_balance, projection?.currentBalance) }
                    item { ForecastParameter(R.string.forecast_average, projection?.averageDailyExpenses) }
                    item { ForecastParameter(R.string.forecast_daily_limit, projection?.dailyLimit) }
                    item { ForecastParameter(R.string.forecast_remaining_today, projection?.remainingToday) }
                    item { ForecastParameter(R.string.forecast_next_limit, projection?.nextDaysLimit) }
                    item { ForecastInfo(stringResource(R.string.forecast_method_explanation)) }
                    item { ForecastInfo(stringResource(R.string.forecast_distribution_explanation)) }
                    if (forecast.warnings.isNotEmpty()) item { ForecastInfo(stringResource(R.string.daily_incomplete)) }
                    if (forecast.dependsOnPlannedIncome) item { ForecastInfo(stringResource(R.string.forecast_income_explanation)) }
                }
            }
        }
    }
}

@Composable
private fun ForecastDetailsChart(state: ForecastUiState, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ForecastHeading(state)
        ForecastStatusMessage(state)
        ForecastLegend(state.currentPace != null)
        ForecastChart(state, Modifier.fillMaxWidth())
    } }
}

@Composable
private fun ForecastScenarioDetails(label: Int, series: ForecastSeries, modifier: Modifier = Modifier) {
    Card(modifier.widthIn(max = 320.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ForecastAmount(label, series.endingBalance)
        Text(series.firstDeficitDate?.let { stringResource(R.string.forecast_first_deficit, forecastDate(it, "d MMMM")) } ?: stringResource(R.string.forecast_normal))
        series.minimum?.let { minimum ->
            Text(stringResource(R.string.dashboard_forecast_minimum), style = MaterialTheme.typography.labelMedium)
            Text(signedForecastMoney(minimum.balance), style = MaterialTheme.typography.titleMedium)
            Text(forecastDate(minimum.date, "d MMMM"))
        }
    } }
}

@Composable
private fun ForecastParameter(label: Int, money: Money?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(money?.let { formatMoney(it.amount, it.currency) } ?: stringResource(R.string.dashboard_value_unavailable), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ForecastInfo(text: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text(text, Modifier.padding(16.dp)) }
}

@Preview(name = "Details", widthDp = 390, heightDp = 1000)
@Preview(name = "Details dark", widthDp = 390, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Details compact large text", widthDp = 320, heightDp = 1000, fontScale = 2f)
@Preview(name = "Details expanded", widthDp = 840, heightDp = 1000)
@Composable private fun DetailsPreview() { FillinTheme { ForecastDetailsScreen(ForecastDetailsUiState(forecastPreviewState), {}) } }
@Preview(name = "Events", widthDp = 390)
@Preview(name = "Events dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun EventsPreview() { FillinTheme { ForecastDetailsScreen(ForecastDetailsUiState(forecastPreviewState, ForecastDetailsTab.Events), {}) } }
@Preview(name = "Parameters", widthDp = 390)
@Preview(name = "Parameters dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ParametersPreview() { FillinTheme { ForecastDetailsScreen(ForecastDetailsUiState(forecastPreviewState, ForecastDetailsTab.Parameters), {}) } }

@Preview(name = "DetailsChart", widthDp = 390)
@Preview(name = "DetailsChart dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun DetailsChartComponentPreview() { FillinTheme { ForecastDetailsChart(forecastPreviewState) } }

@Preview(name = "ScenarioDeficit", widthDp = 390)
@Preview(name = "ScenarioDeficit dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ScenarioDeficitComponentPreview() { FillinTheme { ForecastScenarioDetails(R.string.forecast_current_pace, requireNotNull(forecastPreviewState.currentPace)) } }

@Preview(name = "ScenarioSafe", widthDp = 390)
@Preview(name = "ScenarioSafe dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ScenarioSafeComponentPreview() { FillinTheme { ForecastScenarioDetails(R.string.forecast_by_limit, forecastPreviewState.byLimit) } }

@Preview(name = "Parameter", widthDp = 390)
@Preview(name = "Parameter dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ParameterComponentPreview() { FillinTheme { ForecastParameter(R.string.forecast_daily_limit, forecastPreviewState.projection?.dailyLimit) } }

@Preview(name = "ParameterMissing", widthDp = 390)
@Preview(name = "ParameterMissing dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ParameterMissingComponentPreview() { FillinTheme { ForecastParameter(R.string.forecast_average, null) } }

@Preview(name = "Info", widthDp = 390)
@Preview(name = "Info dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun InfoComponentPreview() { FillinTheme { ForecastInfo(stringResource(R.string.forecast_income_explanation)) } }

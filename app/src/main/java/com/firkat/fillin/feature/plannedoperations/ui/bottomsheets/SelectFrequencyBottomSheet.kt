package com.firkat.fillin.feature.plannedoperations.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.ui.editorTitleResource
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.component.SaldomiRadioButton
import com.firkat.fillin.ui.theme.FillinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectFrequencyBottomSheet(
    type: PlannedOperationType,
    selectedFrequency: PlanningFrequency,
    onSelect: (PlanningFrequency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val frequencies = PlanningFrequency.entries.filter {
        type == PlannedOperationType.Income || it != PlanningFrequency.Biweekly
    }

    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.rule_frequency),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
            Column(Modifier.selectableGroup()) {
                frequencies.forEach { frequency ->
                    SaldomiRadioButton(
                        label = stringResource(frequency.editorTitleResource),
                        selected = frequency == selectedFrequency,
                        onClick = {
                            onSelect(frequency)
                            onDismiss()
                        },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Preview(name = "Expense once")
@Preview(name = "Expense once dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseOnceFrequencyPreview() {
    FillinTheme {
        SelectFrequencyBottomSheet(PlannedOperationType.Expense, PlanningFrequency.Once, {}, {})
    }
}

@Preview(name = "Expense weekly")
@Preview(name = "Expense weekly dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseWeeklyFrequencyPreview() {
    FillinTheme {
        SelectFrequencyBottomSheet(PlannedOperationType.Expense, PlanningFrequency.Weekly, {}, {})
    }
}

@Preview(name = "Income biweekly")
@Preview(name = "Income biweekly dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Income compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@Preview(name = "Income wide", widthDp = 840)
@Composable
private fun IncomeBiweeklyFrequencyPreview() {
    FillinTheme {
        SelectFrequencyBottomSheet(PlannedOperationType.Income, PlanningFrequency.Biweekly, {}, {})
    }
}

@Preview(name = "Income monthly")
@Preview(name = "Income monthly dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeMonthlyFrequencyPreview() {
    FillinTheme {
        SelectFrequencyBottomSheet(PlannedOperationType.Income, PlanningFrequency.Monthly, {}, {})
    }
}

@Preview(name = "Disabled frequency options")
@Preview(name = "Disabled frequency options dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DisabledFrequencyOptionsPreview() {
    FillinTheme {
        SelectFrequencyBottomSheet(PlannedOperationType.Income, PlanningFrequency.Weekly, {}, {}, enabled = false)
    }
}

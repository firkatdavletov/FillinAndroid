package com.firkat.fillin.feature.operationhistory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.feature.operationhistory.ui.bottomsheets.OperationDetailsBottomSheetHost
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.ui.component.OperationTypeTabs
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.utils.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun OperationDetailsRoute(
    operationId: String,
    onClose: () -> Unit,
    onResult: (EditOperationResult) -> Unit,
    onLinkSelected: () -> Unit,
    onOpenPlan: (PlannedOperationType, String) -> Unit,
    viewModel: EditOperationViewModel = koinViewModel { parametersOf(operationId) },
    readOnly: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val result by rememberUpdatedState(onResult)
    val openPlan by rememberUpdatedState(onOpenPlan)
    val event = viewModel::onEvent
    val closeDetails = { if (readOnly) onClose() else event(EditOperationEvent.CloseClicked) }
    BackHandler(onBack = closeDetails)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditOperationEffect.Close -> close()
                is EditOperationEffect.Saved -> result(effect.result)
                is EditOperationEffect.OpenPlan -> openPlan(effect.type, effect.ruleId)
            }
        }
    }
    EditOperationContent(
        operation = state.operation,
        mode = if (readOnly) EditOperationMode.ReadOnly else state.mode,
        input = state.input,
        type = state.type,
        categories = state.categories, selectedCategoryId = state.categoryId, isLoading = state.isLoading,
        isSaving = state.isSaving, canSave = state.canSave, canRefund = state.canRefund,
        inputError = state.inputError,
        error = state.error,
        description = state.description,
        descriptionError = state.descriptionError,
        links = state.links,
        onUnlink = {
            event(EditOperationEvent.WindowChanged(OperationDetailsWindow.Unlink))
        },
        onOpen = {
            event(EditOperationEvent.OpenPlan(it))
        },
        onLinkSelected = onLinkSelected,
        onDescriptionChange = { event(EditOperationEvent.DescriptionChanged(it)) },
        onInputChange = {
            event(EditOperationEvent.InputChanged(it))
        }, onTypeClick = { event(EditOperationEvent.TypeChanged(it)) },
        onCategoryOpen = { event(EditOperationEvent.WindowChanged(OperationDetailsWindow.Category)) },
        onSaveClick = {
            event(EditOperationEvent.SaveClicked)
        }, onRefundClick = { event(EditOperationEvent.RefundClicked) },
        onCloseClick = closeDetails
    )
    OperationDetailsBottomSheetHost(
        window = state.window,
        readOnly = readOnly,
        categories = state.categories,
        categoryId = state.categoryId,
        showRefundConfirmation = state.isRefundConfirmationVisible,
        dirty = state.dirty,
        onCategorySelected = { event(EditOperationEvent.CategorySelected(it)) },
        onDismissWindow = { event(EditOperationEvent.WindowChanged(null)) },
        onDiscard = { event(EditOperationEvent.DiscardConfirmed) },
        onUnlink = { event(EditOperationEvent.UnlinkConfirmed) },
        onRefund = { event(EditOperationEvent.RefundConfirmed) },
        onDismissRefund = { event(EditOperationEvent.RefundDismissed) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditOperationContent(
    operation: Operation?,
    mode: EditOperationMode,
    input: String,
    type: OperationType,
    categories: List<Category>,
    selectedCategoryId: String?,
    isLoading: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    canRefund: Boolean,
    inputError: OperationInputError?,
    error: OperationHistoryError?,
    links: OperationPlanLinks,
    onInputChange: (String) -> Unit,
    onTypeClick: (OperationType) -> Unit,
    onSaveClick: () -> Unit,
    onRefundClick: () -> Unit,
    onCloseClick: () -> Unit,
    onUnlink: () -> Unit,
    onOpen: (SuggestedPlan) -> Unit,
    onLinkSelected: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = operation?.description.orEmpty(),
    descriptionError: Boolean = false,
    onDescriptionChange: (String) -> Unit = {},
    onCategoryOpen: () -> Unit = {}
) {
    val canEdit = mode == EditOperationMode.Edit &&
        operation?.refundedAmount?.signum() == 0
    val editableMode = mode == EditOperationMode.Correct || canEdit
    val editable = !isSaving && editableMode

    val locale = LocalConfiguration.current.locales[0]
    Scaffold(modifier.fillMaxSize().testTag("operation_details"), topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.edit_operation_title)) },
            navigationIcon = {
                IconButton(
                    onClick = onCloseClick,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("edit_operation_close")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.navigation_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (mode != EditOperationMode.ReadOnly) {
                    links.current.forEach { plan ->
                        OperationLinkedPlanCard(
                            plan,
                            !isSaving && mode == EditOperationMode.Edit,
                            onUnlink = onUnlink,
                            onOpen = {
                                onOpen(plan)
                            }
                        )
                    }
                }
                if (mode == EditOperationMode.Correct) {
                    OperationTypeTabs(
                        type = type,
                        enabled = !isSaving,
                        onTypeClick = onTypeClick,
                        testTagPrefix = "operation-create-type"
                    )
                } else {
                    Text(
                        stringResource(
                            if (type == OperationType.Expense) {
                                R.string.planned_operations_expense
                            } else {
                                R.string.planned_operations_income
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                if (isLoading) CircularProgressIndicator(Modifier.testTag("edit_operation_loading"))

                operation?.let { op ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rule_category), style = MaterialTheme.typography.titleMedium)
                        OutlinedCard(
                            onClick = onCategoryOpen,
                            enabled = editable,
                            modifier = Modifier.fillMaxWidth().testTag("operation_details_category")
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val selected = categories.firstOrNull { it.id == selectedCategoryId }
                                selected?.let {
                                    com.firkat.fillin.ui.component.CategoryIconImage(it.icon.code, it.name)
                                }
                                Text(selected?.name ?: op.categoryName, Modifier.weight(1f))
                                if (editable) Text("›")
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.operation_details_date),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedCard(Modifier.fillMaxWidth().testTag("operation_details_date")) {
                            Text(
                                op.occurredOn.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                                Modifier.padding(16.dp)
                            )
                        }
                    }
                    if (mode == EditOperationMode.ReadOnly) {
                        Text(formatOperationAmount(op.amount, op.currency, op.type))
                        if (op.hasOriginalValuation) {
                            Text(
                                stringResource(
                                    R.string.operation_original_amount,
                                    formatOperationAmount(op.originalAmount, op.originalCurrency, op.type)
                                )
                            )
                            op.rubKztRate?.let {
                                Text(stringResource(R.string.operation_original_rub_rate, it.toPlainString()))
                            }
                        }
                        op.description?.let { Text(it) }
                        Text(stringResource(R.string.edit_operation_read_only))
                    } else {
                        Text(
                            stringResource(
                                if (mode ==
                                    EditOperationMode.Correct
                                ) {
                                    R.string.operation_create_placeholder
                                } else {
                                    R.string.operation_history_amount_label
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        SaldomiTextField(
                            input,
                            onInputChange,
                            enabled = editable,
                            isError = inputError != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (mode ==
                                    EditOperationMode.Correct
                                ) {
                                    KeyboardType.Text
                                } else {
                                    KeyboardType.Decimal
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_operation_input")
                        )
                        inputError?.let { reason ->
                            Text(
                                stringResource(
                                    when (reason) {
                                        OperationInputError.DescriptionTooLong ->
                                            R.string.operation_create_description_too_long

                                        OperationInputError.AmbiguousAmount ->
                                            R.string.operation_create_ambiguous_amount

                                        else -> R.string.operation_create_invalid_input
                                    }
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (mode == EditOperationMode.Edit) {
                            Text(
                                stringResource(R.string.operation_create_description),
                                style = MaterialTheme.typography.titleMedium
                            )
                            SaldomiTextField(
                                description,
                                onDescriptionChange,
                                enabled = editable,
                                isError = descriptionError,
                                placeholder = stringResource(R.string.operation_details_description_hint),
                                modifier = Modifier.fillMaxWidth().testTag("edit_operation_description")
                            )
                            if (descriptionError) {
                                Text(
                                    stringResource(R.string.operation_create_description_too_long),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("edit_operation_description_error")
                                )
                            }
                            if (op.refundedAmount.signum() != 0) Text(stringResource(R.string.edit_operation_refunded))
                        }
                    }
                }
                error?.let {
                    Text(
                        stringResource(
                            if (it ==
                                OperationHistoryError.Unknown
                            ) {
                                R.string.edit_operation_save_failed
                            } else {
                                it.messageResource
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("edit_operation_error")
                    )
                }

                if (operation != null && links.current.isEmpty()) {
                    SaldomiOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLinkSelected,
                        enabled = (!isSaving && mode == EditOperationMode.Edit) &&
                            operation.let { it.amount > it.refundedAmount },
                        large = true
                    ) {
                        Text(stringResource(R.string.operation_details_bind))
                    }
                }
            }
            if (operation != null &&
                mode != EditOperationMode.ReadOnly
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    PrimaryButton(
                        stringResource(
                            if (mode ==
                                EditOperationMode.Correct
                            ) {
                                R.string.operation_sync_correct
                            } else {
                                R.string.operation_details_save
                            }
                        ),
                        onSaveClick,
                        enabled = canSave,
                        isLoading = isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("edit_operation_save")
                    )
                    if (mode == EditOperationMode.Edit &&
                        type == OperationType.Expense
                    ) {
                        TextButton(
                            onRefundClick,
                            enabled = canRefund,
                            modifier = Modifier.fillMaxWidth().testTag("edit_operation_refund")
                        ) {
                            Text(stringResource(R.string.operation_history_refund_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationLinkedPlanCard(
    plan: SuggestedPlan,
    enabled: Boolean,
    onUnlink: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Card(
        modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.EventAvailable,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.operation_details_linked),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.operation_details_paid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(plan.categoryName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.operation_details_money,
                        plan.plannedAmount,
                        LocalBudgetCurrency.current.symbol
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                stringResource(
                    R.string.operation_details_payment_date,
                    DateUtils.getShortDate(LocalDate.parse(plan.target.dueOn))
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onUnlink,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.LinkOff, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.operation_details_unlink),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                OutlinedButton(
                    onOpen,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Outlined.OpenInNew,
                        null,
                        Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.operation_details_open_plan),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

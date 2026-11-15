package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOffer
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkState
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferError
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferPage
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferSelection
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.ui.theme.FillinTheme

private val previewExpense = PlanLinkOperation(
    0, "EXPENSE", "communication", "Связь", "COMMUNICATION", "KZT", "2026-11-15", "1250", "1250"
)
private val previewPayment = SuggestedPlan(
    PlanLinkTarget("EXPENSE", ruleId = "internet", dueOn = "2026-11-16"),
    "communication", "Связь", "1250", "1250", 0, "Интернет", PlanningFrequency.Monthly, "KZT"
)
private val previewOther = previewPayment.copy(
    target = previewPayment.target.copy(ruleId = "mobile", dueOn = "2026-11-14"),
    name = "Мобильная связь"
)

@Preview(name = "Совпадение", widthDp = 390)
@Preview(name = "Совпадение, тёмная тема", widthDp = 390, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExactOfferPreview() {
    OfferPreview(review())
}

@Preview(name = "Несколько вариантов")
@Preview(name = "Несколько вариантов", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ChoiceOfferPreview() {
    val plans = listOf(previewPayment, previewOther)
    OfferPreview(
        OperationPlanLinkState(
            PlanOfferPage.Suggestions,
            OperationPlanOffer(previewExpense, PlanSuggestion.Choice(plans, true)),
            localContext(plans)
        )
    )
}

@Preview(name = "Ручной список правил")
@Composable
private fun ManualOfferPreview() {
    OfferPreview(
        OperationPlanLinkState(PlanOfferPage.Manual, context = localContext(listOf(previewPayment, previewOther)))
    )
}

@Preview(name = "Ручной список платежей, выбрано", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ManualPaymentPreview() {
    OfferPreview(review(manual = true).copy(page = PlanOfferPage.Manual, groupKey = "EXPENSE:internet"))
}

@Preview(name = "Ручной список пуст")
@Composable
private fun EmptyManualPreview() {
    OfferPreview(OperationPlanLinkState(PlanOfferPage.Manual, context = localContext(emptyList())))
}

@Preview(name = "Часть оплаты, крупный текст", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Часть оплаты, широкий экран", widthDp = 840, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PartialOfferPreview() {
    OfferPreview(
        review(
            plan = previewPayment.copy(
                name = "Интернет и обслуживание домашней сети",
                plannedAmount = "5000",
                remainingAmount = "3000"
            ),
            manual = true
        )
    )
}

@Preview(name = "Близкая сумма")
@Composable
private fun NearOfferPreview() {
    OfferPreview(
        review(
            previewExpense.copy(amount = "4850", netAmount = "4850"),
            previewPayment.copy(plannedAmount = "5000", remainingAmount = "5000")
        )
    )
}

@Preview(name = "Сумма больше остатка")
@Composable
private fun OverpaymentPreview() {
    OfferPreview(review(previewExpense.copy(amount = "1320", netAmount = "1320")))
}

@Preview(name = "Плановый доход")
@Preview(name = "Плановый доход, тёмная тема", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeOfferPreview() {
    OfferPreview(
        review(
            previewExpense.copy(type = "INCOME", amount = "65000", netAmount = "65000", categoryName = "Зарплата"),
            previewPayment.copy(
                target = PlanLinkTarget("INCOME", "salary", dueOn = "2026-11-16"),
                name = "Зарплата",
                plannedAmount = "65000",
                remainingAmount = "65000"
            )
        )
    )
}

@Preview(name = "Загрузка")
@Composable
private fun LoadingOfferPreview() {
    OfferPreview(review().copy(context = null))
}

@Preview(name = "Связываем")
@Composable
private fun SavingOfferPreview() {
    OfferPreview(review().copy(saving = true))
}

@Preview(name = "Ошибка, повторить")
@Composable
private fun FailedOfferPreview() {
    OfferPreview(review().copy(failure = PlanOfferError.Save))
}

@Preview(name = "План изменился")
@Composable
private fun ChangedOfferPreview() {
    OfferPreview(review().copy(context = localContext(listOf(previewPayment.copy(remainingAmount = "1000")))))
}

@Preview(name = "План недоступен")
@Composable
private fun UnavailableOfferPreview() {
    OfferPreview(review().copy(context = localContext(emptyList())))
}

private fun localContext(
    plans: List<SuggestedPlan>
) = OperationPlanLinkContext(previewExpense, OperationPlanLinks(plans))

private fun review(
    operation: PlanLinkOperation = previewExpense,
    plan: SuggestedPlan = previewPayment,
    manual: Boolean = false
) = OperationPlanLinkState(
    PlanOfferPage.Review,
    context = OperationPlanLinkContext(operation, OperationPlanLinks(listOf(plan))),
    selected = PlanOfferSelection(operation, plan, manual)
)

@Composable
private fun OfferPreview(
    state: OperationPlanLinkState,
    modifier: Modifier = Modifier
) {
    FillinTheme { Surface { OperationPlanLinkContent(state, {}, {}, {}, {}, {}, {}, modifier) } }
}

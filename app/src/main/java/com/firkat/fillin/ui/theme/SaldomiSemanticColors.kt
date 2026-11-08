package com.firkat.fillin.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

private val categoryInitialPalette = listOf(
    Color(0xFFC62828), Color(0xFFAD1457), Color(0xFF8E24AA), Color(0xFF673AB7),
    Color(0xFF3949AB), Color(0xFF1565C0), Color(0xFF0077A3), Color(0xFFC2410C),
)

@Immutable
data class SaldomiSemanticColors(
    val forecastPace: Color,
    val forecastWarning: Color,
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,

    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,

    val transfer: Color,
    val onTransfer: Color,
    val transferContainer: Color,
    val onTransferContainer: Color,
) {
    internal val categoryInitialColors get() = categoryInitialPalette
    internal val onCategoryInitial get() = Color.White
}

val SaldomiLightSemanticColors = SaldomiSemanticColors(
    forecastPace = Color(0xFF356C75), forecastWarning = Color(0xFF806000),
    income = Color(0xFF187347),
    onIncome = Color.White,
    incomeContainer = Color(0xFFDDF5E6),
    onIncomeContainer = Color(0xFF105031),

    expense = Color(0xFFB63B46),
    onExpense = Color.White,
    expenseContainer = Color(0xFFFCE7E9),
    onExpenseContainer = Color(0xFF862832),

    transfer = Color(0xFF326BA8),
    onTransfer = Color.White,
    transferContainer = Color(0xFFE1EDFF),
    onTransferContainer = Color(0xFF214F80),
)

val SaldomiDarkSemanticColors = SaldomiSemanticColors(
    forecastPace = Color(0xFFAFD6DC), forecastWarning = Color(0xFFFFCB47),
    income = Color(0xFF7EE2AC),
    onIncome = Color(0xFF103D29),
    incomeContainer = Color(0xFF184B35),
    onIncomeContainer = Color(0xFF9BF0C0),

    expense = Color(0xFFFFB3B9),
    onExpense = Color(0xFF5C1723),
    expenseContainer = Color(0xFF602C35),
    onExpenseContainer = Color(0xFFFFC9CD),

    transfer = Color(0xFFA7CAFF),
    onTransfer = Color(0xFF153A65),
    transferContainer = Color(0xFF243F64),
    onTransferContainer = Color(0xFFC7DFFF),
)

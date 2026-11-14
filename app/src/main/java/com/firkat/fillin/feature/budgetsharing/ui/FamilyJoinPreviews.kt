package com.firkat.fillin.feature.budgetsharing.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
private fun JoinPreview(
    code: String = "",
    busy: Boolean = false,
    error: String? = null,
    name: String? = null
) {
    FillinTheme {
        FamilyBudgetJoinContent(AuthSession.Authenticated(AuthUser("preview", null, null)), code, busy, error, name, {
        }, {})
    }
}

@Preview(name = "Join empty")
@Preview(name = "Join dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun JoinEmptyPreview() {
    JoinPreview()
}

@Preview(name = "Join filled")
@Composable
private fun JoinFilledPreview() {
    JoinPreview("ABCDE-23456-789AB-CDEFG")
}

@Preview(name = "Join busy")
@Composable
private fun JoinBusyPreview() {
    JoinPreview("ABCDE-23456-789AB-CDEFG", busy = true)
}

@Preview(name = "Join error")
@Composable
private fun JoinErrorPreview() {
    JoinPreview("ABC", error = "Введите код приглашения полностью: 20 букв и цифр.")
}

@Preview(name = "Join success")
@Composable
private fun JoinSuccessPreview() {
    JoinPreview(name = "Большой семейный бюджет")
}

@Preview(name = "Join large text", widthDp = 320, fontScale = 2f)
@Composable
private fun JoinLargePreview() {
    JoinPreview("ABCDE-23456-789AB-CDEFG")
}

@Preview(name = "Join wide", widthDp = 840)
@Composable
private fun JoinWidePreview() {
    JoinPreview("ABCDE-23456-789AB-CDEFG")
}

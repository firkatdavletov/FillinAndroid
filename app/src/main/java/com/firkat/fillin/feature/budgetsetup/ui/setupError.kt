package com.firkat.fillin.feature.budgetsetup.ui

import androidx.annotation.StringRes
import com.firkat.fillin.R
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure

@StringRes
internal fun accessErrorResource(
    code: String
): Int? = when (code) {
    "local_budget_limit" -> R.string.local_budget_limit
    "device_access_denied" -> R.string.app_access_revoked
    else -> null
}

fun setupError(
    error: Exception
): String = when ((error as? BudgetConnectionFailure)?.code) {
    "local_budget_limit", "device_access_denied" -> error.message.orEmpty()
    "not_local_budget" -> "Этот бюджет уже связан с облаком. Подключите его из списка бюджетов аккаунта."
    "creation_pending" -> "Создание предыдущего бюджета ещё не завершено. Продолжите его создание."
    "creation_conflict" -> "Сервер отклонил повтор создания. Запрос сохранён; обратитесь к разработчику."
    "invalid_creation" -> "Проверьте название и начальный баланс бюджета."
    "budget_not_found" -> "Бюджет не найден. Обновите список бюджетов аккаунта."
    "budget_already_exists" -> "Не удалось подключить бюджет. Локальные данные сохранены; выберите бюджет аккаунта."
    "email_not_verified" -> "Подтвердите email в разделе профиля. Локальные данные сохранены."
    "unauthorized", "access_denied" -> "Нет доступа к облачному бюджету. Проверьте аккаунт или войдите снова."
    "sign_in_required" -> "Войдите в аккаунт и выберите доступный бюджет."
    "account_changed" -> "Аккаунт изменился. Выберите бюджет текущего аккаунта."
    "server_not_configured" -> "Серверное подключение не настроено в этой сборке."
    "unsupported_legacy_queue" -> "Этот бюджет использует прежний формат обмена. Данные сохранены; подключение остановлено."
    "missing_api_token" -> "Войдите в аккаунт и подключите бюджет к аккаунту. Локальные данные сохранены."
    "scope_changed" -> "Контекст бюджета изменился. Обмен остановлен; локальные данные сохранены."
    else -> if (error is IllegalArgumentException ||
        error is java.time.DateTimeException
    ) {
        "Не удалось обработать данные бюджета."
    } else {
        "Не удалось подключиться к серверу. Проверьте интернет и повторите попытку."
    }
}

package com.firkat.fillin.feature.budgetsetup.ui

import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure

fun setupError(error: Exception): String =
    when ((error as? BudgetConnectionFailure)?.code) {
        "not_local_budget" -> "Этот бюджет уже связан с облаком. Подключите его из списка бюджетов аккаунта."
        "creation_pending" -> "Создание предыдущего бюджета ещё не завершено. Продолжите его создание."
        "creation_conflict" -> "Сервер отклонил повтор создания. Запрос сохранён; обратитесь к разработчику."
        "invalid_creation" -> "Проверьте название, начальный баланс и дату бюджета."
        "budget_not_found" -> "Бюджет не найден. Обновите список бюджетов аккаунта."
        "budget_already_exists" -> "Не удалось подключить бюджет. Локальные данные сохранены; выберите бюджет аккаунта."
        "unauthorized", "access_denied" -> "Нет доступа к облачному бюджету. Проверьте Google-аккаунт или войдите снова."
        "sign_in_required" -> "Войдите через Google и выберите доступный бюджет."
        "account_changed" -> "Аккаунт изменился. Выберите бюджет текущего аккаунта."
        "server_not_configured" -> "Серверное подключение не настроено в этой сборке."
        "unsupported_legacy_queue" -> "Этот бюджет использует прежний формат обмена. Данные сохранены; подключение остановлено."
        "missing_api_token" -> "Войдите через Google и подключите бюджет к аккаунту. Локальные данные сохранены."
        "scope_changed" -> "Контекст бюджета изменился. Обмен остановлен; локальные данные сохранены."
        else -> if (error is IllegalArgumentException || error is java.time.DateTimeException) "Проверьте баланс и дату начала учёта." else "Не удалось подключиться к серверу. Проверьте интернет и повторите попытку."
    }
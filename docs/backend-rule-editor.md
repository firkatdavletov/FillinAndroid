# Полное редактирование правил

Android сохраняет форму локально, затем отправляет очередь существующим sync API.
Для облачного выпуска необходима серверная поддержка следующих изменений.

`CREATE_RULE` и `UPDATE_RULE` получают необязательное `ruleName` (непустое после trim,
до 160 символов). Срез `RECURRING_RULE` возвращает это поле. Отсутствующее название
на старых записях отображается как название категории.

`UPDATE_RULE` с `replaceDetails: true` заменяет categoryId, amount, frequency,
scheduleValue, startsOn и ruleName. Для ONCE scheduleValue отсутствует; MONTHLY —
1–31, WEEKLY — ISO 1–7. Обязательна категория расходов. Без replaceDetails сохраняется
семантика старого изменения только суммы; поля полного снимка не означают изменение расписания.

Изменения действуют с authoredOn (Asia/Almaty). Неоплаченные SCHEDULED экземпляры
начиная с этой даты обновляются: совпадающие даты сохраняют ID, исчезнувшие отменяются,
новые создаются с уникальностью (ruleId, dueOn). Прошлые, частично оплаченные, оплаченные
и закрытые экземпляры и связи с проводками сохраняются. Новые даты до authoredOn не
генерируются. Месячный день ограничивается последним днём месяца. Повторная доставка
requestId не создаёт дублей. Старая прошедшая дата начала разрешена, если не изменена;
новая дата не раньше authoredOn и начала учёта.

Доходы используют существующие incomeName/incomeFrequency/incomeActive и команды
CREATE/UPDATE_INCOME(_RULE). Редактирование прежнего отдельного EXPECTED_INCOME использует UPDATE_INCOME_RULE
и выделяет правило с тем же ID, в том числе для ONCE: исходный экземпляр сохраняет
ID и связи. Это позволяет оставить прошлые и частично выполненные платежи неизменными.
Переход разового дохода на регулярный сохраняет ID и связи;
обратный переход регулярного правила использует UPDATE_INCOME_RULE с ONCE.

## Согласование облачной поддержки доходов

`GET /api/v4/budgets/{budgetId}` должен объявлять `incomeRuleEditingV1` в `capabilities`
после реализации метаданных incomeName/incomeFrequency/incomeActive, команд
CREATE/UPDATE/DELETE_INCOME_RULE и их round-trip через sync changes. Android передаёт
`X-Fillin-Capabilities: confirmedPlanLinkingV1,incomeRuleEditingV1,dailyLimitPlanningV1`
в запросах бюджета, changes и commands. Поддержка полного `dailyLimitPlanningV1`
также разрешает редактирование доходов для совместимости.

Пример ответа сервера, реализующего редактор и подтверждаемые связи, но не остатки:

```json
{"scope":{"householdId":"…","memberId":"…","role":"OWNER","accountingStartedAt":"…","timeZone":"Asia/Almaty","currency":"KZT"},"capabilities":["confirmedPlanLinkingV1","incomeRuleEditingV1"]}
```

`incomeRuleEditingV1` покрывает редактор правил. Для CONFIRM_REMAINDER_INCOME и его полей
Android проверяет `planPaymentRemaindersV1` или `dailyLimitPlanningV1`. Закрытие платежа
с проверкой `expectedAppliedAmount` требует `planPaymentRemaindersV1`.
Связи с доходами, включая ruleId/scheduledOn, требуют `confirmedPlanLinkingV1`.
Объявлять `dailyLimitPlanningV1` при реализации только редактора нельзя.

Android проверяет возможности до подготовки запроса к отправке, использует один ответ
чтения бюджета за обмен и заново проверяет его при следующем обмене. Отсутствие нужной
capability останавливает отправку с `backend_update_required`, сохраняя requestId,
intent и уже зафиксированный wirePayload. Существующие команды без новой метаинформации
не требуют `incomeRuleEditingV1`. Номер HTTP API v4 сам по себе не означает поддержку редактора.

Серверную реализацию и развёртывание этот Android PR не включает. Требуется проверить
приём новых полей, round-trip среза и объявление `incomeRuleEditingV1` до облачного выпуска.

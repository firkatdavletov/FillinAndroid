# Saldomi · Design preview

Откройте `index.html` в браузере двойным щелчком. Сервер, сборка и интернет не нужны.

- `index.html` — цвета, контраст, типографика, компоненты, состояния и геометрия.
- `screen-categories.html` — список категорий, пустое состояние, загрузка, ошибки, обновление, режим просмотра и статусы облака.
- `screen-create-category.html` — создание категории.
- `screen-edit-category.html` — редактирование, OTHER, доход, сохранение, ошибки.
- `sheets.html` — каталог иконок и подтверждение удаления.

Переключатель темы находится в меню. Выбор сохраняется при переходах, если браузер разрешает localStorage для локальных файлов. Панели справа управляют исключительно состояниями превью. Данные приложения не читаются и не изменяются.

Цвета: `tokens/colors.json`. Сгенерированные CSS-переменные: `assets/theme.css`. Контраст: `contrast-report.md`. Соответствие исходникам и ограничения проверки: `traceability.md`.

Проверены 5 страниц, локальные ссылки и 120 цветовых пар. Проверка синтаксиса JavaScript пройдена. Визуальная проверка не выполнена: браузерный хост недоступен. Проверка токенов не является полной проверкой доступности.

## Обновление темы Android

`tokens/colors.json` служит источником цветов HTML и Android. После изменения JSON из корня проекта запустите:

```bash
python3 scripts/generate-material-colors.py
```

Генератор создаёт `app/src/main/java/com/firkat/fillin/ui/theme/GeneratedMaterialColors.kt`. `Theme.kt` передаёт сгенерированные светлую и тёмную `ColorScheme` в `MaterialTheme`. Например, `textPrimary` становится `onBackground` и `onSurface`, `surfaceAlt` становится `surfaceVariant`, `danger` становится `error`, `borderStrong` становится `outline`. Все исходные роли доступны из Compose через `MaterialTheme.saldomiDesignColors`. Уже существующий `MaterialTheme.saldomiColors` для финансовых состояний остаётся отдельной семантической палитрой.

Для проверки актуальности сгенерированного Kotlin без записи файла используйте `python3 scripts/generate-material-colors.py --check`.

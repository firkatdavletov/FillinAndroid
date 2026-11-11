# T03 — серверная авторизация Firebase

## Доступно в сборке

После Google-входа вкладка «Облако» получает Firebase ID token из SDK и вызывает
`POST /api/v4/auth/firebase`. При восстановленной Firebase-сессии профиль запрашивается
без повторного окна Google. Успех отображается как «Аккаунт подтверждён сервером».
Ошибка сети или сервера сохраняет Google-сессию и предлагает «Повторить проверку».
При окончательном отказе авторизации предлагается выйти и войти снова.

Используется Firebase ID token, полученный через `FirebaseUser.getIdToken(false)`;
Google credential из окна входа на сервер не отправляется. После первого `401`
выполняется `getIdToken(true)` и ровно один повтор. Другие ответы автоматически
не повторяются. Токены не сохраняются приложением и не логируются.
Ответ для другого UID или прежнего аккаунта после выхода/смены аккаунта отбрасывается.
HTTP redirects для этого клиента отключены.

Сервер FinanceConsultante использует Firebase Admin Java SDK 9.11.0 и
`verifyIdToken(token, true)`: проверяет подпись, срок, issuer/audience, отзыв токена
и блокировку/удаление пользователя. Принимается Google provider. Проверенный UID
сохраняется в `firebase_user` (V23); `UNIQUE(firebase_uid)` и атомарный upsert
возвращают прежний UUID при повторном и одновременном первом входе.
Имя и email берутся из проверенных claims. Собственный JWT не выдаётся.

Вход не создаёт членство и не меняет бюджет или очередь. Привязка бюджетов — T05;
старый мобильный API и статический токен остаются до следующих этапов плана.

## Адрес сервера

По умолчанию Android обращается к развёрнутому серверу Railway:

`https://cheerful-art-production-c4f9.up.railway.app/api/v4/auth/firebase`.

Для обычной сборки дополнительная настройка адреса не нужна. Для проверки с другим
сервером можно задать параметр сборки `-PfirebaseApiBaseUrl=https://test-server.example/`
или `FIREBASE_API_BASE_URL` в `local.properties` (исключён из Git). Параметр сборки
имеет приоритет. Пустое значение отключает проверку серверного профиля.
HTTPS обязателен, кроме localhost с явным портом в debug. Release не получает localhost HTTP.

## Настройка локального тестового сервера

Для локальных проверок в Android `local.properties`:

```properties
FIREBASE_API_BASE_URL=http://127.0.0.1:18080/
```

Для Pixel выполнить `adb reverse tcp:18080 tcp:18080`. После переподключения устройства
его нужно повторить. Для приёмки T03 использовались отдельный сервер на `127.0.0.1:18080`
и Docker PostgreSQL `fillin-t03-postgres` на `127.0.0.1:55433`, БД `fillin_t03`.
После локальных проверок удалить переопределение адреса, чтобы вернуться к Railway.

Сервер включается через `FIREBASE_AUTH_ENABLED=true`, `FIREBASE_PROJECT_ID` и
`GOOGLE_APPLICATION_CREDENTIALS` с путём к существующему service account JSON вне Git.
Ключ проверен локально для проекта `fillin-77e97`, права файла — `0600`.
Firebase credentials и токены не нужно копировать в Android, документацию или чат.
Команда воспроизведения сервера приведена в FinanceConsultante `docs/firebase-authentication.md`.

## Проверки (2026-10-20)

- Android: debug APK и instrumentation APK собраны; 176 unit-тестов успешны.
- Pixel 10 Pro XL, Android 17 / API 37.1: 28 тестов успешны. Новые проверки используют
  production Retrofit/repository с синтетическими токенами и управляемыми HTTP-ответами:
  первый `401` → новый токен → успех; повторный `401` → остановка; `503` → ошибка → ручной повтор.
- FinanceConsultante: `test`, `check`, `bootJar` успешны; 315 unit и 265 integration-тестов,
  Docker/Testcontainers PostgreSQL 17.11, ktlint и Detekt. Проверены конкуренция,
  HTTP-контракт, запрет записи при отказе и миграция V22→V23 с сохранением прежних данных.
- На запущенном сервере с настоящим Firebase Admin запрос без токена и неверный
  статический токен возвращают `401`. Ошибки expired/revoked/disabled проверены
  отдельно имитацией ответов Admin SDK; вызов SDK обязательно передаёт `checkRevoked=true`.
- Пользователь подтвердил настоящий Google-вход и «Аккаунт подтверждён сервером» на Pixel.
  Затем отдельно прошли 2 opt-in live-теста без fake credentials/verifier: восстановленная
  Firebase-сессия получает одинаковый серверный UUID при двух запросах; управляемый первый
  `401` вызывает настоящий `getIdToken(true)`, и повтор успешно проверяется настоящим Admin SDK.
  В тестовой БД одна запись пользователя без дублей. Реальные UID/email и токены в вывод не попали.
- До установки создана приватная резервная копия постоянных данных Pixel.
  Копия восстановлена в отдельный каталог и прошла SQLite integrity check.
  После первой установки и тестов логические данные бюджета и очередей совпали;
  изменился только `operation_sync_state.lastSyncedAt`.
- Первый UI-прогон при выключенном/заблокированном экране завершился четырьмя ошибками
  отсутствия Compose hierarchy. После разблокировки пользователем весь набор прошёл.

Настоящий Google-вход, восстановление сессии, серверный профиль и обновление токена проверены.
Реальный отзыв Firebase-сессии не выполнялся; соответствующие SDK-ошибки проверены отдельно.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.firkat.fillin.feature.auth.ServerAuthenticationDeviceTest,com.firkat.fillin.feature.auth.AuthAccountSectionTest,com.firkat.fillin.feature.sync.BudgetSyncPanelTest,com.firkat.fillin.FirebaseConfigurationTest,com.firkat.fillin.feature.budgetsetup.BudgetSetupUiTest,com.firkat.fillin.feature.budgetsetup.CloudBudgetLifecycleTest,com.firkat.fillin.feature.sync.BudgetOfflinePersistenceTest \
  com.firkat.fillin.debug.test/com.firkat.fillin.FillinTestRunner
```

`connectedDebugAndroidTest` на устройстве с пользовательскими данными не использовать:
он удаляет приложение при завершении. Экран Pixel должен быть разблокирован.

Отдельный живой прогон после настоящего Google-входа, только с тестовым сервером:

```bash
adb shell am instrument -w \
  -e class com.firkat.fillin.feature.auth.FirebaseServerLiveTest \
  -e liveFirebaseAuth true \
  com.firkat.fillin.debug.test/com.firkat.fillin.FillinTestRunner
```

Без явного `liveFirebaseAuth=true` эти два теста пропускаются: обычные тесты не должны
использовать настоящий аккаунт. В live-прогоне бюджетная синхронизация выключена test runner.

## Ручная приёмка

1. Открыть «Облако», войти в тестовый Google-аккаунт. Дождаться «Аккаунт подтверждён сервером».
2. Перезапустить приложение и снова открыть «Облако». Серверный UUID пользователя должен
   остаться прежним; проверить это в тестовой БД без копирования UID/email в логи.
3. Остановить локальный сервер, полностью перезапустить приложение и открыть «Облако».
   Убедиться в понятной ошибке; после запуска сервера нажать «Повторить проверку».
4. Отозвать сессию только выделенного тестового пользователя в Firebase. Старый токен
   должен получить `401`; повторный вход должен восстановить профиль с тем же серверным UUID.
5. Выйти во время загрузки профиля: ответ прежней сессии не должен появиться на экране.

## Источники

- [Firebase Admin setup](https://firebase.google.com/docs/admin/setup)
- [Проверка ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Отзыв сессий](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Firebase Admin Java releases](https://firebase.google.com/support/release-notes/admin/java)

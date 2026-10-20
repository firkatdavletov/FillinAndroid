# T01 — настройка Firebase

## Текущее состояние

Android-часть этапа реализована и установлена на Pixel 10 Pro XL (Android 17 / API 37.1).
Этап ещё не принят: серверный приватный ключ не создан, release-сертификат отсутствует,
ручная проверка существующего бюджета требует восстановления после описанного ниже инцидента.
Вход через Google и изменение облачной синхронизации относятся к следующим задачам.

Проект Firebase: `fillin-77e97` (Fillin), план Spark.
Android application ID: `com.firkat.fillin`.
Google Analytics и Gemini при создании проекта выключены; Analytics SDK не подключается.
Google provider включён; публичное имя приложения — Fillin, адрес поддержки выбран пользователем.
Android-приложение зарегистрировано, оба debug-отпечатка добавлены;
локальный `app/google-services.json` содержит Google Web OAuth client.

## Android

Подключены:

- Google Services Gradle plugin `4.5.0`;
- Firebase BoM `35.0.2` и `firebase-auth`;
- Credential Manager и Play services adapter `1.6.0`;
- Google ID `1.2.1`.

Актуальный `google-services.json` нужно загрузить **после** включения Google provider
и поместить в `app/google-services.json`. Файл исключён из Git.
Google Services plugin применяется только при наличии этого файла;
`BuildConfig.FIREBASE_CONFIGURED` отражает его наличие при сборке.
Без файла приложение по-прежнему собирается для локальной работы, а Firebase не инициализируется.
В конфигурации для Google-входа должен присутствовать Web OAuth client
(`default_web_client_id` в сгенерированных ресурсах).

Firebase Auth не вызывает `signInAnonymously`: локальный бюджет не является Firebase-пользователем.
Существующий статический облачный токен пока сохраняется до этапа T12.

## Сертификаты

Текущий debug-сертификат получен командой `./gradlew :app:signingReport`:

- SHA-1: `BF:EB:80:A0:C7:4C:D1:AE:53:B4:52:F0:9B:22:CF:F1:B3:85:8E:26`.
- SHA-256: `80:A7:FB:0F:16:2D:68:B9:FE:76:6D:47:86:E2:68:3A:A6:9A:44:68:E9:5D:82:86:BE:73:64:2A:88:CF:FD:07`.

Release signing в Gradle сейчас не настроен. Перед release-проверкой необходимо добавить
отпечатки фактического release/app signing сертификата из используемого хранилища или Google Play Console.
Debug-сертификат не заменяет release-сертификат.

## Серверные credentials

Сервисный ключ Firebase Admin не должен попадать в Android, Git, логи или чат.
Для FinanceConsultante потребуется project ID и Application Default Credentials.
Для локального сервера предполагается файл `~/.config/fillin/firebase-adminsdk.json` вне обоих репозиториев
с правами `0600`; переменная `GOOGLE_APPLICATION_CREDENTIALS` содержит только путь к нему.
На хостинге файл подключается как secret file. Серверная реализация начинается в T03.

Создание нового приватного сервисного ключа — отдельное действие в консоли с подтверждением;
наличие Firebase Android-конфигурации не означает наличие серверных credentials.

## Проверка

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e package com.firkat.fillin.feature.budgetsetup \
  com.firkat.fillin.test/com.firkat.fillin.FillinTestRunner
adb shell am instrument -w \
  -e class com.firkat.fillin.FirebaseConfigurationTest,com.firkat.fillin.feature.sync.BudgetOfflinePersistenceTest \
  com.firkat.fillin.test/com.firkat.fillin.FillinTestRunner
```

Для основной установки с пользовательскими данными сначала нужна проверенная резервная копия.
`connectedDebugAndroidTest` на этом устройстве удалил установленное приложение вместе с его данными;
на основной установке эту команду не использовать. Предпочтительно отдельное тестовое устройство/эмулятор.
Обычная установка через `adb install -r` и ручной запуск runner после тестов оставили приложение установленным.

`FirebaseConfigurationTest` проверяет реальную инициализацию SDK и наличие Google Web OAuth client
без входа в аккаунт. Сборка без файла проверяет отсутствие автоматической инициализации Firebase.
Существующие budget setup/lifecycle тесты используют тестовые базы и fake API;
`FillinTestRunner` отключает автоматическую синхронизацию с production.

Ручная приёмка: открыть сохранённый локальный бюджет; без интернета создать локальный бюджет
на чистой тестовой установке; перезапустить приложение и убедиться в сохранении данных.
Основную установленную базу ради этой проверки не очищать.

## Результаты T01 (2026-10-20)

- Сборка и 153 unit-теста успешны как без Firebase-конфигурации, так и с реальной конфигурацией проекта.
- На Pixel вручную пройдены 9 budget setup/lifecycle тестов и 9 SDK/offline persistence тестов:
  всего 18 тестов, без ошибок.
- Первоначальные UI-тесты выявили несовместимость Espresso 3.5.1 с Android 17
  (`InputManager.getInstance`). После обновления Espresso до 3.7.0 и AndroidX JUnit до 1.3.0 UI-тесты прошли.
- Новый тест подтвердил инициализацию реального Firebase App/Auth и наличие Google Web OAuth client.
- Google-вход ещё не реализован и не проверялся (T02).
- Реальный авиарежим и ручная приёмка пользователем не подтверждены; автоматические проверки
  локального бюджета используют fake API без сетевых обращений.

### Инцидент при первоначальном запуске Gradle device-тестов

До запуска на Pixel существовала база `operations-*.db`. После Gradle device-запуска
пакет приложения оказался удалён; после переустановки прежняя база отсутствовала.
Резервная копия перед запуском не была создана. Доступных локальных копий и Android restore sets не найдено.
Серверный бюджет проверен GET-запросом и доступен для повторного подключения через
«Подключиться к существующему». Серверные данные не изменялись.
Восстановление неотправленных локальных изменений подтвердить невозможно.
Поэтому сохранность первоначальной пользовательской базы не считается прошедшей проверкой.

## Официальные источники

- [Firebase Android setup](https://firebase.google.com/docs/android/setup)
- [Google Sign-In with Firebase](https://firebase.google.com/docs/auth/android/google-signin)
- [Credential Manager releases](https://developer.android.com/jetpack/androidx/releases/credentials)
- [Google ID releases](https://developers.google.com/identity/android-credential-manager/releases)

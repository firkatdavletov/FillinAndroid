# T02 — вход и выход через Google

## Реализовано

Вход выполняется на отдельном `AuthScreen` с существующим `AuthContent`. Во вкладке
«Облако» отображается кнопка «Авторизация» или «Выйти из аккаунта» в зависимости от
Firebase-сессии. После выбора аккаунта Credential Manager возвращает Google ID token,
который передаётся Firebase Authentication. Переходы между Launch, Auth и Main описаны
в [глобальной навигации](navigation.md).

Firebase SDK сохраняет сессию и восстанавливает её после перезапуска; приложение не хранит
собственную копию ID token или refresh token. Выход вызывает Firebase `signOut` и
Credential Manager `clearCredentialState`. Следующий вход предлагает выбор аккаунта без автоматического выбора.

Отмена выбора аккаунта не отображается как ошибка. Повторные нажатия не запускают второй запрос.
Уход с экрана отменяет незавершённый выбор; результат старого запроса не может завершить новый.
Firebase-обмен после получения Google credential выполняется в ViewModel, без сохранения Activity.

При отсутствии `google-services.json` локальная сборка продолжает работать, а вход отключён.
Ошибки настройки, сети и credentials показаны понятным текстом без содержимого токенов.
Если сброс Credential Manager не удался, Firebase-сессия всё равно остаётся завершённой.

Feature `auth` разделена на domain/data/ui и подключена через Koin. AuthRepository не зависит
от базы бюджета, BudgetSetupRepository или синхронизации. Вход/выход не создаёт, не подключает,
не удаляет бюджет и не меняет его очереди. Серверный Firebase Admin key для T02 не нужен.
Серверная авторизация реализована отдельно в [T03](firebase-server-authentication.md).
Привязка к бюджетам остаётся в следующих этапах.

## Автоматические проверки

- Unit: восстановленное состояние, вход после получения credential, двойное нажатие,
  отмена/повтор, запоздалый результат, ошибка сети, сериализация входа/выхода,
  ошибка сброса provider и отсутствие Firebase-конфигурации.
- Compose на Pixel: вход с fake Google launcher/repository, профиль и выход,
  отмена, уход/возврат на экран без повторного picker, восстановленный профиль,
  отсутствие конфигурации.
- Регрессия: экран синхронизации, Firebase SDK wiring из T01, настройка локального бюджета,
  жизненный цикл облака с fake API и офлайн-сохранение.

Выполнено: сборка debug APK и тестового APK, 161 unit-тест и 25 тестов на Pixel 10 Pro XL
(Android 17 / API 37.1). Все прошли. Проверка Google provider с реальным аккаунтом
не подменяется fake-тестами и фиксируется отдельно при ручной приёмке.

В обычном запуске установленной сборки проверено открытие вкладки «Облако»:
реальные зависимости Koin/Firebase загрузились, показаны локальный режим и кнопка входа.
Настоящий Google-вход, сохранение его сессии после перезапуска и смена двух аккаунтов
пока ожидают ручной приёмки на устройстве.

Перед установкой сделана приватная резервная копия данных устройства вне репозитория.
Установка выполнена через `adb install -r`, запуск тестов — вручную через instrumentation runner.
После тестов SHA-256 базы `operations-*.db` и WAL совпали с копией до установки;
исходные данные и очереди не изменились.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.firkat.fillin.feature.auth.AuthAccountSectionTest,com.firkat.fillin.feature.sync.BudgetSyncPanelTest,com.firkat.fillin.FirebaseConfigurationTest,com.firkat.fillin.feature.budgetsetup.BudgetSetupUiTest,com.firkat.fillin.feature.budgetsetup.CloudBudgetLifecycleTest,com.firkat.fillin.feature.sync.BudgetOfflinePersistenceTest \
  com.firkat.fillin.test/com.firkat.fillin.FillinTestRunner
```

На основной установке с пользовательскими данными сначала нужна проверенная резервная копия.
Gradle `connectedDebugAndroidTest` не использовать: при T01 он удалил приложение с данными.

## Ручная приёмка на устройстве

1. Открыть существующий бюджет и вкладку «Облако».
2. Нажать «Авторизация», затем «Продолжить с Google», выбрать аккаунт и убедиться,
   что открылась вкладка «Облако» с кнопкой выхода.
3. Полностью закрыть приложение и открыть снова: аккаунт должен сохраниться, picker не открывается.
4. Нажать «Выйти из аккаунта»: открывается AuthScreen; бюджет и неотправленные операции остаются.
5. Повторить вход с другим Google-аккаунтом; бюджет не перепривязывается автоматически.
6. Выйти, открыть выбор Google-аккаунта и отменить: остаётся локальный режим без ошибки.

## Источники

- [Firebase Authentication: Google Sign-In](https://firebase.google.com/docs/auth/android/google-signin)
- [Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)

package com.firkat.fillin.feature.budgetsetup

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetsetup.data.RoomLocalBudgetCreator
import com.firkat.fillin.feature.budgetsetup.ui.*
import com.firkat.fillin.feature.budgettransfer.BudgetTransferScreenTest.RecordingPicker
import com.firkat.fillin.feature.budgettransfer.data.BudgetCsvCodec
import com.firkat.fillin.feature.budgettransfer.data.BudgetSnapshotValidator
import com.firkat.fillin.feature.budgettransfer.data.RoomBudgetSnapshot
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferViewModel
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BudgetSetupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
    private val env = BudgetConnectionDeviceTest.Environment(server)
    private val models = ViewModelStore()
    private lateinit var model: BudgetSetupViewModel
    private var backs = 0
    private var signIns = 0
    private var completed = 0
    private fun newModel(
        saved: SavedStateHandle = SavedStateHandle(),
        access: AppAccess? = null
    ) = BudgetSetupViewModel(
        env.auth,
        env.connection,
        env.creation,
        RoomLocalBudgetCreator(env.auth, env.registry, env.manager, BudgetDatabaseFactory(env.context), env.koin.get()),
        env.sharing,
        saved,
        dispatcher = kotlinx.coroutines.Dispatchers.IO,
        access = access,
        observeReadyBudgetExists = { uid ->
            env.registry.observeReadyBudgetExists(uid, BudgetDatabaseFactory(env.context))
        }
    ).also {
        models.put("setup-${models.keys().size}", it)
    }
    private fun show(
        signedIn: Boolean = false,
        compact: Boolean = false,
        access: AppAccess? = null,
        fromProfile: Boolean = false,
        picker: RecordingPicker? = null,
        restoration: StateRestorationTester? = null
    ) {
        if (signedIn) env.auth.login("owner")
        runBlocking { env.manager.restore() }
        model = newModel(access = access)
        val transfer = picker?.let {
            BudgetTransferViewModel(env.koin.get(), SavedStateHandle()).also { models.put("transfer", it) }
        }
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val content: @Composable () -> Unit = {
            FillinTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 2f else 1f)) {
                    Box(if (compact) Modifier.fillMaxWidth().height(300.dp) else Modifier.fillMaxSize()) {
                        val pickerOwner = picker ?: compose.activity
                        CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                            BudgetSetupScreen(
                                model, { backs++ }, { signIns++ }, { completed++ }, {},
                                fromProfile = fromProfile,
                                transferViewModel = transfer
                            )
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitUntil(5000) { model.state.value.initialized && !model.state.value.loading }
    }
    private fun waitForCompletion() {
        try {
            compose.waitUntil(30000) { completed == 1 }
        } catch (
            e: Throwable
        ) {
            throw AssertionError("Creation did not complete: ${model.state.value}", e)
        }
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
        models.clear()
        env.close()
    }

    @Test
    fun firstSetupCanCancelRetryAndRestoreCsv() {
        val picker = RecordingPicker()
        val restoration = StateRestorationTester(compose)
        val file = File.createTempFile("setup-budget", ".csv", env.context.cacheDir)
        try {
            show(picker = picker, restoration = restoration)
            val button = compose.onNodeWithTag("setup_create_from_file")
            compose.waitUntil(5000) { compose.onAllNodesWithTag("setup_create_from_file").fetchSemanticsNodes().isNotEmpty() }
            val name = compose.onNodeWithTag("budget_setup_name")
            name.performScrollTo().performTextReplacement("Черновик")
            closeSoftKeyboard()
            button.performScrollTo().performClick()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)
            compose.onNodeWithTag("setup_create").assertIsNotEnabled()
            compose.runOnIdle { picker.deliver(Activity.RESULT_CANCELED) }
            name.assertTextContains("Черновик")

            button.performClick()
            compose.runOnIdle { picker.deliver(Activity.RESULT_OK, Uri.fromFile(file)) }
            val error = compose.activity.getString(R.string.budget_csv_invalid_row, 1)
            compose.waitUntil(5000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }

            writeBackup(file)
            button.performScrollTo().performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle { picker.deliver(Activity.RESULT_OK, Uri.fromFile(file)) }
            compose.waitUntil(5000) { env.manager.active.value?.profile?.name == "Из копии" }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("setup_create_from_file").fetchSemanticsNodes().isEmpty() }
        } finally {
            file.delete()
        }
    }

    @Test
    fun additionalSetupFromProfileHidesImport() {
        show(fromProfile = true, picker = RecordingPicker())
        compose.onNodeWithTag("setup_create_from_file").assertDoesNotExist()
    }

    private fun writeBackup(file: File) = runBlocking {
        val json = env.koin.get<kotlinx.serialization.json.Json>()
        Room.inMemoryDatabaseBuilder(env.context, OperationDatabase::class.java).build().let { database ->
            try {
                val session = CloudBudgetSession(database, LocalOnlyCloudApi, "", json, {})
                session.createLocal("123.45".toBigDecimal(), session.today)
                val snapshot = RoomBudgetSnapshot(json).read(database, "Из копии")
                file.bufferedWriter().use { BudgetCsvCodec(BudgetSnapshotValidator(json)).write(snapshot, it) }
            } finally {
                database.close()
            }
        }
    }

    @Test fun localCreationStoresNameWithoutNetwork() {
        show()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performTextReplacement("123,45")
        compose.onNodeWithTag("budget_setup_date").assertDoesNotExist()
        closeSoftKeyboard()
        compose.onNodeWithTag(
            "budget_setup_currency"
        ).assertTextContains(compose.activity.getString(R.string.budget_currency_rub))
        compose.onNodeWithTag("budget_setup_currency").performScrollTo().performClick()
        compose.onNode(hasText(compose.activity.getString(R.string.budget_currency_rub)) and isSelected())
            .assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.budget_currency_eur))
            .assertIsNotSelected().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        waitForCompletion()
        assertEquals("Поездка", env.manager.active.value!!.profile.name)
        runBlocking {
            env.database(env.manager.active.value!!.profile) { assertEquals("EUR", it.operations().state()?.currency) }
        }
        assertTrue(server.creations.isEmpty())
    }

    @Test fun offlineCreationDoesNotShowCloudControlsAfterCompletion() {
        val access = object : AppAccess {
            override val mode = MutableStateFlow(AppMode.OfflineFree)
            override val checking = MutableStateFlow(false)
            override fun installationId() = "test"
            override suspend fun refresh(
                manual: Boolean
            ) = false
            override fun revoke() = Unit
        }
        show(access = access)
        compose.onNodeWithTag("setup_back").assertDoesNotExist()
        compose.onNodeWithText("Где хранить бюджет?").assertDoesNotExist()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        waitForCompletion()
        compose.onNodeWithTag("setup_back").assertDoesNotExist()
        compose.onNodeWithText("Где хранить бюджет?").assertDoesNotExist()
    }

    @Test fun invalidAmountShowsFieldErrorAndCanBeCorrected() {
        show()
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performTextReplacement("1.234")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.onNodeWithText(
            "Введите сумму с точностью до двух знаков после запятой."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("budget_setup_balance").performTextReplacement("10")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        waitForCompletion()
    }

    @Test fun switchingStorageKeepsInputAndAsksGuestToSignInOnlyOnCreate() {
        show()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        assertEquals(0, signIns)
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.waitUntil { signIns == 1 }
        assertEquals("Поездка", model.state.value.name)
    }

    @Test fun cloudCreationUsesSameForm() {
        show(signedIn = true)
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performTextReplacement("125,50")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        waitForCompletion()
        assertEquals(BudgetProfileKind.CLOUD, env.manager.active.value!!.profile.kind)
        assertEquals(1, server.creations.size)
        assertEquals("125.50", server.openingBalance)
        assertEquals("RUB", server.creations.values.single().first.currency)
    }

    @Test fun existingBudgetRequiresExplicitSelection() {
        server.budgetExists = true
        show(signedIn = true)
        compose.onNodeWithTag("budget_setup_name").assertDoesNotExist()
        compose.onNodeWithText("Тестовый бюджет · Владелец").performScrollTo().performClick()
        waitForCompletion()
        assertTrue(server.creations.isEmpty())
    }

    @Test fun pendingCreationLocksFieldsAndResumesWithoutDuplicate() {
        server.loseCreationResponse = true
        show(signedIn = true)
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.waitUntil(10000) { model.state.value.pending != null && !model.state.value.busy }
        compose.onNodeWithTag("budget_setup_name").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Продолжить создание").performScrollTo().performClick()
        waitForCompletion()
        assertEquals(1, server.creations.size)
    }

    @Test fun separateJoinPageKeepsDraftOnBack() {
        show(signedIn = true)
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_open_join").performScrollTo().performClick()
        compose.onNodeWithTag("setup_code").performTextReplacement("bad")
        closeSoftKeyboard()
        compose.onNodeWithText("Присоединиться по коду").performScrollTo().performClick()
        compose.onNodeWithText("Введите код приглашения полностью: 20 букв и цифр.").assertExists()
        compose.onNodeWithTag("setup_back").performScrollTo().performClick()
        compose.onNodeWithText("Поездка").assertExists()
    }

    @Test fun bothButtonsAreReachableAboveRealKeyboard() {
        show()
        screenshot("budget-setup.png")
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performClick()
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(
                compose.activity.window.decorView
            )?.isVisible(WindowInsetsCompat.Type.ime()) ==
                true
        }
        for (tag in listOf("setup_create", "setup_open_join")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val windowInsets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            assertTrue(windowInsets.isVisible(WindowInsetsCompat.Type.ime()))
            val keyboardTop =
                compose.activity.window.decorView.height - windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            assertTrue(compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom <= keyboardTop + 1f)
        }
        screenshot("budget-setup-ime.png")
    }
    private fun screenshot(
        name: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun shortScreenWithLargeFontKeepsButtonsReachable() {
        show(compact = true)
        compose.onNodeWithTag("setup_create").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("setup_open_join").performScrollTo().assertIsDisplayed()
    }
}

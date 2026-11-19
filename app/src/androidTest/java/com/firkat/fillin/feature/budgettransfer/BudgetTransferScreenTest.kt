package com.firkat.fillin.feature.budgettransfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransfer
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferRoute
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferViewModel
import com.firkat.fillin.feature.profile.ui.ProfileScreen
import com.firkat.fillin.feature.profile.ui.ProfileUiState
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BudgetTransferScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileUsesDocumentPickerAndReportsSuccessfulImport() {
        val transfer = FakeTransfer()
        val picker = RecordingPicker()
        compose.setContent {
            val model = remember { BudgetTransferViewModel(transfer, SavedStateHandle()) }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                FillinTheme {
                    ProfileScreen(
                        ProfileUiState(loading = false),
                        null,
                        false,
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        transferContent = { BudgetTransferRoute(model, true) }
                    )
                }
            }
        }
        val export = compose.onNodeWithTag("budget_csv_export")
        val import = compose.onNodeWithTag("budget_csv_import")
        export.assertIsNotEnabled()
        import.performScrollTo().performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)
        compose.runOnIdle { picker.deliver(Activity.RESULT_OK, PICKED_URI) }
        val successMessage = "Создан и выбран новый локальный бюджет."
        compose.onNodeWithText(successMessage).assertExists()
        assertEquals(PICKED_URI.toString(), transfer.importedFile)

        compose.runOnIdle { transfer.exportSource.value = BudgetSyncIdentity("budget-a", null) }
        export.assertIsEnabled().performScrollTo().performClick()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        assertEquals("text/csv", picker.intent.type)
        assertTrue(picker.intent.getStringExtra(Intent.EXTRA_TITLE)?.endsWith(".csv") == true)
        compose.runOnIdle { picker.deliver(Activity.RESULT_CANCELED) }
        export.assertIsEnabled()
        assertEquals(0, transfer.exports)
    }

    private class FakeTransfer : BudgetTransfer {
        override val exportSource = MutableStateFlow<BudgetSyncIdentity?>(null)
        var importedFile: String? = null
        var exports = 0

        override suspend fun export(
            source: BudgetSyncIdentity,
            document: String
        ) {
            exports++
        }

        override suspend fun import(
            document: String,
            fallbackName: String,
            nameSuffix: String
        ): Boolean {
            importedFile = document
            return true
        }
    }

    internal class RecordingPicker :
        ActivityResultRegistry(),
        ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        lateinit var intent: Intent
        private var code = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            code = requestCode
            intent = contract.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, input)
        }

        fun deliver(
            result: Int,
            uri: Uri? = null
        ) {
            dispatchResult(code, result, uri?.let { Intent().setData(it) })
        }
    }

    private companion object {
        val PICKED_URI: Uri = Uri.parse("content://saldomi.test/backup.csv")
    }
}

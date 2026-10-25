package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

/** Shared modal container. The caller owns its presence and supplies feature content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaldomiBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        properties = properties,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Bottom sheet")
@Preview(name = "Bottom sheet dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Bottom sheet compact", widthDp = 320, fontScale = 2f)
@Preview(name = "Bottom sheet wide", widthDp = 840)
@Composable
private fun SaldomiBottomSheetPreview() {
    FillinTheme {
        SaldomiBottomSheet(onDismissRequest = {}) {
            Text(
                text = stringResource(R.string.edit_operation_title),
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}

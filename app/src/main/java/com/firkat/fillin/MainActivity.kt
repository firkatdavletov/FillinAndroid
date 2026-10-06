package com.firkat.fillin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.firkat.fillin.feature.purchasehistory.ui.PurchaseHistoryRoute
import com.firkat.fillin.ui.theme.FillinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FillinTheme {
                FillinApp()
            }
        }
    }
}

@Composable
fun FillinApp() {
    PurchaseHistoryRoute()
}

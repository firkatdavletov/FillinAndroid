package com.firkat.fillin.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshableContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyContentRemainsCenteredAndCanBeRefreshed() {
        var refreshCount = 0
        composeRule.setContent {
            FillinTheme {
                RefreshableContent(
                    isRefreshing = false,
                    onRefresh = { refreshCount++ },
                    hasScrollableContent = false,
                    modifier = Modifier.testTag("refresh"),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Empty")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Empty").assertIsDisplayed()
        val containerCenter = composeRule.onNodeWithTag("refresh").fetchSemanticsNode().boundsInRoot.center
        val textCenter = composeRule.onNodeWithText("Empty").fetchSemanticsNode().boundsInRoot.center
        assertEquals(containerCenter.y, textCenter.y, 1f)
        composeRule.onNodeWithTag("refresh").performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun shortListCanBeRefreshed() {
        var refreshCount = 0
        composeRule.setContent {
            FillinTheme {
                RefreshableContent(
                    isRefreshing = false,
                    onRefresh = { refreshCount++ },
                    hasScrollableContent = true,
                    modifier = Modifier.testTag("refresh"),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { Text("Single item") }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("refresh").performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun pullingInTheMiddleOfAListDoesNotRefresh() {
        var refreshCount = 0
        composeRule.setContent {
            FillinTheme {
                RefreshableContent(
                    isRefreshing = false,
                    onRefresh = { refreshCount++ },
                    hasScrollableContent = true,
                    modifier = Modifier.testTag("refresh"),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = rememberLazyListState(initialFirstVisibleItemIndex = 20),
                    ) {
                        items(40) {
                            Text("Item $it", Modifier.fillMaxWidth().height(80.dp))
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("refresh").performTouchInput {
            swipe(start = Offset(centerX, height * 0.3f), end = Offset(centerX, height * 0.6f))
        }
        composeRule.runOnIdle { assertEquals(0, refreshCount) }
    }

    @Test
    fun pullingWhileRefreshingDoesNotStartAnotherRefresh() {
        var refreshCount = 0
        composeRule.setContent {
            FillinTheme {
                RefreshableContent(
                    isRefreshing = true,
                    onRefresh = { refreshCount++ },
                    hasScrollableContent = false,
                    modifier = Modifier.testTag("refresh"),
                ) {
                    Text("Empty")
                }
            }
        }

        composeRule.onNodeWithTag("refresh").performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertEquals(0, refreshCount) }
    }
}

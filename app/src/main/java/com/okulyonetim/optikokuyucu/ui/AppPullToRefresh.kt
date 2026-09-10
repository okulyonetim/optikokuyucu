package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    enabled: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            onRefresh()
            // Keep the Material 3 indicator visible long enough to feel intentional
            // even when refreshing local/offline data completes immediately.
            delay(550)
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!refreshing) refreshing = true },
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}

package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

enum class AppFeedbackTone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

data class AppFeedbackMessage(
    val text: String,
    val tone: AppFeedbackTone,
    val id: Long = System.nanoTime()
)

class AppFeedbackController internal constructor(
    private val publish: (AppFeedbackMessage) -> Unit
) {
    fun show(text: String, tone: AppFeedbackTone = AppFeedbackTone.INFO) {
        if (text.isNotBlank()) publish(AppFeedbackMessage(text.trim(), tone))
    }

    fun success(text: String) = show(text, AppFeedbackTone.SUCCESS)
    fun info(text: String) = show(text, AppFeedbackTone.INFO)
    fun warning(text: String) = show(text, AppFeedbackTone.WARNING)
    fun error(text: String) = show(text, AppFeedbackTone.ERROR)
}

val LocalAppFeedback = staticCompositionLocalOf { AppFeedbackController {} }

@Composable
fun AppFeedbackProvider(content: @Composable () -> Unit) {
    var message by remember { mutableStateOf<AppFeedbackMessage?>(null) }
    val controller = remember { AppFeedbackController { message = it } }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        delay(
            when (current.tone) {
                AppFeedbackTone.SUCCESS -> 2200L
                AppFeedbackTone.INFO -> 2600L
                AppFeedbackTone.WARNING -> 4200L
                AppFeedbackTone.ERROR -> 4800L
            }
        )
        if (message?.id == current.id) message = null
    }

    CompositionLocalProvider(LocalAppFeedback provides controller) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            message?.let { AppFeedbackPopup(it) }
        }
    }
}

@Composable
private fun AppFeedbackPopup(message: AppFeedbackMessage) {
    val container = when (message.tone) {
        AppFeedbackTone.SUCCESS -> Color(0xFF1E6B49)
        AppFeedbackTone.INFO -> MaterialTheme.colorScheme.primary
        AppFeedbackTone.WARNING -> Color(0xFF9A5B08)
        AppFeedbackTone.ERROR -> MaterialTheme.colorScheme.error
    }
    val symbol = when (message.tone) {
        AppFeedbackTone.SUCCESS -> "✓"
        AppFeedbackTone.INFO -> "i"
        AppFeedbackTone.WARNING -> "!"
        AppFeedbackTone.ERROR -> "×"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = Color.White,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(symbol, fontWeight = FontWeight.Bold)
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AppConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Onayla",
    dismissText: String = "Vazgeç",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}

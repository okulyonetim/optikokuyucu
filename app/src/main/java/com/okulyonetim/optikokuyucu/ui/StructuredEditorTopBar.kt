package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Six-argument overload used by the structured form editor.
 * The editor already applies safeDrawingPadding, so this bar intentionally does not
 * add a second status-bar inset. It also reserves real space for Save + theme actions
 * instead of centering the title underneath them.
 */
@Composable
fun ProductTopBar(
    title: String,
    leadingText: String?,
    onLeadingClick: (() -> Unit)?,
    actionText: String,
    onActionClick: (() -> Unit)?,
    showAutomaticBack: Boolean
) {
    val themeController = LocalProductThemeController.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                modifier = Modifier.size(width = 48.dp, height = 46.dp),
                enabled = onLeadingClick != null,
                onClick = { onLeadingClick?.invoke() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = leadingText ?: if (showAutomaticBack) "‹" else "",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (onActionClick != null) {
                Button(
                    modifier = Modifier.height(40.dp),
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 0.dp)
                ) {
                    Text(actionText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (themeController != null) {
                TextButton(
                    modifier = Modifier.size(width = 44.dp, height = 44.dp),
                    onClick = { themeController.toggleLightDark() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (themeController.isDark) "☀" else "☾",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

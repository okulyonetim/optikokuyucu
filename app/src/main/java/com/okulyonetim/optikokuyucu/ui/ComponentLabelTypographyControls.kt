package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun ComponentLabelTypographyControls(
    fontSize: Double,
    bold: Boolean,
    onFontSizeChange: (Double) -> Unit,
    onBoldChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(7f))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(6f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Etiket Yazı Boyutu: ${fontSize.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled = fontSize > 4.0, onClick = { onFontSizeChange((fontSize - 1.0).coerceAtLeast(4.0)) }) { Text("−") }
            OutlinedButton(enabled = fontSize < 72.0, onClick = { onFontSizeChange((fontSize + 1.0).coerceAtMost(72.0)) }) { Text("+") }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Kalın", modifier = Modifier.weight(1f))
            Switch(checked = bold, onCheckedChange = onBoldChange)
        }
    }
}

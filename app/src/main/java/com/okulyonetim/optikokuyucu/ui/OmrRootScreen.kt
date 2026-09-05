package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult

private enum class RootDestination {
    HOME,
    SCANNER,
    RESULTS,
    DESIGNER
}

@Composable
fun OmrRootScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult
) {
    var destination by remember { mutableStateOf(RootDestination.HOME) }

    if (destination != RootDestination.HOME) {
        BackHandler { destination = RootDestination.HOME }
    }

    MaterialTheme {
        when (destination) {
            RootDestination.HOME -> RootHomeScreen(
                onOpenScanner = { destination = RootDestination.SCANNER },
                onOpenResults = { destination = RootDestination.RESULTS },
                onOpenDesigner = { destination = RootDestination.DESIGNER }
            )

            RootDestination.SCANNER -> OmrAppScreen(
                openCvReady = openCvReady,
                selfTest = selfTest
            )

            RootDestination.RESULTS -> ScanSessionScreen(
                onBack = { destination = RootDestination.HOME }
            )

            RootDestination.DESIGNER -> OmrDesignerScreen(
                openCvReady = openCvReady,
                selfTest = selfTest,
                onBack = { destination = RootDestination.HOME }
            )
        }
    }
}

@Composable
private fun RootHomeScreen(
    onOpenScanner: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenDesigner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Optik Okuyucu", style = MaterialTheme.typography.headlineMedium)
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            text = "Doğruluk + hız odaklı bağımsız OMR",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenScanner
        ) {
            Text("Tara · Kamera ve OMR Testleri")
        }

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            onClick = onOpenResults
        ) {
            Text("Tarama Oturumu · Sonuçlar")
        }

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            onClick = onOpenDesigner
        ) {
            Text("Optik Form Tasarımcısı")
        }
    }
}

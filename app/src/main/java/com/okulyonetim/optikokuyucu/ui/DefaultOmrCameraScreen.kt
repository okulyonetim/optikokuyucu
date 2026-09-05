package com.okulyonetim.optikokuyucu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.LiveScanRecorder
import com.okulyonetim.optikokuyucu.omr.template.resolveActiveOmrTemplate

/**
 * Normal scanner entry point.
 *
 * Designer camera tests call the template-aware overload directly, so they never persist
 * student scan records. Normal scanning resolves the user's active template and persists only
 * reads that have already passed live temporal consensus.
 */
@Composable
fun OmrCameraScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult
) {
    val context = LocalContext.current
    val activeTemplate = remember(context) { resolveActiveOmrTemplate(context) }
    val template = activeTemplate.template
    val recorder = remember(context) {
        LiveScanRecorder(FileScanRecordRepository(context.applicationContext))
    }

    OmrCameraScreen(
        openCvReady = openCvReady,
        selfTest = selfTest,
        template = template,
        onAcceptedRead = { result ->
            runCatching {
                recorder.record(
                    template = template,
                    result = result
                )
            }
        }
    )
}

package com.okulyonetim.optikokuyucu.ui

import androidx.compose.runtime.Composable
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument

/**
 * Keeps the existing designer screen source-compatible while the export/test card gains explicit
 * OpenCV readiness. OmrDesignerScreen already lives behind successful app-level OpenCV startup;
 * a later screen cleanup can pass that flag directly and remove this small overload.
 */
@Composable
fun DesignerPdfExportCard(document: DesignerDocument) {
    DesignerPdfExportCard(document = document, openCvReady = true)
}

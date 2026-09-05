package com.okulyonetim.optikokuyucu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.diagnostics.OpenCvOmrSelfTest
import com.okulyonetim.optikokuyucu.ui.OmrRootScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openCvReady = OpenCVLoader.initLocal()
        val selfTest = if (openCvReady) {
            OpenCvOmrSelfTest.run()
        } else {
            OmrSelfTestResult.NotRun
        }

        setContent {
            OmrRootScreen(
                openCvReady = openCvReady,
                selfTest = selfTest
            )
        }
    }
}

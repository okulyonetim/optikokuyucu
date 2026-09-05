package com.okulyonetim.optikokuyucu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.okulyonetim.optikokuyucu.ui.OmrCameraScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openCvReady = OpenCVLoader.initLocal()

        setContent {
            OmrCameraScreen(openCvReady = openCvReady)
        }
    }
}

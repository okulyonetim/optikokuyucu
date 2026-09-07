package com.okulyonetim.optikokuyucu

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTypography
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.diagnostics.OpenCvOmrSelfTest
import com.okulyonetim.optikokuyucu.ui.OmrRootScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private var exitDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (exitDialog?.isShowing == true) return
                    exitDialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle("Uygulamadan çıkılsın mı?")
                        .setMessage("Optik Okuyucu uygulamasını kapatmak istediğinize emin misiniz?")
                        .setNegativeButton("Vazgeç", null)
                        .setPositiveButton("Çık") { _, _ -> finish() }
                        .setOnDismissListener { exitDialog = null }
                        .show()
                }
            }
        )

        DesignerTypography.install(this)

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

    override fun onDestroy() {
        exitDialog?.dismiss()
        exitDialog = null
        super.onDestroy()
    }
}

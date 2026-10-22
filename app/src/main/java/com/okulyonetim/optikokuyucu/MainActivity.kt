package com.okulyonetim.optikokuyucu

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTypography
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.diagnostics.OpenCvOmrSelfTest
import com.okulyonetim.optikokuyucu.ui.OmrRootScreen
import com.okulyonetim.optikokuyucu.ui.SchoolPortalGate
import com.okulyonetim.optikokuyucu.ui.StaticStartupSplash
import com.okulyonetim.optikokuyucu.update.StartupUpdatePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        // Loading the native library has to happen before any OpenCV class is touched, but the
        // marker/registration self-test is pure diagnostics and used to block the first frame.
        val openCvReady = OpenCVLoader.initLocal()

        setContent {
            val selfTest by produceState(initialValue = OmrSelfTestResult.NotRun, openCvReady) {
                if (openCvReady) {
                    value = withContext(Dispatchers.Default) { OpenCvOmrSelfTest.run() }
                }
            }
            StaticStartupSplash {
                SchoolPortalGate {
                    OmrRootScreen(
                        openCvReady = openCvReady,
                        selfTest = selfTest
                    )
                }
            }
        }

        // Okul Yönetim uygulamasındaki gibi her açılışta yayınlanan son APK'yı kontrol et.
        // Kontrol başarısız olursa uygulamanın açılışı kesinlikle engellenmez.
        StartupUpdatePrompt.checkOnStartup(this)
    }

    override fun onDestroy() {
        exitDialog?.dismiss()
        exitDialog = null
        super.onDestroy()
    }
}

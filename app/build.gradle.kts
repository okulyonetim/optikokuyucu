import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val notoSansCommit = "5e35378e6bda803962ee6fd257e444a7d459660d"
val notoSansGitBlobSha = "75575046c015ff623a848096a15779867ba71453"
val notoSansUrl = "https://raw.githubusercontent.com/google/fonts/$notoSansCommit/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
val generatedNotoFontResDir = layout.buildDirectory.dir("generated/noto-font/res")
val generatedNotoFontResFile = generatedNotoFontResDir.get().asFile

val prepareNotoSansFont = tasks.register("prepareNotoSansFont") {
    val outputFile = generatedNotoFontResDir.map { it.file("font/noto_sans.ttf") }
    outputs.file(outputFile)

    doLast {
        fun gitBlobSha(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
            return digest.digest().joinToString("") { byte: Byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }

        val target = outputFile.get().asFile
        if (target.exists()) {
            val existing = target.readBytes()
            if (gitBlobSha(existing) == notoSansGitBlobSha) return@doLast
        }

        val bytes = URI(notoSansUrl).toURL().openStream().use { input -> input.readBytes() }
        val actualSha = gitBlobSha(bytes)
        check(actualSha == notoSansGitBlobSha) {
            "Noto Sans integrity check failed. Expected $notoSansGitBlobSha but received $actualSha."
        }
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
    }
}

android {
    namespace = "com.okulyonetim.optikokuyucu"

    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.okulyonetim.optikokuyucu"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.12.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            res.srcDir(generatedNotoFontResFile)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareNotoSansFont)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.opencv)

    testImplementation(libs.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

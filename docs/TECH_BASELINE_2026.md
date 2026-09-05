# 2026 Android Teknik Tabanı

Son doğrulama: 2026-09-05

Android uygulama tabanı:

- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- compileSdk: Android 17 API 37.0
- targetSdk: Android 16 API 36
- minSdk: 26
- Java: 17
- Kotlin / Compose Compiler plugin: 2.4.10
- Compose BOM: 2026.08.00
- CameraX: 1.6.1
- AndroidX Core: 1.19.0
- Activity Compose: 1.13.0
- Android NDK için sonraki native OMR adımında hedef: güncel kararlı NDK
- OpenCV değerlendirme tabanı: 5.0.0

## Neden compileSdk 37.0, targetSdk 36?

2026-09-05 itibarıyla kullandığımız güncel AndroidX ve Compose bileşenleri derleme sırasında API 37 veya üzerini gerektiriyor. Android 17 halen önizleme/beta kanalında olduğundan uygulamayı yeni Android 17 çalışma zamanı davranışlarına zorla geçirmek istemiyoruz.

Bu nedenle:

- `compileSdk` 37.0: güncel kütüphaneleri derleyebilmek ve API 37 sembollerine erişebilmek için,
- `targetSdk` 36: kararlı Android 16 davranış tabanını korumak için

ayrı tutulur.

Android build sistemi compileSdk ile targetSdk'nin birbirinden bağımsız yükseltilebilmesini destekler.

## Android 17 SDK paketi

Android 17 platformu komut satırı paket kanalında `platforms;android-37.0` adıyla sağlanabildiği için GitHub CI bu paketi kurar. CI ayrıca güncel Android Command-Line Tools paketini kullanır.

## Kaynak yaklaşımı

Sürüm ve mimari kararları yalnızca güncel resmi Android, Kotlin ve OpenCV kaynakları doğrulandıktan sonra güncellenecektir. Önizleme teknolojileri yalnızca gerekli olduğunda ve çalışma zamanı hedefinden ayrıştırılabildiğinde kullanılacaktır.

## CI

`main` branch'ine gelen her değişiklikte GitHub Actions debug APK üretir. Böylece yerel bilgisayar veya Android Studio zorunlu değildir.

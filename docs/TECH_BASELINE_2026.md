# 2026 Android Teknik Tabanı

Son doğrulama: 2026-09-05

İlk Android iskeleti için kararlı sürümler tercih edilmiştir:

- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- compileSdk: 36
- targetSdk: 36
- Java: 17
- Kotlin / Compose Compiler plugin: 2.4.10
- Compose BOM: 2026.08.00
- CameraX: 1.6.1
- AndroidX Core: 1.19.0
- Activity Compose: 1.13.0
- Android NDK için sonraki native OMR adımında hedef: güncel kararlı NDK
- OpenCV değerlendirme tabanı: 5.0.0

## Android 17 notu

Android 17, API 37'dir; ancak 2026-09-05 itibarıyla Android Developers güncelleme sayfasında Beta olarak listelenmektedir. Bu nedenle üretim tabanı şimdilik Android 16 / API 36 olarak tutulur. Android 17 kararlı olduğunda ve GitHub CI ortamında standart SDK paketi olarak erişilebilir olduğunda compileSdk / targetSdk yükseltmesi ayrıca yapılacaktır.

## Kaynak yaklaşımı

Sürüm ve mimari kararları yalnızca güncel resmi Android, Kotlin ve OpenCV kaynakları doğrulandıktan sonra güncellenecektir. Alfa/beta bağımlılıklar performans veya gerekli bir özellik için açık gerekçe olmadan ana hatta alınmayacaktır.

## CI

`main` branch'ine gelen her değişiklikte GitHub Actions debug APK üretir. Böylece yerel bilgisayar veya Android Studio zorunlu değildir.

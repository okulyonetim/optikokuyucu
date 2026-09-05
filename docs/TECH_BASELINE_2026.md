# 2026 Android Teknik Tabanı

Son doğrulama: 2026-09-05

İlk Android iskeleti için kararlı sürümler tercih edilmiştir:

- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- compileSdk: 37
- targetSdk: 37
- Java: 17
- Kotlin / Compose Compiler plugin: 2.4.10
- Compose BOM: 2026.08.00
- CameraX: 1.6.1
- AndroidX Core: 1.19.0
- Activity Compose: 1.13.0
- Android NDK için sonraki native OMR adımında hedef: r29 / 29.0.14206865
- OpenCV değerlendirme tabanı: 5.0.0

## Kaynak yaklaşımı

Sürüm ve mimari kararları yalnızca güncel resmi Android, Kotlin ve OpenCV kaynakları doğrulandıktan sonra güncellenecektir. Alfa/beta bağımlılıklar performans veya gerekli bir özellik için açık gerekçe olmadan ana hatta alınmayacaktır.

## CI

`main` branch'ine gelen her değişiklikte GitHub Actions debug APK üretir. Böylece yerel bilgisayar veya Android Studio zorunlu değildir.

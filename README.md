# Optik Okuyucu

Android üzerinde tamamen bağımsız çalışan, yüksek hızlı ve yüksek doğruluklu kamera tabanlı OMR / Optik Okuyucu uygulaması.

## Durum

Proje sıfırdan geliştirilmektedir. İlk hedef kullanıcı arayüzü değil, güvenilir ve düşük gecikmeli OMR motorudur.

## Teknoloji tabanı

- Android / Kotlin
- Jetpack Compose
- CameraX
- AGP 9.4.0 / Gradle 9.6.0
- compileSdk / targetSdk 36 (kararlı Android 16 tabanı)
- OMR native çekirdeği için Android NDK + C++ (sonraki aşama)
- OpenCV 5.x değerlendirmesi

Android 17 / API 37, kararlı SDK tabanına geçtiğinde ayrıca değerlendirilecektir.

## Geliştirme modeli

Bilgisayar zorunlu değildir. Kod GitHub üzerinden yönetilir ve her `main` güncellemesinde GitHub Actions debug APK üretir.

APK, başarılı workflow çalışmasının `optik-okuyucu-debug-apk` artifact'ından alınabilir.

## Mimari

Ayrıntılar için [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) dosyasına bakın.

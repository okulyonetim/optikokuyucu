# Optik Okuyucu — Teknik Mimari

## Ürün ilkesi

Bu proje yalnızca Android için geliştirilen bağımsız bir OMR / Optik Okuyucu uygulamasıdır.

Birinci öncelik: **doğruluk + hız**. Büyük arayüz geliştirmesi OMR motoru yeterli kaliteye ulaşmadan yapılmayacaktır.

## Hedef işlem hattı

```text
CameraX ImageAnalysis
        ↓
YUV_420_888 / Y plane
        ↓
Fast Marker Detection
        ↓
Temporal Tracking
        ↓
Geometry + Quality Analysis
        ↓
Homography
        ↓
Perspective Correction
        ↓
Illumination Normalization
        ↓
Template ROI Extraction
        ↓
Bubble Analysis
        ↓
Confidence Engine
        ↓
Result
```

## Katmanlar

- `app`: Compose tabanlı Android uygulama kabuğu.
- Kamera: CameraX `ImageAnalysis`.
- OMR Core: sonraki adımda ayrı native C++/NDK çekirdeği.
- Template modeli: piksel değil milimetre tabanlı deterministik geometri.
- Yerel veri: sonraki fazda Room/SQLite.
- Bulut: zorunlu değildir; gelecekte opsiyonel senkronizasyon katmanı olarak eklenebilir.

## İlk kilometre taşları

1. Android CI ve APK üretimi.
2. CameraX canlı analiz hattı ve frame-time telemetrisi.
3. Marker benchmark: OpenCV 5 marker seçenekleri ve AprilTag 3 karşılaştırması.
4. Stabil marker tracking ve homography.
5. 20 soruluk OMR doğruluk testi.
6. 100 soruluk gecikme benchmarkı.
7. Öğrenci numarası ve seri tarama.
8. Optik Form Tasarımcısı.

## Performans kuralı

Performans kararları masa başı tahminle değil, gerçek Android cihaz benchmarklarıyla verilecektir.

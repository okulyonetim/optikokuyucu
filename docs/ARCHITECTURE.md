# Optik Okuyucu — Teknik Mimari

## Ürün ilkesi

Bu proje yalnızca Android için geliştirilen bağımsız bir OMR / Optik Okuyucu uygulamasıdır.

Birinci öncelik: **doğruluk + hız**. Büyük arayüz geliştirmesi OMR motoru yeterli kaliteye ulaşmadan yapılmayacaktır.

## Temel karar: fiziksel kağıt boyutundan bağımsız okuma

OMR motoru A4, A5, milimetre, DPI, yazıcı kenar boşluğu veya sayfa kenarı üzerinden çalışmayacaktır.

Aynı optik form:

- A4'e,
- A5'e,
- yazıcının "sayfaya sığdır" seçeneğiyle,
- farklı kenar boşluklarıyla,
- daha küçük veya daha büyük bir ölçekle

yazdırılabilir. Formdaki dört fiducial/marker görünür kaldığı sürece bunların tamamı **aynı OMR şablonu** kabul edilir.

### Canonical Template Space

Şablon geometrisi fiziksel ölçü yerine birimsiz mantıksal koordinat sisteminde tutulur.

Örnek varsayılan form alanı:

```text
width  = 1000 logical units
height = 1414.213562 logical units
```

Bu değerler yalnız formun tasarım oranını tanımlar. 1000 birim = 1000 piksel veya 1000 mm değildir.

Marker, baloncuk, öğrenci numarası ve diğer tüm bölgeler bu canonical koordinat sisteminde saklanır.

### Fiziksel baskı alanı OMR modelinin parçası değildir

Yazdırma sırasında oluşabilecek:

- uniform scale,
- X/Y yönünde küçük farklı ölçekleme,
- farklı yazıcı marginleri,
- sayfa üzerindeki kayma,
- kamera perspektifi,
- hafif rotasyon ve skew

marker tabanlı registration/homography aşamasında giderilir.

Sayfanın fiziksel kenarları OMR kararında kullanılmaz.

## Hedef işlem hattı

```text
CameraX ImageAnalysis
        ↓
YUV_420_888 / Y plane
        ↓
Fiducial Detection (4 unique IDs)
        ↓
Temporal Tracking
        ↓
Anchor Geometry + Quality Analysis
        ↓
Camera → Canonical Template Homography
        ↓
Fixed Canonical Raster Normalization
        ↓
Illumination Normalization
        ↓
Canonical ROI Extraction
        ↓
Bubble Analysis
        ↓
Confidence Engine
        ↓
Result / Ambiguous / Rescan
```

## Registration stratejisi

Dört marker yalnız sayfayı bulmak için değil, doğrudan koordinat sistemini kurmak için kullanılır.

Her marker'ın:

- benzersiz ID'si,
- canonical konumu,
- dört canonical köşesi

şablonda bellidir.

Kamerada marker bulunduğunda gözlenen marker köşeleri canonical marker köşelerine eşlenir. Homography hesaplanırken mümkün olduğunda yalnız dört marker merkezi yerine **16 marker köşesi** kullanılacaktır. RANSAC/reprojection error ile yanlış eşleşmeler elenecektir.

Böylece A4 tasarımının A5'e küçültülmesi okuma geometrisini değiştirmez; yalnız kamera görüntüsündeki fiziksel piksel ölçeği değişir.

## Kalite kapıları

OMR motoru yeterli görüntü kanıtı yoksa cevap üretmeyecektir.

Önemli kalite sinyalleri:

- dört beklenen marker ID'sinin görünmesi,
- marker reprojection error,
- markerların görüntü alanındaki kapsama oranı,
- marker başına minimum piksel boyutu,
- hareket oranı,
- blur/odak metriği,
- parlaklık/kontrast,
- temporal stabilite,
- homography condition/geometry quality.

Form küçük yazdırılmışsa sorun "A5 olması" değildir. Sorun ancak kamera görüntüsünde marker veya baloncukların güvenilir analiz için fazla az piksele düşmesidir. Böyle bir durumda uygulama yanlış okumak yerine kullanıcıdan kamerayı yaklaştırmasını ister.

## Bubble okuma ilkesi

Bubble ROI'leri fiziksel milimetre veya kamera pikseliyle değil canonical template koordinatlarıyla tanımlanacaktır.

Homography sonrası form sabit canonical raster'a dönüştürülür. Ardından her baloncuk için:

- iç doluluk,
- çevre/arka plan referansı,
- lokal kontrast,
- komşu seçeneklere göre göreceli fark,
- kenar yoğunluğu,
- çoklu kare tutarlılığı

birlikte değerlendirilir.

Tek bir global threshold kullanılmayacaktır.

## Yazıcı toleransı

Desteklenen normal değişiklikler:

- A4 → A5 küçültme,
- farklı printer margins,
- fit-to-page,
- sayfa üzerinde translasyon,
- küçük anisotropic scaling,
- kamera perspektifi ve dönüş.

Desteklenmeyen veya yeniden tarama gerektirecek durumlar:

- markerlardan birinin kesilmesi,
- formun yalnız bir bölümünün yazdırılması,
- markerların birbirine göre bağımsız yer değiştirmesi,
- ağır kağıt kırışması / lokal non-linear warp,
- marker veya baloncukların kamera için aşırı küçük kalması.

İlerleyen fazda ağır lokal baskı/kağıt deformasyonu için dört ana marker'a ek olarak opsiyonel iç kontrol noktalarıyla piecewise/local warp düzeltmesi değerlendirilecektir.

## Katmanlar

- `app`: Compose tabanlı Android uygulama kabuğu.
- Kamera: CameraX `ImageAnalysis`.
- Fiducial katmanı: fiziksel kağıdı değil logical form frame'ini bulur.
- Template modeli: birimsiz canonical geometri.
- Registration: kamera koordinatlarını canonical template alanına taşır.
- OMR Core: performans kritik aşamada ayrı native C++/NDK çekirdeği.
- Yerel veri: sonraki fazda Room/SQLite.
- Bulut: zorunlu değildir; gelecekte opsiyonel senkronizasyon katmanı olarak eklenebilir.

## Test stratejisi

Fiziksel yazıcı olmadan sentetik test matrisi oluşturulacaktır:

1. %50, %70.7, %100, %125 baskı ölçeği.
2. Farklı sol/sağ/üst/alt margin ve sayfa içi kaydırma.
3. X/Y ölçeğinde küçük farklılıklar.
4. Perspektif ve rotasyon.
5. Parlaklık, gölge, blur ve sensör gürültüsü.
6. Eksik marker / yanlış marker ID negatif testleri.
7. Farklı canonical raster çözünürlükleri.
8. 20 ve 100 soruluk bubble doğruluk + gecikme benchmarkları.

## İlk kilometre taşları

1. Android CI ve APK üretimi.
2. CameraX canlı analiz hattı ve frame-time telemetrisi.
3. Marker benchmark: OpenCV 5 marker seçenekleri ve AprilTag 3 karşılaştırması.
4. Stabil marker tracking ve scale-invariant homography.
5. Sentetik print-scale/perspective benchmarkı.
6. 20 soruluk OMR doğruluk testi.
7. 100 soruluk gecikme benchmarkı.
8. Öğrenci numarası ve seri tarama.
9. Optik Form Tasarımcısı.

## Performans kuralı

Performans kararları masa başı tahminle değil, sentetik benchmark + gerçek Android cihaz ölçümüyle verilecektir.

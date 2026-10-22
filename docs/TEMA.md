# Merkezi tema

Uygulamanın görünümü `app/src/main/java/com/okulyonetim/optikokuyucu/ui/theme/` altında tek yerden yönetilir.
Ekranlarda ham renk (`Color(0xFF…)`), sabit yazı boyutu (`fontSize = 10.sp`) veya sabit köşe
(`RoundedCornerShape(12.dp)`) yazmayın; aşağıdaki belirteçleri kullanın.

| Konu | Dosya | Kullanım |
| --- | --- | --- |
| Renk şeması, durum/sonuç renkleri, kamera ve kâğıt katman renkleri, vurgu gradyanları | `OptikColors.kt` | `MaterialTheme.colorScheme.*`, `OptikTheme.status.success.content`, `OptikTheme.result.correct`, `OptikOverlay.locked` |
| Yazı tipi (Noto Sans) ve boyut merdiveni | `OptikTypography.kt` | `fontSize = OptikTextSize.small` (en küçük: `micro` = 11sp) |
| Köşe yuvarlaklığı | `OptikShapes.kt` | `OptikShape.md` (xs 6 · sm 10 · md 14 · lg 18 · xl 24 · pill) |
| Boşluk ve çizgi kalınlığı | `OptikSpacing.kt` | `OptikSpacing.sm`, `OptikBorder.regular` |
| Tema girişi, sistem çubukları | `OptikTheme.kt` | `OptikTheme(darkTheme) { … }` (uygulamada `OptikProductTheme` çağırır) |

## Renk değiştirirken

1. `OptikColors.kt` içindeki değeri değiştirin.
2. `./gradlew :app:testDebugUnitTest --tests '*OptikThemeContrastTest'` çalıştırın. Test, açık ve koyu temadaki
   her metin/zemin çiftinin WCAG AA (4.5:1) ve çizgilerin 3:1 kontrastını doğrular; ihlalde hangi çiftin
   bozulduğunu söyler. GitHub'daki CI de aynı testi her push'ta çalıştırır.
3. Kâğıt/kamera üzerine çizilen renkler (`OptikOverlay`) fotoğraf zemini üzerinde olduğundan temadan bağımsızdır.

## Not

Form editöründeki kâğıt önizlemeleri ve basılı form çizimi (`omr/designer`) bilerek bu yazı boyutu
merdiveninin dışındadır; onlar kâğıt ölçüsüne göre çizilir.

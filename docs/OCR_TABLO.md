# OCR tablo okuyucu (çok kitapçıklı cevap anahtarı)

MEB/LGS tipi yayımlanmış cevap anahtarı görselleri: her hücre `12-B` biçiminde, A/B/C/D Grubu tabloları
yan yana veya alt alta, her tabloda ders başına sütun grubu.

Kod: `ocr/OcrAnswerTableParser.kt` (okuma), `ocr/OcrAnswerTableDraft.kt` (kullanıcı düzeltmeleri),
`ui/OcrTableReview.kt` (önizleme). Sayfa bir tablo değilse `OcrAnswerKeyParser` (eski satır okuyucu) çalışır.

## Nasıl okur
1. **Hücreler:** `N-X` kalıbına uyan her öğe (tek öğede birden çok hücre olabilir; "12" + "B" diye bölünmüş
   öğeler birleştirilir). Soru numarası hücrenin içinde olduğu için satır modeli gerekmez.
2. **Tablolar:** ders başlıklarının hizalı olduğu başlık satırları; bir ders ikinci kez görününce yeni tablo başlar.
   Kitapçık, tablonun hemen üstündeki "A Grubu" başlığından alınır.
3. **Dersler:** sütunlar soru numaralarına göre bloklara ayrılır (her ders 1'den başlar), bloklar başlıklarla soldan
   sağa eşleşir. Başlığı okunamayan tek ders, artan tek blokla eleme yoluyla eşleşir ve işaretlenir.
4. **Doğrulama:** her ders 1..N tam bir kez aranır. Eksik → `MISSING`; aynı numara farklı cevap → `CONFLICT`;
   benzer karakterden onarılan (`8→B`, `0→D`, `4→A`), sırası bozuk, numarası sıraya göre düzeltilen veya düşük
   güvenli (<0,5) hücre → `SUSPICIOUS`.

## Önizleme kuralı
Boş hücre doldurulmadan ve işaretli hücre onaylanmadan (dokun, şık seç ya da dersi toplu onayla) kayıt açılmaz;
belirsiz okuma kendiliğinden resmî anahtara girmez. Sınav formunda kitapçık alanı yoksa tek tablo seçilip kaydedilir.

## Bilinen sınırlar
- Birden fazla dersin başlığı birden okunamazsa sütunlar eşleştirilemez; o dersler boş kalır, uyarı verilir.
- Hiç ders başlığı okunamazsa okuyucu tablo saymaz (eski okuyucuya düşer).
- Doğruluk testleri rapidocr çıktısıyla yapıldı (`src/test/resources/ocr`); ML Kit ile cihaz doğrulaması ayrıca yapılmalı.

## Yeni örnekle test
OCR çıktısını `metin<TAB>sol<TAB>üst<TAB>sağ<TAB>alt<TAB>güven` satırları olarak (ilk satır `genişlik<TAB>yükseklik`)
`src/test/resources/ocr/` altına koyup `OcrAnswerTableParserTest` içindeki `loadFixture` ile okutun.

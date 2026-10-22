# OCR tablo okuyucu (çok kitapçıklı cevap anahtarı)

Yayımlanmış cevap anahtarı görselleri. İki düzen okunur, hangisi daha çok hücreyi açıklıyorsa o seçilir:
- **Sütun başlıklı:** ders adları üstte, ders başına sütun grubu (LGS anahtarı `12-B` hücreli; MEB anahtarı
  `1.` ve `A` ayrı kutularda).
- **Satır başlıklı:** ders adı solda, cevaplar satır boyunca (`1. C 2. B …`, 11–20 bir alt satırda), tablolar alt alta
  (ÖZDER deneme anahtarı). Kitapçık, tablonun üstündeki büyük harften ya da "A Grubu/Kitapçığı" başlığından alınır.

Kod: `ocr/OcrAnswerTableParser.kt` (okuma), `ocr/OcrAnswerTableDraft.kt` (kullanıcı düzeltmeleri),
`ui/OcrTableReview.kt` (önizleme). Sayfa bir tablo değilse `OcrAnswerKeyParser` (eski satır okuyucu) çalışır.

## Nasıl okur
1. **Hücreler:** `N-X` kalıbına uyan her öğe (tek öğede birden çok hücre olabilir; "12" + "B" diye bölünmüş
   öğeler birleştirilir). Soru numarası hücrenin içinde olduğu için satır modeli gerekmez.
2. **Tablolar:** ders başlıklarının hizalı olduğu başlık satırları (satır başlıklıda: soldaki etiket listesi); bir ders
   ikinci kez görününce yeni tablo başlar. Kitapçık, tablonun hemen üstündeki "A Grubu" başlığından ya da büyük
   tek harften alınır.
3. **Dersler:** sütunlar (satır başlıklıda satırlar) soru numaralarına göre bloklara ayrılır (her ders 1'den başlar),
   bloklar başlıklarla okuma sırasında eşleşir. Başlığı okunamayan tek ders, artan tek blokla eleme yoluyla eşleşir ve işaretlenir.
4. **Doğrulama:** her ders 1..N tam bir kez aranır. Eksik → `MISSING`; aynı numara farklı cevap → `CONFLICT`;
   benzer karakterden onarılan (`8→B`, `0→D`, `4→A`), sırası bozuk, numarası sıraya göre düzeltilen veya düşük
   güvenli (<0,5) hücre → `SUSPICIOUS`.

5. **Güven kontrolü:** okuma hücrelerin yarısından azını açıklıyorsa tablo sayılmaz ve eski satır okuyucuya düşülür
   (boş tablolar üretmez).

## Önizleme kuralı
Boş hücre doldurulmadan ve işaretli hücre onaylanmadan (dokun, şık seç ya da dersi toplu onayla) kayıt açılmaz;
belirsiz okuma kendiliğinden resmî anahtara girmez. Sınav formunda kitapçık alanı yoksa tek tablo seçilip kaydedilir.

## Bilinen sınırlar
- Birden fazla dersin başlığı birden okunamazsa sütunlar eşleştirilemez; o dersler boş kalır, uyarı verilir.
- Hiç ders başlığı okunamazsa okuyucu tablo saymaz (eski okuyucuya düşer).
- Doğruluk testleri rapidocr çıktısıyla yapıldı (`src/test/resources/ocr`: LGS 2023 ve ÖZDER anahtarı); ML Kit ile cihaz
  doğrulaması ayrıca yapılmalı. MEB (`1.` / `A` ayrı kutu) düzeni yalnızca sentetik belirteçlerle denendi.
- Büyük kitapçık harfi OCR'ca okunamazsa kitapçık boş kalır, önizlemede seçilir.

## Yeni örnekle test
OCR çıktısını `metin<TAB>sol<TAB>üst<TAB>sağ<TAB>alt<TAB>güven` satırları olarak (ilk satır `genişlik<TAB>yükseklik`)
`src/test/resources/ocr/` altına koyup `OcrAnswerTableParserTest` içindeki `loadFixture` ile okutun.

# LGS tahmini puan regresyon planı

1. Tek öğrenci, tüm sorular boş: yaklaşık 187.40 puan.
2. Tek öğrenci, 90 doğru: 500.00 puan.
3. Türkçe testinde 3 yanlış: Türkçe neti -1.00 ve tahmini puan 183.21.
4. Yapısal `answers-1` ... `answers-6` test kimlikleri doğru derslere eşlenmeli.
5. LGS dışındaki puanlama türleri mevcut `ExamScoreEngine` davranışını korumalı.
6. Eksik test / şüpheli / anahtarsız işaret varsa tahmini LGS puanı üretilmemeli.

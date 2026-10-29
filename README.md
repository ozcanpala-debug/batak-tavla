# Batak & Tavla (internetsiz, telefondan telefona)

Aşama 1: proje iskeleti, ana menü, oda kur / odaya katıl, koltuklar, bot ekleme ve bağlantı testi.
Aşama 1b: XOX oyunu eklendi (2 kişi, kurucu hakem, bota karşı da oynanır, yeniden oyna).
Tavla ve Batak oyun mantığı sonraki aşamalarda eklenecek.

## Açma
1. Android Studio'yu aç, **Open** ile bu klasörü seç (`OfflineOyun`).
2. Gradle senkronizasyonu bitsin (ilk seferde internet gerekir, kütüphaneler iner).
   Studio "Gradle wrapper eksik" gibi bir şey sorarsa kendi Gradle'ını kullanmasına izin ver.
3. İki gerçek Android telefon bağla (emülatör Nearby için güvenilir değil) ve uygulamayı ikisine de yükle.

## Test
1. İki telefonda da Wi-Fi ve Bluetooth açık olsun (internet gerekmiyor). Android 11 ve altında Konum da açık olmalı.
2. Telefon A: ad yaz, oyunu seç, **Oda kur**. İzinleri ver.
3. Telefon B: ad yaz, **Odaya katıl**. İzinleri ver, listede A'nın odası çıkınca **Katıl**'a bas.
4. A'da koltuk dolar. Boş koltuklara **Bot ekle** de diyebilirsin. **Oyunu başlat**.
5. Geçici oyun ekranındaki kutudan mesaj yaz: iki telefonda da görünüyorsa internetsiz bağlantı çalışıyor.

## Yapı
- `net/NearbyNet.kt`: Nearby Connections (P2P_STAR, 1 kurucu + en fazla 3 telefon)
- `lobby/Lobby.kt`: oyun türleri, koltuklar, JSON mesaj biçimi
- `game/Xox.kt`, `ui/XoxScreen.kt`: XOX kuralları, bot (minimax) ve ekranı
- `SessionViewModel.kt`: kurucu hakem mantığı (koltuklar, bot, başlat, kopma)
- `ui/Screens.kt`, `MainActivity.kt`: ekranlar ve izinler

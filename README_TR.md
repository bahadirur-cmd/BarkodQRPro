# BarkodQR Pro (Android)

## Özellikler
- Tek uygulamada Code 128 barkod ve QR modu
- TXT dosyasından satır satır toplu kod yükleme
- Ekranda dikey liste ve otomatik kaydırma
- Hız ayarı, başlat/durdur
- PNG'leri Galeri içindeki `Pictures/BarkodQRPro` klasörüne kaydetme
- Kod üretimi cihaz üzerinde yapılır, internet bağlantısı gerekmez

## APK oluşturma (GitHub Actions)
1. Bu ZIP'i bilgisayarında çıkar.
2. GitHub'da yeni ve boş bir repository oluştur.
3. Çıkardığın klasörün içindeki dosyaları repository'ye yükle ( `.github` klasörü dahil).
4. GitHub'da **Actions** sekmesine gir.
5. `Build BarkodQR Pro APK` iş akışını açıp **Run workflow** seç.
6. İşlem bitince sayfanın altındaki **Artifacts** bölümünden `BarkodQRPro-debug` dosyasını indir.
7. ZIP'i aç, `app-debug.apk` dosyasını Android telefona gönderip kur.

## TXT biçimi
Her satıra bir kod gelecek şekilde UTF-8 metin dosyası kullan:
```
123456789012
987654321098
ABC-123
```

## Not
Bu paket kaynak projedir, APK dosyası değildir. APK, GitHub Actions tarafından derlenir.

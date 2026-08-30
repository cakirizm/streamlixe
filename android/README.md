# StreamLiveX Android

Android uygulaması mevcut StreamLiveX web arayüzünü WebView içinde kullanır; yayın açıldığında URL, JavaScript köprüsü üzerinden native Media3/ExoPlayer katmanına aktarılır. Bu sayede profil, dil, RTL ve kategori hafızası web uygulamasıyla aynı kalırken video trafiği doğrudan IPTV sağlayıcısından cihaza gider.

## Gereksinimler

- Android Studio ve Android SDK 36
- JDK 17
- Test için Android 6.0 (API 23) veya daha yeni telefon, tablet ya da Android TV

## Derleme

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK dosyası `android/app/build/outputs/apk/debug/app-debug.apk` altında oluşur.

## Google Play yayını

Telefon, tablet, Android TV ve Google TV için tek uygulamayla Play Console'a
yükleme adımları: [`PUBLISHING.md`](PUBLISHING.md). Yayın paketini (AAB) üretmek için:

```powershell
cd android
./scripts/build-release.ps1
```

Mağaza metinleri ve görsel gereksinimleri: [`play-store/`](play-store/).

## Yerel web arayüzüyle geliştirme

Önce depo kökünde web sunucusunu çalıştırın. Doğrudan Vite geliştirme sunucusu varsayılan olarak `5173` portunu kullanır ve Android emülatörü ana bilgisayara `10.0.2.2` adresiyle ulaşır:

```powershell
npm run dev
cd android
.\gradlew.bat assembleDebug -PSTREAMLIVEX_WEB_URL=http://10.0.2.2:5173
```

Docker geliştirme sunucusu kullanılıyorsa adres `http://10.0.2.2:3000` olur. Fiziksel cihazda `STREAMLIVEX_WEB_URL` için bilgisayarın yerel ağ adresini kullanın. Yayınlanmış sürüm varsayılan olarak mevcut StreamLiveX Sites adresini açar; ileride etkinleştirilecek özel alan adı aynı Gradle özelliğiyle verilebilir.

## Güvenlik ve oynatma

- IPTV kullanıcı adı veya parolası Android kaynak koduna yazılmaz.
- `TMDB_TOKEN` web sunucusunda kalır ve APK içine eklenmez.
- HTTP kullanan eski IPTV sağlayıcıları için açık metin medya bağlantılarına izin verilir.
- Media3 cihazın donanımsal codec çözücülerini kullanır; HEVC desteği cihaz modeline göre değişebilir.
- Uygulama telefon başlatıcısı ve Android TV Leanback başlatıcısı için yapılandırılmıştır.

## Özel bağlantı

Kurulu uygulamada bir yayını doğrudan açmak için:

```text
streamlivex://play?url=HTTPS_YAYIN_ADRESI&title=Film%20Adi&kind=movie
```

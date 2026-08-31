# StreamLiveX – Google Play yayınlama rehberi

Bu rehber, StreamLiveX Android uygulamasını **tek bir uygulama kaydıyla**
Google Play üzerinde **telefon, tablet, Android TV ve Google TV** için nasıl
yayınlayacağınızı anlatır.

> **Önemli mimari not:** Ayrı bir "TV uygulaması" ve "telefon uygulaması" gerekmez.
> `android/` içindeki tek modül her iki form faktörünü de hedefler:
> - Telefon/tablet → `LAUNCHER` intent-filter (dokunmatik).
> - Android TV / Google TV → `LEANBACK_LAUNCHER` intent-filter + `android:banner`.
>
> Aynı `.aab`, aynı `applicationId` (`com.streamlivex.android`) ve aynı imza tüm
> cihazlara gider. Google TV, Android TV mağazasını kullanır; ayrı bir kayıt
> gerektirmez — Android TV desteği verirseniz Google TV'de de görünürsünüz.

---

## 0. Ön koşullar

- **Google Play Developer hesabı** (tek seferlik 25 USD kayıt ücreti, doğrulanmış).
- **JDK 17** ve **Android SDK 36** (Android Studio ile gelir).
- Depo kökünde `android/` klasörü.
- Bir **upload keystore** (aşağıda oluşturuluyor).

---

## 1. Upload keystore oluştur (bir kez)

Play, uygulamayı **Play App Signing** ile imzalar; siz yalnızca bir *upload*
anahtarıyla imzalayıp yüklersiniz. Anahtarı depo DIŞINDA güvenli bir yerde tutun.

```bash
keytool -genkeypair -v \
  -keystore streamlivex-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -storepass "GUCLU_PAROLA" -keypass "GUCLU_PAROLA"
```

> `-validity 9125` ≈ 25 yıl. Bu `.jks` dosyasını ve parolalarını **kaybederseniz**
> upload anahtarını Play destekten sıfırlatabilirsiniz, ama yine de yedekleyin.
> `*.jks` ve `keystore.properties` `.gitignore` ile hariç tutulur — **asla commit etmeyin.**

Ardından imza bilgilerini Gradle'a tanıtın. Şablonu kopyalayın:

```bash
cp android/keystore.properties.example android/keystore.properties
```

`android/keystore.properties` içeriğini gerçek değerlerle doldurun:

```properties
storeFile=C:/Users/SEDAT/keys/streamlivex-upload.jks
storePassword=GUCLU_PAROLA
keyAlias=upload
keyPassword=GUCLU_PAROLA
```

**CI kullanıyorsanız** dosya yerine ortam değişkenleri de geçerlidir:
`STREAMLIVEX_KEYSTORE_FILE`, `STREAMLIVEX_KEYSTORE_PASSWORD`,
`STREAMLIVEX_KEY_ALIAS`, `STREAMLIVEX_KEY_PASSWORD` (bkz. `app/build.gradle.kts`).

---

## 2. Yayın paketini (AAB) derle

Play Console yalnızca **Android App Bundle (.aab)** kabul eder — APK değil.

**Windows (PowerShell):**

```powershell
cd android
./scripts/build-release.ps1
```

**Linux / macOS / CI:**

```bash
cd android
./scripts/build-release.sh
```

Betik imzalı AAB'yi üretir:

```
android/app/build/outputs/bundle/release/app-release.aab
```

### Sürüm numarasını yükseltmek

Play'e **her yeni yüklemede `versionCode` bir öncekinden büyük** olmalı.
`build.gradle.kts` bunu `-P` özelliğinden okuyacak şekilde ayarlıdır:

```powershell
./scripts/build-release.ps1 -VersionCode 2 -VersionName "1.0.1"
```

```bash
VERSION_CODE=2 VERSION_NAME=1.0.1 ./scripts/build-release.sh
```

### Web arayüz adresini değiştirmek

Uygulama kabuğu web arayüzünü bir URL'den yükler (varsayılan
`https://streamlivex.com/app`). Gerekirse:

```powershell
./scripts/build-release.ps1 -WebUrl "https://streamlivex.com/app"
```

---

## 3. Play Console'da uygulamayı oluştur

1. [Play Console](https://play.google.com/console) → **Uygulama oluştur**.
2. Uygulama adı: **StreamLiveX**, tür: **Uygulama**, ücretsiz/ücretli seçimi.
3. Paket adı otomatik olarak ilk AAB'den (`com.streamlivex.android`) alınır.

---

## 4. Mağaza kaydı (Store listing)

Metin şablonları hazır: `android/play-store/metadata/android/<dil>/`
(6 dil: `tr`, `en-US`, `de-DE`, `es-ES`, `fr-FR`, `ar`).

- **Ana Mağaza girişi**'nde varsayılan dili seçin, sonra çeviri ekleyin.
- Her dil için `title.txt`, `short_description.txt`, `full_description.txt`
  içeriğini Play Console'daki ilgili alanlara yapıştırın.
- Görselleri `android/play-store/GRAPHICS.md`'deki boyutlara göre yükleyin
  (simge 512×512, öne çıkan grafik 1024×500, telefon/tablet/TV ekran görüntüleri).

> **İçerik politikası uyarısı (kritik):** StreamLiveX içerik SAĞLAMAYAN, kullanıcının
> kendi IPTV aboneliğini oynatan bir "BYO" istemcidir. Açıklama metinleri bunu
> açıkça belirtir. Bu netlik, Play'in telifli yayın içeren uygulamalara yönelik
> reddini önlemek için önemlidir; metinlerdeki "içerik sağlamaz" ifadesini kaldırmayın.

---

## 5. Android TV / Google TV kaydı

TV desteğinin mağazada görünmesi için:

1. **Sol menü → İçerik derecelendirmesi, Hedef kitle** vb. bölümleri doldurun.
2. **Cihaz kataloğu / form faktörleri**: uygulama TV form faktörünü otomatik
   algılar çünkü manifestte şunlar var:
   - `LEANBACK_LAUNCHER` intent-filter (TV başlatıcısı),
   - `android:banner="@drawable/tv_banner"`,
   - `uses-feature android.software.leanback required="false"`,
   - `uses-feature android.hardware.touchscreen required="false"` (dokunmatik zorunlu değil).
3. **TV görsellerini yükleyin** (zorunlu): 1280×720 TV afişi + 1920×1080 TV ekran
   görüntüleri. Bunlar olmadan uygulama TV cihazlarında listelenmez.
4. Play, gönderdiğiniz sürümü ayrıca **Android TV kalite incelemesinden** geçirir
   (kumandayla tam gezinme, geri tuşu davranışı, banner). Uygulama D-pad ile tam
   gezinilebilir olacak şekilde tasarlanmıştır.

> Google TV ayrı bir işlem gerektirmez: Android TV'de yayınlanan uygulama Google TV
> arayüzünde de görünür.

---

## 6. Uygulama içeriği bildirimleri (App content)

Play, yayın öncesi şu bölümlerin doldurulmasını ister:

- **Gizlilik politikası** URL'si (zorunlu).
- **Veri güvenliği** formu: Uygulama IPTV kimlik bilgilerini yalnızca cihazda
  saklar, sunucuya göndermez; buna göre beyan edin.
- **Reklamlar**: yok (varsa beyan edin).
- **İçerik derecelendirmesi** anketi.
- **Hedef kitle ve içerik**: yetişkin/genel.
- **Devlet uygulaması / haber uygulaması**: hayır.

---

## 7. Sürüm oluştur ve dağıt

1. **Test → Kapalı test** (önerilir) ile başlayın: yeni sürüm → `app-release.aab`
   yükleyin → sürüm notlarını girin (şablon: her dilin `changelogs/1.txt`'i).
2. Test edenleri ekleyin, telefon **ve** bir Android TV / Google TV cihazında doğrulayın.
3. Sorun yoksa **Üretim (Production)** kanalına yükseltin → aşamalı sunum (staged
   rollout) yüzdesi seçin → yayınla.

---

## 8. Otomasyon (isteğe bağlı) — fastlane supply

`android/play-store/metadata/` klasörü fastlane `supply` düzeniyle uyumludur.
Bir Play Console servis hesabı JSON'u oluşturduktan sonra tüm metin ve görselleri
tek komutla yükleyebilirsiniz:

```bash
cd android
fastlane supply \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --metadata_path play-store/metadata \
  --json_key /path/to/play-service-account.json \
  --track internal
```

---

## Kontrol listesi

- [ ] Upload keystore oluşturuldu ve güvenli yerde yedeklendi
- [ ] `android/keystore.properties` dolduruldu (commit EDİLMEDİ)
- [ ] `versionCode` bir önceki yüklemeden büyük
- [ ] `build-release` betiği imzalı `app-release.aab` üretti
- [ ] 6 dilde mağaza metinleri girildi
- [ ] Simge (512×512) + öne çıkan grafik (1024×500) yüklendi
- [ ] Telefon + tablet ekran görüntüleri yüklendi
- [ ] TV afişi (1280×720) + TV ekran görüntüleri (1920×1080) yüklendi
- [ ] Gizlilik politikası + Veri güvenliği + İçerik derecelendirmesi tamamlandı
- [ ] Kapalı testte telefon **ve** TV/Google TV cihazında doğrulandı
- [ ] Üretim sürümü yayınlandı

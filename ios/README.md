# StreamLiveX iOS

Bu klasör, mevcut StreamLiveX web arayüzünü iPhone ve iPad'de `WKWebView` içinde çalıştıran, videoyu AVFoundation ile yerel oynatan iOS uygulamasını içerir. tvOS/Apple TV hedefi yoktur. Android, Android TV ve web bağımsız kalır.

## Mimari

- Web arayüzü `https://streamlivex.com/app` adresinden yüklenir. Kalıcı `WKWebsiteDataStore` çerezleri, localStorage/IndexedDB'yi, profilleri, dil/RTL, tema, favoriler ve kategori konumlarını korur.
- `NativeBridge`, Android telefon/tablet uygulamasındaki `window.chrome.webview.postMessage` sözleşmesini aynen uygular: `play`, `preview`, `preview-layout`, `promote-preview`, `close` ve `close-preview`.
- `AVPlayer`/`AVPlayerViewController` canlı, HLS, VOD, film ve dizi oynatır. Gömülü ses ve altyazı parçaları sistem oynatıcısından seçilebilir; web tercihi eşleşirse başlangıçta uygulanır.
- Canlı önizleme ve tam ekran aynı AVPlayer'ı kullandığından geçişte medya yeniden hazırlanmaz.

## Xcode projesi ve signing

Güncel Xcode ve XcodeGen gerekir (`brew install xcodegen`). macOS'ta `cd ios && xcodegen generate` komutuyla `StreamLiveX.xcodeproj` üretin. Projeyi açıp kişisel Team'i seçin. Depoda Team ID, provisioning profili, sertifika veya secret bulunmaz.

Minimum iOS 16.0, Bundle ID `com.streamlivex.ios`, cihaz ailesi yalnızca iPhone/iPad (`1,2`) olarak tanımlıdır. Geliştirme sunucusu için `Info.plist` içindeki `SLXWebAppURL` HTTPS test adresiyle değiştirilebilir; fiziksel cihazda `localhost` cihazın kendisidir.

## Ağ, güvenlik ve gizlilik

ATS doğrulaması açıktır; genel `NSAllowsArbitraryLoads` kullanılmaz. Web kabuğu yalnızca HTTPS yüklenir. Kullanıcıların mevcut HTTP IPTV medya akışları için Apple'ın medya ile sınırlı `NSAllowsArbitraryLoadsForMedia` anahtarı kullanılır; bu, HTTPS sertifika doğrulamasını kapatmaz ve WKWebView trafiğine uygulanmaz. Kimlik bilgileri ve oynatma URL'leri loglanmaz.

Kamera, mikrofon, konum, fotoğraf ve kişi izni istenmez. Background audio ve Picture in Picture ürün/inceleme kapsamını gereksiz büyütmemek için etkin değildir.

## Deep link

`streamlivex://play?url=https%3A%2F%2Fexample.com%2Fstream.m3u8&title=Sample&kind=live`

Yalnızca HTTP(S) medya URL'leri kabul edilir.

## Windows ve cloud Mac

Windows'ta web/Android kontrolleri ile `powershell -File ios/scripts/validate-project.ps1` çalıştırılabilir. Swift derleme, Simulator, cihaz, signing ve Archive yalnızca macOS/Xcode'dadır. Cloud Mac'te depoyu klonlayıp XcodeGen çalıştırın, ardından `xcodebuild -scheme StreamLiveX -destination 'platform=iOS Simulator,name=iPhone 16' build` ve gerçek iPhone/iPad QA'sını yapın. Signing verilerini yalnızca keychain/CI secret olarak saklayın.

## App Store/yasal inceleme

StreamLiveX içerik sağlamayan oynatıcı olarak sunulmalıdır. İnceleme hesabı ve ekran görüntüleri yalnızca dağıtım hakkı bulunan veya açık lisanslı akış içermelidir. Üçüncü taraf korsan katalog, gömülü liste veya geliştiriciye kullanıcı şifresi aktaran akış eklenmemelidir. App Privacy formunda web uygulaması ve kullanıcının IPTV sağlayıcısının veri uygulamaları ayrı değerlendirilmelidir.

## Manuel QA — iPhone

- Temiz kurulum, liste ekleme, profil, altı dil ve Arapça RTL
- Karanlık/açık/sistem tema ve yeniden açılışta kalıcılık
- Portrait/landscape, notch ve Home Indicator safe area
- Canlı önizleme → tam ekran; HLS, VOD, film ve dizi bölümü
- Oynat/duraklat, ileri/geri sar, gömülü ses/altyazı ve altyazıyı kapatma
- Favori, geçmiş, kaldığı yer; çağrı/ses kesintisi, arka plan/ön plan
- Ağ kesintisi, hatalı URL ve desteklenmeyen codec hatası
- Deep link ve dış bağlantıların Safari/uygun uygulamada açılması

## Manuel QA — iPad

- Tüm boyutlar, portrait/landscape, Split View ve Stage Manager
- Grid/kategori düzeni, dokunma hedefleri ve temel klavye gezinmesi
- Tam ekran aç/kapat, dönüş ve çoklu görev sonrası oynatma
- Profil/dil/tema/kategori durumunun yeniden açılışta korunması

## Yayın öncesi sırası

1. `powershell -File scripts/validate-project.ps1`
2. `xcodegen generate`
3. Xcode Analyze ve uyarı temizliği
4. iPhone/iPad Simulator build ve manuel QA
5. Gerçek cihazda yayın, ses, altyazı, ağ ve kesinti testleri
6. Release signing, Archive, Validate App ve TestFlight
7. App Privacy, yaş derecelendirmesi, destek/gizlilik URL'leri ve yasal demo

## Bilinen iOS sınırları

AVFoundation yalnızca iOS'un desteklediği codec/container kombinasyonlarını oynatır. MKV, bazı MPEG-TS ve özel codec akışları sunucuda HLS/fMP4'e dönüştürülmelidir. Ayrı SRT/ASS dosyaları AVPlayer'a bağımsız parça olarak takılamaz; sağlayıcı bunları HLS WebVTT rendition olarak paketlemelidir. Bu sınırlar TLS doğrulamasını kapatmak için gerekçe değildir.

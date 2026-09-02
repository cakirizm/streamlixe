# StreamLiveX iOS

Bu klasör, mevcut StreamLiveX web arayüzünü iPhone ve iPad'de `WKWebView` içinde çalıştıran, videoyu VLCKit/libVLC ile yerel oynatan iOS uygulamasını içerir. tvOS/Apple TV hedefi yoktur. Android, Android TV ve web bağımsız kalır.

## Mimari

- Branch'e ait iOS web arayüzü `npm run build:ios-web` ile `Resources/Web` altına üretilir ve IPA içinde yerelden yüklenir. Üretim `/app` sayfası görsel kaynak değildir. Kalıcı `WKWebsiteDataStore` localStorage/IndexedDB'yi, profilleri, dil/RTL, tema, favoriler ve kategori konumlarını korur; sunucu gerektiren API uçları HTTPS hizmetine yönlendirilir.
- `NativeBridge`, Android telefon/tablet uygulamasındaki `window.chrome.webview.postMessage` sözleşmesini aynen uygular: `play`, `preview`, `preview-layout`, `promote-preview`, `close` ve `close-preview`.
- `VLCKit` canlı, HLS, ham MPEG-TS, VOD, film ve diziyi doğrudan sağlayıcı URL'sinden oynatır. Yayın, web proxy katmanından geçmez.
- Canlı önizleme ve tam ekran aynı VLCMediaPlayer'ı kullandığından geçişte medya yeniden hazırlanmaz.
- iOS kabuğunda referans tasarıma uygun beş sekmeli mobil navigasyon, portre/landscape ve iPad uyarlaması bulunur. Bu tema `.streamlivex-ios` ile sınırlandığı için Android/TV görünümü değişmez.
- HLS VOD içerikleri `AVAssetDownloadURLSession` ile arka planda indirilebilir, duraklatılabilir, sürdürülebilir, silinebilir ve yerel VLC oynatıcıda çevrimdışı açılabilir. Canlı TV kaydı desteklenmez.
- Ebeveyn PIN'i yalnız iOS Keychain'de cihazla sınırlı, salt+SHA-256 doğrulama verisi olarak saklanır; düz metin PIN web deposuna yazılmaz.

## Xcode projesi ve signing

Güncel Xcode, Node.js, XcodeGen ve CocoaPods gerekir (`brew install xcodegen cocoapods`). macOS'ta depo kökünde önce `npm ci && VITE_UI_BUILD_SHA="$(git rev-parse --short HEAD)" npm run build:ios-web`, ardından `cd ios && xcodegen generate && pod install` komutlarıyla branch UI paketini ve projeyi üretin; `StreamLiveX.xcworkspace` dosyasını açın. Depoda provisioning profili, sertifika veya secret bulunmaz.

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

VLCKit, AVFoundation'a göre daha fazla codec ve container destekler; yine de donanım, bozuk akış, sağlayıcı oturum/IP sınırı veya DRM nedeniyle her kaynak garanti edilemez. HTTP(S) IPTV adresleri cihazdan doğrudan sağlayıcıya gider; TLS doğrulaması kapatılmaz.

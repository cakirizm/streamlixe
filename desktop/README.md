# StreamLiveX Windows uygulaması

Windows uygulaması web sitesinin ikinci bir kopyası değildir. Aynı arayüzü WebView2 ile kullanır; video, ses ve altyazı oynatmasını LibVLC üzerinden yerel olarak yapar. Bu nedenle AC3, EAC3, DTS, HEVC ve tarayıcıların desteklemediği benzer biçimler için ayrıca bir VPS gerekmez.

## Proje yapısı

- `StreamLiveX.Desktop`: WPF, WebView2 ve LibVLC kullanan Windows uygulaması.
- `packaging`: Microsoft Store için MSIX manifesti ve paket görselleri.
- `scripts`: yerel çalıştırma ve Store paketi üretme betikleri.

Web sitesi ve Windows uygulaması aynı GitHub deposunda tutulur. Böylece arayüz değişiklikleri iki farklı projeye kopyalanmaz.

## Bilgisayarda çalıştırma

Web sitesinin geliştirme sunucusunu ilk terminalde açın:

```powershell
npm run dev
```

İkinci PowerShell penceresinde uygulamayı yerel siteye bağlayarak açın:

```powershell
.\desktop\scripts\Run-Desktop.ps1
```

Yayımlanmış siteyi uygulama içinde açmak için:

```powershell
dotnet run --project .\desktop\StreamLiveX.Desktop\StreamLiveX.Desktop.csproj
```

Varsayılan adres `https://streamlivex.com/app` değeridir. Geliştirme adresi `STREAMLIVEX_WEB_URL` ortam değişkeniyle değiştirilir.

## Store paketi

Önce Microsoft Partner Center üzerinde uygulama adı ayrılır. Partner Center'ın verdiği `Identity Name` ve `Publisher` değerleri aşağıdaki komuta yazılır:

```powershell
.\desktop\scripts\Build-StorePackage.ps1 `
  -IdentityName "PARTNER_CENTER_IDENTITY" `
  -Publisher "CN=PARTNER_CENTER_PUBLISHER" `
  -Version "1.0.0.0" `
  -Architecture x64
```

Betik self-contained Windows çıktısını ve `.msix` paketini `desktop/artifacts` altında oluşturur. `MakeAppx.exe` bulunamazsa Visual Studio Installer üzerinden **MSIX Packaging Tools** ve güncel **Windows SDK** bileşenleri yüklenmelidir.

Store'a gönderilecek son paketin Partner Center kimliğiyle oluşturulması ve Store tarafından imzalanması gerekir. Manifestteki örnek kimlik yayın için kullanılmamalıdır.

## Özel bağlantı

MSIX kurulduğunda aşağıdaki bağlantı uygulamayı doğrudan oynatıcı ekranında açabilir:

```text
streamlivex://play?url=HTTPS_YAYIN_ADRESI&title=Film%20Adi&kind=movie
```

Yayın URL'si yalnızca `http` veya `https` olabilir.

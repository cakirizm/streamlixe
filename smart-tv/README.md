# StreamLiveX — Smart TV (LG webOS + Samsung Tizen)

LG (webOS) ve Samsung (Tizen) TV'ler Android değildir; uygulamayı **paketlenmiş web
uygulaması** olarak çalıştırır. Bu klasör, mevcut React web arayüzünü bu iki platforma
paketlemek için gerekli iskeleti içerir.

- LG webOS → `.ipk`  (paketleyici: `ares-package`)
- Samsung Tizen → `.wgt`  (paketleyici: Tizen CLI + sertifika)

## Klasör yapısı
```
smart-tv/
  webos/        webOS uygulama kaynağı (appinfo.json, index.html, ikonlar)
  tizen/        Tizen uygulama kaynağı (config.xml, index.html, ikon)
  scripts/
    build-webos.sh   webOS .ipk üretir → smart-tv/dist/
  dist/         üretilen paketler (git'e girmez)
```

## Mimari (önemli)
Web uygulaması Vinext/Cloudflare Worker tabanlı, **sunucu taraflı** bir uygulamadır ve
veriyi göreli `/api/*` route'ları üzerinden alır. TV paketi ise saf istemcidir. Bu yüzden
TV sürümünde:

1. **API tabanı** `/api/*` yerine `https://streamlivex.com/api/*` olmalı
   (tmdb / import / pair çağrıları Cloudflare'den sorunsuz çalışır).
2. **Yayın oynatma doğrudan** yapılmalı: TV residential IP'de olduğundan, native uygulamada
   olduğu gibi **ham yayın URL'si TV'nin kendi oynatıcısında** açılır; `/api/stream` proxy'si
   atlanır (proxy datacenter IP'den 404 veriyor).

## SPA yapısı
`smart-tv/spa/` — TV'ye özgü saf istemci girişi:
- `main.tsx` — PlayerApp'i mount eder; `/api/*` çağrılarını `https://streamlivex.com`'a yönlendirir
  ve doğrudan (ham URL) oynatma bayrağını açar.
- `index.html` — SPA giriş sayfası.
- Derleme: kökteki `vite.tv.config.ts` (Vinext/RSC'den ayrı, saf istemci Vite derlemesi).

PlayerApp'te iki geriye-uyumlu kanca eklendi (global tanımsızsa davranış aynen korunur):
`window.__SLX_PROXY_ORIGIN__` (proxy origin override) ve `window.__SLX_TV_DIRECT__` (doğrudan oynatma).

## Durum
- [x] webOS CLI (`@webosose/ares-cli`) kuruldu.
- [x] webOS iskeleti + `.ipk` paketleme pipeline'ı çalışıyor.
- [x] Tizen iskeleti (`config.xml`) hazır.
- [x] Gerçek SPA build'i pakete gömülüyor (API tabanı streamlivex.com + doğrudan oynatma).
- [x] Kök-mutlak varlık (`/streamlivex-logo.jpeg`) `publicDir` ile paket köküne gömülüyor
      (emülatörde origin şeması `file://` çıkarsa runtime yeniden yazma ile doğrulanacak).
- [ ] Tizen `.wgt` paketleme (Tizen Studio + sertifika).
- [ ] webOS TV Emulator / Tizen TV Emulator üzerinde test.
- [ ] LG Seller Lounge / Samsung Seller Office gönderimi.

## webOS paketi üretme (gerçek SPA ile)
`build-webos.sh` artık önce SPA'yı derler, staging'de metadata ile birleştirir, sonra paketler.

## webOS paketi üretme
```bash
bash smart-tv/scripts/build-webos.sh
# çıktı: smart-tv/dist/com.streamlivex.app_1.0.0_all.ipk
```

## Tizen paketi (sonraki adım)
Tizen Studio (GUI installer) + TV extensions gerekir; author/distributor sertifikası
Certificate Manager'da üretilir, ardından `tizen build-web` + `tizen package -t wgt` ile
imzalı `.wgt` oluşur.

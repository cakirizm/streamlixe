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

## Durum
- [x] webOS CLI (`@webosose/ares-cli`) kuruldu.
- [x] webOS iskeleti + `.ipk` paketleme pipeline'ı çalışıyor (placeholder önyükleme ekranı).
- [x] Tizen iskeleti (`config.xml`) hazır.
- [ ] Gerçek SPA build'inin pakete gömülmesi (API tabanı + doğrudan oynatma adaptasyonu).
- [ ] webOS TV Emulator / Tizen TV Emulator üzerinde test.
- [ ] LG Seller Lounge / Samsung Seller Office gönderimi.

## webOS paketi üretme
```bash
bash smart-tv/scripts/build-webos.sh
# çıktı: smart-tv/dist/com.streamlivex.app_1.0.0_all.ipk
```

## Tizen paketi (sonraki adım)
Tizen Studio (GUI installer) + TV extensions gerekir; author/distributor sertifikası
Certificate Manager'da üretilir, ardından `tizen build-web` + `tizen package -t wgt` ile
imzalı `.wgt` oluşur.

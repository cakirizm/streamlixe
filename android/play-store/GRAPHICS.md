# Play Console görsel varlıkları

Google Play mağaza kaydı için gereken grafiklerin tam listesi. Boyutlar Play
Console'un 2026 gereksinimlerine göredir. Görselleri hazırlayıp aşağıdaki
klasörlere (fastlane `supply` düzeni) koyabilir **veya** doğrudan Play Console
arayüzünden yükleyebilirsiniz.

Dosyalar `metadata/android/<dil>/images/` altına konur. Görseller diller arası
ortaksa yalnızca varsayılan dile (`tr`) koymanız yeterlidir; Play tüm dillerde
onu kullanır.

## Her uygulama için zorunlu (telefon/tablet + TV ortak)

| Varlık | Boyut | Format | Klasör / dosya |
|---|---|---|---|
| Uygulama simgesi (yüksek çöz.) | 512 × 512 | 32-bit PNG (alfa) | `images/icon.png` |
| Öne çıkan grafik (feature graphic) | 1024 × 500 | PNG/JPG (alfa yok) | `images/featureGraphic.png` |

> Simge: `android/app/src/main/res/mipmap-*` içindeki launcher simgesiyle görsel
> olarak tutarlı olmalı. Öne çıkan grafik TV mağazasında da vitrin görselidir.

## Telefon ve tablet (mobil kayıt)

| Varlık | Adet | Boyut | Klasör |
|---|---|---|---|
| Telefon ekran görüntüsü | 2–8 | kısa kenar ≥ 320 px, uzun/kısa oran ≤ 2:1 (ör. 1080 × 1920) | `images/phoneScreenshots/` |
| 7" tablet ekran görüntüsü | en az 1 (önerilir) | ör. 1200 × 1920 | `images/sevenInchScreenshots/` |
| 10" tablet ekran görüntüsü | en az 1 (önerilir) | ör. 1600 × 2560 | `images/tenInchScreenshots/` |

## Android TV / Google TV (zorunlu — TV kaydı bunlar olmadan yayınlanmaz)

| Varlık | Adet | Boyut | Klasör / dosya |
|---|---|---|---|
| TV afişi (banner) | 1 | 1280 × 720 (16:9) | `images/tvBanner.png` |
| TV ekran görüntüsü | 1–8 | 1920 × 1080 (yatay, 16:9) | `images/tvScreenshots/` |

> Uygulama TV afişini `android:banner="@drawable/tv_banner"` ile manifestte
> zaten bildiriyor. Play Console için ayrıca 1280×720 PNG afiş yüklenmelidir.
> TV ekran görüntüleri gerçek Leanback arayüzünden alınmalı (kumandayla gezilen
> ana ekran, canlı TV, oynatıcı).

## İsteğe bağlı

| Varlık | Boyut |
|---|---|
| Tanıtım videosu (YouTube linki) | — (`metadata/android/<dil>/video.txt` içine URL) |

## Dosya yerleşimi örneği

```
play-store/metadata/android/
├── tr/
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── changelogs/1.txt
│   └── images/
│       ├── icon.png
│       ├── featureGraphic.png
│       ├── tvBanner.png
│       ├── phoneScreenshots/1_home.png …
│       ├── sevenInchScreenshots/1.png …
│       ├── tenInchScreenshots/1.png …
│       └── tvScreenshots/1_leanback.png …
├── en-US/ …
├── de-DE/ …
├── es-ES/ …
├── fr-FR/ …
└── ar/ …
```

Bu depoya `.png/.jpg` görseller eklenmez (yalnızca metin şablonları tutulur);
görselleri yerelde bu klasörlere koyup Play Console'a yükleyin.

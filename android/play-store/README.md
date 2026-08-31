# play-store — Google Play mağaza varlıkları

Bu klasör StreamLiveX'in Google Play kaydı için hazır şablonları tutar.
Yükleme adımlarının tamamı için: [`../PUBLISHING.md`](../PUBLISHING.md).

## Yapı

```
play-store/
├── GRAPHICS.md                     # zorunlu görsellerin boyut/format listesi
└── metadata/android/<dil>/         # fastlane `supply` uyumlu metin şablonları
    ├── title.txt                   # ≤ 30 karakter
    ├── short_description.txt       # ≤ 80 karakter
    ├── full_description.txt        # ≤ 4000 karakter
    └── changelogs/<versionCode>.txt
```

Diller: `tr` (varsayılan), `en-US`, `de-DE`, `es-ES`, `fr-FR`, `ar` — uygulamanın
desteklediği 6 dille aynı.

Metinleri ya Play Console arayüzüne elle yapıştırın ya da `fastlane supply` ile
otomatik yükleyin (bkz. PUBLISHING.md §8). Görsel dosyaları (`.png/.jpg`) depoya
eklenmez; GRAPHICS.md'deki klasörlere yerelde koyup yükleyin.

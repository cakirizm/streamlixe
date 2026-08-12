# StreamLiveX – Claude Code çalışma notları

Bu depo, React 19 + Vinext/Vite tabanlı bir IPTV web oynatıcısıdır.

## Geliştirme komutları

- `docker compose up --build`: geliştirme sunucusunu açar.
- `npm run build`: üretim derlemesini doğrular.
- `npm test`: derleme ve HTML kontrollerini çalıştırır.
- `docker compose -f docker-compose.prod.yml up --build`: üretim imajını çalıştırır.

## Önemli dosyalar

- `app/page.tsx`: ana uygulama, IPTV kütüphanesi ve oynatıcı arayüzü.
- `app/globals.css`: tüm responsive tasarım.
- `app/i18n.tsx`: Türkçe, İngilizce, Arapça, Almanca, Fransızca ve İspanyolca çeviriler.
- `app/api/import/route.ts`: M3U, Xtream ve EPG veri alma katmanı.
- `app/api/stream/route.ts`: yayın ve görsel proxy katmanı.
- `app/api/tmdb/route.ts`: TMDB metadata katmanı.

## Değişiklik kuralları

1. IPTV kullanıcı adı, parola, MAC adresi veya API anahtarı kaynak koda yazılmamalı.
2. `TMDB_TOKEN` yalnızca `.env` üzerinden okunmalı.
3. Canlı TV, film ve dizi kategori konum hafızaları korunmalı.
4. Arapça RTL düzeni ve altı dil desteği bozulmamalı.
5. Her değişiklikten sonra en az `npm run build` çalıştırılmalı.
6. Tarayıcıların H.265/HEVC desteğinin sınırlı olduğu unutulmamalı; başarısız kaynağın nedeni kullanıcıya doğru anlatılmalı.

## Claude Code için başlangıç istemi

> Bu StreamLiveX IPTV projesini incele. Önce CLAUDE.md ve README_DOCKER.md dosyalarını oku. İstenen değişikliği mevcut mimariyi, altı dil desteğini, profil bazlı tercihleri ve kategori konum hafızasını bozmadan uygula. İş bitince npm run build çalıştır ve sonucu bildir.

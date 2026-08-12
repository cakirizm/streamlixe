# StreamLiveX Docker paketi

Bu paket, StreamLiveX kaynak kodunu Docker içinde çalıştırmak ve Claude Code ile geliştirmek için hazırlanmıştır.

## Gereksinimler

- Docker Desktop veya Docker Engine
- Docker Compose v2
- TMDB API anahtarı

## İlk kurulum

1. Arşivi açın ve proje klasörüne girin.
2. `.env.example` dosyasını `.env` adıyla kopyalayın.
3. `.env` içindeki `TMDB_TOKEN` değerini kendi anahtarınızla değiştirin.
4. Aşağıdaki komutu çalıştırın:

```bash
docker compose up --build
```

5. Tarayıcıdan `http://localhost:3000` adresini açın.

Kodda yaptığınız değişiklikler geliştirme konteynerine anında yansır. Durdurmak için `Ctrl+C`, tamamen kapatmak için:

```bash
docker compose down
```

## Üretim biçiminde çalıştırma

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

## Claude Code ile geliştirme

Proje klasöründe Claude Code'u açın. `CLAUDE.md` dosyası mimariyi, önemli dosyaları ve koruması gereken davranışları Claude'a açıklar.

Örnek istek:

```text
CLAUDE.md dosyasını oku. Canlı TV oynatıcısındaki kaynak değiştirme arayüzünü geliştir, mevcut kategori konum hafızasını ve altı dil desteğini koru. Sonunda npm run build ile doğrula.
```

## Güvenlik

- `.env` dosyasını paylaşmayın veya Git'e göndermeyin.
- IPTV kullanıcı bilgileri tarayıcıdaki yerel depolamada tutulabilir; ortak cihazlarda gerçek hesap kullanırken dikkatli olun.
- Yalnızca erişim hakkınız bulunan yayın listelerini kullanın.

## Not

Tarayıcılar VLC değildir. H.265/HEVC, bazı MPEG-TS akışları, CORS engelli veya süresi dolmuş yayınlar tarayıcıda açılamayabilir. Docker kullanmak tarayıcının codec desteğini değiştirmez.

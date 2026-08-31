# Product Marketing Context

**Document version:** v1
**Last updated:** 2026-08-21

> Not: Proje henüz geliştirme aşamasında. Amaç ve konumlandırma net; ürün detayları
> olgunlaştıkça bu doküman güncellenecek. `[TBD]` işaretli alanlar sonra doldurulacak.

## Product Overview
**One-liner:** Kendi IPTV aboneliğini getir, StreamLiveX ile her cihazda izle.
**What it does:** M3U, Xtream Codes ve EPG bilgilerini içe aktaran; canlı TV, film ve
dizileri TMDB posterleri/özetleriyle zenginleştirilmiş bir arayüzde oynatan çok platformlu
IPTV istemcisi. Web ve Android (Capacitor) üzerinde çalışır; HLS ve MPEG-TS destekler.
**Product category:** IPTV oynatıcı / istemci uygulaması (BYO — bring your own subscription).
**Product type:** SaaS / uygulama (içerik sağlamaz; kullanıcının kendi aboneliğini oynatır).
**Business model:** Uygulama erişimi için abonelik (aylık/yıllık). Fiyat: [TBD].

## Target Audience
**Target companies:** Yok — doğrudan tüketici (B2C).
**Decision-makers:** Son kullanıcı = karar verici (evde IPTV izleyen kişi).
**Primary use case:** Elindeki IPTV aboneliğini (M3U/Xtream) tek, düzgün ve çok dilli bir
arayüzde; telefon, tablet, TV ve web üzerinde sorunsuz izlemek.
**Jobs to be done:**
- "IPTV aboneliğimi kolayca kurup hemen izlemeye başlamak istiyorum."
- "Dizi/filmleri poster ve özetleriyle, dağınık kanal listesi yerine düzenli görmek istiyorum."
- "Kaldığım bölümden/kanaldan devam etmek, tercihlerimin hatırlanmasını istiyorum."
**Use cases:**
- Yurt dışındaki izleyici kendi ülkesinin kanallarını ana dilinde bir arayüzde izliyor (6 dil, Arapça RTL).
- Kullanıcı Xtream bilgisini girip canlı TV + VOD kütüphanesini tek uygulamada topluyor.
- Aynı hesabı farklı cihazlarda (web + Android) kullanıyor.

## Personas
*B2C — tek persona. Detaylı ayrım gerekmiyor.*
| Persona | Cares about | Challenge | Value we promise |
|---------|-------------|-----------|------------------|
| IPTV izleyicisi | Sorunsuz oynatma, düzenli/güzel arayüz, kendi dili | Kötü/karışık oynatıcılar, bozuk yayınlar, dil sorunu | Kurulumu kolay, çok dilli, poster destekli, akıcı bir izleme deneyimi |

## Problems & Pain Points
**Core problem:** Kullanıcının IPTV aboneliği var ama iyi bir oynatıcısı yok — mevcut
uygulamalar çirkin, karmaşık, sık çöküyor veya kendi diline/cihazına uymuyor.
**Why alternatives fall short:**
- Arayüzler dağınık ve teknik; sıradan izleyici için zor.
- Poster/özet gibi metadata çoğu zaman yok; sadece düz kanal listeleri.
- Dil desteği ve RTL sınırlı.
- Yayın başarısızlıklarının nedeni (ör. tarayıcı HEVC sınırı) kullanıcıya açıklanmıyor.
**What it costs them:** Boşa geçen zaman, izleyememe stresi, "bozuk mu?" belirsizliği.
**Emotional tension:** "Parasını verdiğim aboneliği düzgün izleyemiyorum" hayal kırıklığı.

## Competitive Landscape
**Direct:** IPTV Smarters, TiviMate — güçlü ama arayüzleri teknik/karmaşık; bazıları tek platforma veya kilitli ekosisteme bağlı.
**Direct:** XCIPTV, Perfect Player — işlevsel ama eski/az cilalı arayüz, sınırlı metadata ve dil.
**Secondary:** Sağlayıcının verdiği jenerik M3U/web oynatıcılar — temel, hafızasız, dağınık.
**Indirect:** Netflix/Disney+ gibi platformlar — StreamLiveX'in izleyiciye hatırlattığı "olması gereken deneyim" standardı (rakip değil ama beklenti çıtası).

## Differentiation
**Key differentiators:**
- Tek kod tabanından hem web hem Android (Capacitor) — cihazdan bağımsız izleme.
- TMDB entegrasyonu: dizi/film poster + özet ile platform-benzeri zengin arayüz.
- 6 dil (TR, EN, AR, DE, FR, ES) + tam Arapça RTL.
- Profil bazlı tercihler + kategori konum hafızası + "İzlemeye devam et".
- Yayın başarısızlığında nedeni doğru anlatma (ör. tarayıcı HEVC sınırı).
**How we do it differently:** Teknik bir oynatıcı yerine, sıradan izleyici için tasarlanmış,
çok dilli ve metadata-zengin bir deneyim.
**Why that's better:** Kurulum kolay, arayüz tanıdık, kendi dilinde ve her cihazda çalışıyor.
**Why customers choose us:** [TBD — ürün olgunlaşınca gerçek kullanıcı gerekçesiyle doldur.]

## Objections
| Objection | Response |
|-----------|----------|
| "Zaten bir oynatıcım var." | StreamLiveX çok dilli, poster destekli ve web+Android; sadece liste değil, düzenli bir deneyim. |
| "Kurulumu zor mudur?" | M3U/Xtream bilgisini gir, gerisi otomatik; kaldığın yerden devam eder. |
| "Yayın bozuk oynatıyor." | Sorun genelde sağlayıcı/format (ör. tarayıcı HEVC sınırı); uygulama nedeni açıkça söyler, native tarafta ham URL oynatır. |

**Anti-persona:** İçerik/abonelik arayan kişi — StreamLiveX içerik sağlamaz, yalnızca kullanıcının kendi aboneliğini oynatır.

## Switching Dynamics
**Push:** Mevcut oynatıcı çirkin/karmaşık, çöküyor, dilini desteklemiyor.
**Pull:** Temiz çok dilli arayüz, poster/özet, her cihazda çalışma, hafıza özellikleri.
**Habit:** Alışılmış eski uygulamayı bırakmama, kurulumu yeniden yapma üşengeçliği.
**Anxiety:** "Aboneliğim bununla çalışır mı? Bilgilerimi girmek güvenli mi?"

## Customer Language
**How they describe the problem:**
- "[TBD — gerçek kullanıcı ifadeleri toplanacak]"
**How they describe us:**
- "[TBD]"
**Words to use:** izle, kolay kurulum, kaldığın yerden devam, kendi dilinde, her cihazda
**Words to avoid:** "içerik sağlıyoruz", "kanal satıyoruz" (yasal/konumlandırma açısından yanlış)
**Glossary:**
| Term | Meaning |
|------|---------|
| M3U | Kanal/yayın listesi dosya formatı |
| Xtream Codes | Kullanıcı adı/şifre ile bağlanılan IPTV panel API'si |
| EPG | Elektronik yayın rehberi (program akışı) |
| HLS / MPEG-TS | Yayın oynatma protokolleri/formatları |
| BYO | Bring Your Own — kullanıcı kendi aboneliğini getirir |

## Brand Voice
**Tone:** Sade, güven veren, teknik olmayan.
**Style:** Doğrudan ve yardımcı; izleyiciyle konuşur, mühendisle değil.
**Personality:** Güvenilir, modern, çok kültürlü, pürüzsüz, yardımsever.

## Proof Points
**Metrics:** [TBD]
**Customers:** [TBD]
**Testimonials:** [TBD]
**Value themes:**
| Theme | Proof |
|-------|-------|
| Kolay kurulum | M3U/Xtream/EPG içe aktarma akışı |
| Zengin arayüz | TMDB poster + özet entegrasyonu |
| Herkes için | 6 dil + Arapça RTL |
| Kaldığın yerden | Profil tercihleri + kategori hafızası + "devam et" |
| Her cihazda | Web + Android (Capacitor), HLS + MPEG-TS |

## Goals
**Business goal:** Ücretli abone sayısını büyütmek (uygulama aboneliği).
**Conversion action:** Kullanıcının aboneliği girip ilk yayını başarıyla oynatması → ücretli plana geçmesi.
**Current metrics:** [TBD — ürün henüz yayında değil.]

## Changelog
*Newest first. One line per revision: what changed and why.*
- v1 (2026-08-21) — Initial context. BYO IPTV oynatıcı olarak konumlandırma; B2C son kullanıcı kitlesi; uygulama aboneliği modeli; rakipler IPTV Smarters/TiviMate/XCIPTV/Perfect Player. Ürün detayları koddan (React 19, Capacitor, TMDB, 6 dil) çıkarıldı; strateji alanları kullanıcı yanıtlarından. Metrik/testimonial/gerçek kullanıcı dili [TBD].

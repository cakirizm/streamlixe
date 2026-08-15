import type { Metadata } from "next";
import { LegalPage } from "../../legal-page";

export const metadata: Metadata = {
  title: "IPTV Oynatıcı Seçerken Nelere Dikkat Edilmeli?",
  description: "Codec desteği, çoklu profil, EPG ve platform uyumluluğu gibi kriterlerle doğru IPTV oynatıcısını seçme rehberi.",
  alternates: { canonical: "/rehber/iptv-oynatici-secimi" },
};

export default function IptvOynaticiSecimiPage() {
  return (
    <LegalPage
      eyebrow="REHBER"
      title="IPTV Oynatıcı Seçerken Nelere Dikkat Edilmeli?"
      summary="Bir IPTV oynatıcısı seçerken göz önünde bulundurmanız gereken teknik kriterleri derledik."
    >
      <section>
        <h2>1. Codec ve format desteği</h2>
        <p>
          Tarayıcı tabanlı oynatıcıların çoğu HEVC (H.265), AC3, EAC3 ve DTS gibi ses/görüntü biçimlerini doğrudan
          oynatamaz. Bu formatları kullanan yayınlar için, tarayıcının sınırlarını aşan <strong>native bir oynatıcı
          motoruna</strong> (örneğin LibVLC) sahip bir uygulama tercih etmek, "yayın açılmıyor" veya "ses gelmiyor" gibi
          sorunları büyük ölçüde azaltır.
        </p>
      </section>
      <section>
        <h2>2. Birden fazla bağlantı yöntemi desteği</h2>
        <p>
          Sağlayıcılar genellikle M3U, Xtream Codes veya Stalker/MAC Portal yöntemlerinden birini sunar. Bu üç yöntemi de
          destekleyen bir oynatıcı, hangi sağlayıcıyı kullanırsanız kullanın uyumluluk sorunu yaşamamanızı sağlar.
        </p>
      </section>
      <section>
        <h2>3. Kategori düzenleme ve arama</h2>
        <p>
          Binlerce kanal/film/dizi içeren büyük listelerde, içeriklerin otomatik olarak kategorilere ayrılması ve hızlı
          arama yapılabilmesi kullanılabilirliği doğrudan etkiler. İyi bir oynatıcı, ham listeyi olduğu gibi değil,
          düzenli ve gezinmesi kolay bir arayüzde sunar.
        </p>
      </section>
      <section>
        <h2>4. Zengin meta veri (TMDB gibi entegrasyonlar)</h2>
        <p>
          Film ve dizi adlarının yanında afiş, özet, oyuncu ve puan bilgisi görebilmek, "hangi filmi izleyeceğim"
          kararını çok kolaylaştırır. TMDB gibi bir veri kaynağıyla entegre çalışan oynatıcılar bu bilgiyi otomatik
          olarak eşleştirir.
        </p>
      </section>
      <section>
        <h2>5. Çoklu profil ve favoriler</h2>
        <p>
          Aynı hesabı birden fazla kişi (örneğin aile üyeleri) kullanacaksa, her biri için ayrı profil, izleme geçmişi
          ve favori listesi tutabilen bir oynatıcı deneyimi büyük ölçüde iyileştirir.
        </p>
      </section>
      <section>
        <h2>6. Platform uyumluluğu</h2>
        <p>
          Sadece bilgisayardan değil; telefon, Android TV veya farklı cihazlardan da izlemeyi planlıyorsanız,
          oynatıcının web, Windows, Android ve Android TV gibi birden fazla platformda tutarlı bir deneyim sunması
          önemlidir.
        </p>
      </section>
      <section>
        <h2>7. Gizlilik ve veri sorumluluğu</h2>
        <p>
          Oynatıcının, IPTV hesap bilgilerinizi nasıl işlediğini/sakladığını açıkça belirten bir gizlilik politikası
          olmalıdır. Ayrıca iyi bir oynatıcı, kendisi içerik sağlamaz — yalnızca sizin kendi yasal olarak edindiğiniz
          kaynaklara bağlanan bir istemci olarak çalışır.
        </p>
        <p>
          Bağlantı yöntemleri hakkında daha fazla bilgi için <a href="/rehber/m3u-nedir">M3U nedir</a> ve
          <a href="/rehber/xtream-codes-nedir"> Xtream Codes nedir</a> rehberlerimize göz atabilirsiniz.
        </p>
      </section>
    </LegalPage>
  );
}

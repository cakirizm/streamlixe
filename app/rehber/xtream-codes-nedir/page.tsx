import type { Metadata } from "next";
import { LegalPage } from "../../legal-page";

export const metadata: Metadata = {
  title: "Xtream Codes Nedir? Nasıl Bağlanılır?",
  description: "Xtream Codes API'nin çalışma mantığı, sunucu adresi, kullanıcı adı ve şifre bilgileriyle bir IPTV oynatıcısına nasıl bağlanılır? Kısa ve teknik bir rehber.",
  alternates: { canonical: "/rehber/xtream-codes-nedir" },
};

export default function XtreamCodesNedirPage() {
  return (
    <LegalPage
      eyebrow="REHBER"
      title="Xtream Codes Nedir? Nasıl Bağlanılır?"
      summary="Xtream Codes API'sinin ne olduğunu ve bir oynatıcıya nasıl bağlanacağınızı kısaca açıklıyoruz."
    >
      <section>
        <h2>Xtream Codes nedir?</h2>
        <p>
          Xtream Codes, IPTV panellerinin yaygın olarak kullandığı bir <strong>API (uygulama programlama arayüzü)</strong>
          standardıdır. M3U dosyalarının aksine, Xtream Codes tüm listeyi tek seferde indirmek yerine; sunucu adresi, kullanıcı
          adı ve şifre bilgileriyle sunucuya bağlanıp kanal, film, dizi ve kategori bilgilerini talep üzerine (canlı olarak)
          çeker. Bu sayede büyük kütüphaneler daha esnek şekilde yönetilebilir ve güncellenebilir.
        </p>
      </section>
      <section>
        <h2>Bağlanmak için hangi bilgiler gerekir?</h2>
        <p>Bir Xtream Codes hesabına bağlanmak için genellikle üç bilgi istenir:</p>
        <ul>
          <li><strong>Sunucu adresi</strong> — genelde <code>http://sunucu-adresi:port</code> biçiminde bir URL</li>
          <li><strong>Kullanıcı adı</strong></li>
          <li><strong>Şifre</strong></li>
        </ul>
        <p>
          Bu bilgiler, hesabı size sağlayan IPTV sağlayıcısı tarafından verilir. StreamLiveX bu bilgileri üretmez, satmaz veya
          sağlamaz; yalnızca sizin girdiğiniz bilgilerle sunucuya bağlanan bir istemcidir.
        </p>
      </section>
      <section>
        <h2>Xtream Codes ile M3U arasındaki fark nedir?</h2>
        <p>
          M3U statik bir dosya/liste iken, Xtream Codes API üzerinden dinamik olarak sorgulanan bir yapıdır. Xtream Codes
          genellikle kategori bazlı gezinme, ayrı canlı TV / film / dizi bölümleri ve daha zengin meta veri (kapak görseli,
          açıklama gibi) sunar. Bazı sağlayıcılar aynı hesap için hem M3U hem Xtream Codes erişimi sağlayabilir.
        </p>
      </section>
      <section>
        <h2>StreamLiveX'te Xtream Codes ile nasıl bağlanılır?</h2>
        <p>
          Başlangıç ekranında "Xtream Codes" sekmesini seçin; sunucu adresini, kullanıcı adınızı ve şifrenizi girip
          "Oynatma listesini ekle" butonuna basın. StreamLiveX, hesabınızdaki canlı TV kanallarını, filmleri ve dizileri
          otomatik olarak çekip kategorilere ayırır ve TMDB verileriyle zenginleştirir.
        </p>
        <p>
          Büyük hesaplarda (binlerce kanal/film içeren) bu işlem biraz zaman alabilir — sayfayı kapatmadan işlemin
          tamamlanmasını bekleyin.
        </p>
        <p>
          M3U tabanlı bağlantılar için <a href="/rehber/m3u-nedir">M3U rehberimize</a>, genel oynatıcı seçim kriterleri için
          <a href="/rehber/iptv-oynatici-secimi"> IPTV oynatıcı seçim rehberimize</a> göz atabilirsiniz.
        </p>
      </section>
    </LegalPage>
  );
}

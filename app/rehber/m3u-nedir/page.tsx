import type { Metadata } from "next";
import { LegalPage } from "../../legal-page";

export const metadata: Metadata = {
  title: "M3U Nedir? M3U Oynatma Listesi Nasıl Kullanılır?",
  description: "M3U ve M3U8 dosya biçimi ne işe yarar, içeriği nasıl okunur ve bir M3U bağlantısı StreamLiveX gibi bir oynatıcıya nasıl eklenir? Teknik ve kısa bir rehber.",
  alternates: { canonical: "/rehber/m3u-nedir" },
};

export default function M3uNedirPage() {
  return (
    <LegalPage
      eyebrow="REHBER"
      title="M3U Nedir? M3U Oynatma Listesi Nasıl Kullanılır?"
      summary="M3U/M3U8 dosya biçiminin ne olduğunu, nasıl çalıştığını ve bir oynatıcıya nasıl ekleneceğini kısaca açıklıyoruz."
    >
      <section>
        <h2>M3U dosyası nedir?</h2>
        <p>
          M3U (Moving Picture Experts Group Audio Layer 3 Uniform Resource Locator), aslında bir video ya da ses dosyası değil,
          düz metin tabanlı bir <strong>oynatma listesi biçimidir</strong>. İçinde tek tek medya dosyalarının veya canlı yayın
          adreslerinin (URL) bir listesi bulunur. Bir M3U dosyasını herhangi bir metin düzenleyiciyle açtığınızda, satır satır
          kanal adları ve bu kanallara ait bağlantıları görürsünüz.
        </p>
        <p>
          M3U8, aynı formatın UTF-8 karakter kodlamasını kullanan sürümüdür ve günümüzde IPTV ile HLS (HTTP Live Streaming)
          yayınlarında en sık karşılaşılan biçimdir. Pratikte "M3U" ve "M3U8" çoğu zaman birbirinin yerine kullanılır.
        </p>
      </section>
      <section>
        <h2>Bir M3U dosyasının içeriği nasıl görünür?</h2>
        <p>
          Basit bir M3U dosyası şu şekilde bir yapıya sahiptir: dosya <code>#EXTM3U</code> satırıyla başlar, ardından her kanal
          için bir <code>#EXTINF</code> satırı (kanal adı, logosu ve kategorisi gibi bilgileri içerir) ve hemen altında o kanalın
          gerçek yayın adresi (URL) yer alır. Oynatıcı yazılımı bu dosyayı okuyup kanalları/içerikleri düzenli bir liste
          hâlinde kullanıcıya sunar.
        </p>
      </section>
      <section>
        <h2>M3U listesi nereden gelir?</h2>
        <p>
          M3U listeleri iki şekilde elde edilir: doğrudan bir <strong>.m3u/.m3u8 dosyası</strong> olarak indirilir, ya da bir
          <strong> M3U bağlantısı (URL)</strong> şeklinde sağlanır ve oynatıcı bu adresi periyodik olarak sorgulayarak listeyi
          günceller. Liste, kullanıcının kendi yasal olarak edindiği bir IPTV aboneliğinden, kendi kaydettiği yayınlardan ya da
          serbestçe paylaşılan içeriklerden gelebilir — kaynağın yasallığı ve kullanım hakları tamamen listeyi sağlayan tarafa
          ve kullanıcıya aittir.
        </p>
      </section>
      <section>
        <h2>StreamLiveX'te M3U nasıl eklenir?</h2>
        <p>
          StreamLiveX'in başlangıç ekranında "M3U" sekmesini seçip elinizdeki M3U bağlantısını yapıştırabilir veya bilgisayarınızdaki
          bir .m3u/.m3u8 dosyasını doğrudan seçebilirsiniz. Uygulama, listeyi okuyup kanal, film ve dizileri otomatik olarak
          kategorilere ayırır; TMDB entegrasyonu sayesinde film ve dizileriniz için afiş, özet ve puan bilgisi de eklenir.
        </p>
        <p>
          Daha fazla bilgi için <a href="/rehber/iptv-oynatici-secimi">IPTV oynatıcı seçim rehberimize</a> veya
          Xtream Codes tabanlı bağlantılar için <a href="/rehber/xtream-codes-nedir">Xtream Codes rehberimize</a> göz atabilirsiniz.
        </p>
      </section>
    </LegalPage>
  );
}

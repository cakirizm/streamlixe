import type { Metadata } from "next";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Gizlilik Politikası",
  description: "StreamLiveX gizlilik politikası ve kullanıcı verilerinin işlenmesine ilişkin açıklamalar.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <LegalPage eyebrow="GİZLİLİK VE VERİ GÜVENLİĞİ" title="Gizlilik Politikası" summary="Bu politika, StreamLiveX web sitesi ve Windows uygulaması kullanılırken hangi bilgilerin neden işlendiğini açıklar.">
      <section>
        <h2>1. StreamLiveX ne sağlar?</h2>
        <p><strong>StreamLiveX yalnızca bir medya oynatıcı ve kişisel kütüphane düzenleme aracıdır.</strong> StreamLiveX hiçbir IPTV aboneliği, kanal paketi, oynatma listesi, portal adresi, kullanıcı adı, şifre, film veya dizi sağlamaz; satmaz ve dağıtmaz.</p>
        <p>Kullanıcı, yalnızca kendisine ait veya kullanmaya yetkili olduğu M3U dosyasını, M3U bağlantısını ya da Xtream erişim bilgilerini kendi isteğiyle girer. Bu bilgilerin doğruluğundan, kullanım yetkisinden ve bağlı olduğu hizmetten kullanıcı sorumludur.</p>
      </section>
      <section>
        <h2>2. Cihazda saklanan bilgiler</h2>
        <p>Oynatma listesi, sağlayıcı profili, uygulama dili, favoriler, izleme geçmişi, kaldığınız yer, altyazı ve oynatıcı tercihleri gibi bilgiler uygulamanın çalışabilmesi için tarayıcı veya uygulama içindeki yerel depolama alanında saklanabilir.</p>
        <p>Bu bilgiler StreamLiveX hesabı altında merkezi bir kullanıcı veritabanına kaydedilmez. Uygulamadaki “Yeni oynatma listesi ekle”, “Geçmişi temizle” ve tarayıcı veri temizleme seçenekleri kullanılarak cihazdaki kayıtlar kaldırılabilir.</p>
      </section>
      <section>
        <h2>3. Bağlantı sırasında işlenen bilgiler</h2>
        <p>Kullanıcının seçtiği listeyi almak, içerik bilgilerini göstermek veya tarayıcı uyumluluğu sağlamak için sunucu adresi, liste bağlantısı ve gerekli erişim bilgileri StreamLiveX’in bağlantı katmanı üzerinden kullanıcının belirlediği yayın sağlayıcısına iletilebilir. Uygulama kodu bu bilgileri kalıcı bir sunucu veritabanına yazmak üzere tasarlanmamıştır.</p>
        <p>Web barındırma ve güvenlik hizmetleri; IP adresi, istek zamanı, hata bilgisi ve cihaz/tarayıcı türü gibi sınırlı teknik kayıtları güvenlik, kötüye kullanımın önlenmesi ve hizmetin çalıştırılması amacıyla geçici olarak işleyebilir. Kullanıcı adı ve şifrelerin destek taleplerine veya ekran görüntülerine eklenmemesi gerekir.</p>
      </section>
      <section>
        <h2>4. Üçüncü taraf hizmetleri</h2>
        <p>İçerik adı, afiş, oyuncu, açıklama ve benzeri katalog bilgileri için TMDB gibi üçüncü taraf veri hizmetlerine içerik adı veya kimliği gönderilebilir. IPTV sağlayıcısı ve diğer üçüncü taraf hizmetler kendi gizlilik politikalarına tabidir; StreamLiveX bu hizmetleri yönetmez.</p>
      </section>
      <section>
        <h2>5. Güvenlik ve sorumluluk</h2>
        <p>Erişim bilgilerinizi yalnızca güvendiğiniz cihazlarda kullanın, ortak cihazlarda uygulama verilerini temizleyin ve bilgilerinizi üçüncü kişilerle paylaşmayın. Hiçbir internet aktarım yöntemi tamamen risksiz değildir; bu nedenle makul teknik önlemler uygulanmasına rağmen mutlak güvenlik garantisi verilemez.</p>
      </section>
      <section>
        <h2>6. Politika değişiklikleri ve iletişim</h2>
        <p>Uygulamanın işleyişi veya yasal gereklilikler değiştiğinde bu politika güncellenebilir. Güncel tarih sayfanın üst kısmında gösterilir. Gizlilikle ilgili talepler için <a href="/support">Destek sayfasını</a> kullanabilirsiniz.</p>
      </section>
    </LegalPage>
  );
}

import type { Metadata } from "next";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Kullanım Koşulları | StreamLiveX",
  description: "StreamLiveX web ve Windows uygulaması kullanım koşulları.",
};

export default function TermsPage() {
  return (
    <LegalPage eyebrow="HİZMET VE KULLANIM" title="Kullanım Koşulları" summary="StreamLiveX’i kullanarak aşağıdaki koşulları ve kişisel yayın kaynaklarınıza ilişkin sorumluluklarınızı kabul etmiş olursunuz.">
      <section>
        <h2>1. Hizmetin kapsamı</h2>
        <p>StreamLiveX, kullanıcının kendi medya bağlantılarını düzenlemesine ve uyumlu içerikleri oynatmasına yardımcı olan bağımsız bir yazılımdır. StreamLiveX bir televizyon operatörü, yayıncı, içerik mağazası veya IPTV sağlayıcısı değildir.</p>
        <p><strong>StreamLiveX hiçbir kullanıcıya IPTV kullanım bilgisi, abonelik, M3U listesi, portal adresi, kullanıcı adı veya şifre vermez.</strong> Gerekli bilgileri kullanıcı kendi hizmet sağlayıcısından edinir ve uygulamaya kendisi girer.</p>
      </section>
      <section>
        <h2>2. Yasal kullanım sorumluluğu</h2>
        <p>Kullanıcı yalnızca erişim ve izleme hakkına sahip olduğu yayınları kullanacağını kabul eder. İzinsiz, korsan, hukuka aykırı veya üçüncü kişilerin fikrî mülkiyet haklarını ihlal eden kaynakların eklenmesi yasaktır.</p>
        <p>Bir bağlantının teknik olarak oynatılabilmesi, o içeriğin kullanımının yasal veya izinli olduğu anlamına gelmez. Gerekli abonelik, lisans ve izinlerin kontrolü tamamen kullanıcıya aittir.</p>
      </section>
      <section>
        <h2>3. Üçüncü taraf sağlayıcılar</h2>
        <p>Uygulamaya eklenen IPTV veya medya hizmetleri StreamLiveX’ten bağımsız üçüncü taraflardır. Kanal erişimi, yayın kalitesi, kesinti, hesap geçerliliği, ücretlendirme veya içerik doğruluğu konusunda StreamLiveX sorumlu değildir. Bu konularda kullanıcı kendi sağlayıcısıyla iletişime geçmelidir.</p>
      </section>
      <section>
        <h2>4. Kabul edilebilir kullanım</h2>
        <ul>
          <li>Uygulamayı yürürlükteki mevzuata ve üçüncü taraf haklarına uygun kullanın.</li>
          <li>Başkasına ait erişim bilgilerini izinsiz kullanmayın veya paylaşmayın.</li>
          <li>Hizmetin güvenliğini aşmaya, sistemi kötüye kullanmaya ya da zararlı trafik oluşturmaya çalışmayın.</li>
          <li>StreamLiveX adını, logosunu veya uygulamasını yanıltıcı biçimde yeniden yayımlamayın.</li>
        </ul>
      </section>
      <section>
        <h2>5. Kullanılabilirlik ve garanti</h2>
        <p>Farklı sağlayıcıların bağlantı biçimleri, codec’leri ve erişim politikaları değişebilir. Bu nedenle her listenin, kanalın, filmin, dizinin, ses parçasının veya altyazının kesintisiz ve tüm cihazlarda çalışacağı garanti edilmez. Uygulama ve özellikler bakım, güvenlik veya teknik gereksinimler nedeniyle güncellenebilir.</p>
      </section>
      <section>
        <h2>6. Koşullardaki değişiklikler</h2>
        <p>Bu koşullar hizmet veya mevzuat değişikliklerine göre güncellenebilir. Güncel sürüm ve yürürlük tarihi bu sayfada yayımlanır. Sorularınız için <a href="/support">Destek sayfasını</a> ziyaret edebilirsiniz.</p>
      </section>
    </LegalPage>
  );
}

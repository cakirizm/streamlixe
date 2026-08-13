import type { Metadata } from "next";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Destek | StreamLiveX",
  description: "StreamLiveX kurulum, oynatma ve uygulama desteği.",
};

export default function SupportPage() {
  return (
    <LegalPage eyebrow="YARDIM MERKEZİ" title="StreamLiveX Destek" summary="Uygulama, oynatıcı ve arayüz sorunlarında izleyebileceğiniz güvenli destek adımları.">
      <section>
        <h2>Uygulama desteği</h2>
        <p>Arayüz, kategori geçişi, oynatıcı kontrolleri, altyazı görünümü, indirme veya Windows uygulamasıyla ilgili bir sorun yaşarsanız sorunu yeniden oluşturma adımlarını ve mümkünse kişisel bilgi içermeyen bir ekran görüntüsünü paylaşın.</p>
        <a className="legal-action" href="https://github.com/cakirizm/streamlixe/issues" target="_blank" rel="noreferrer">Teknik destek talebi oluştur <span aria-hidden="true">↗</span></a>
      </section>
      <section>
        <h2>Yayın veya hesap sorunları</h2>
        <p>StreamLiveX IPTV hizmeti ve giriş bilgisi sağlamaz. Kullanıcı kendi yasal M3U veya Xtream bilgilerini uygulamaya kendisi girer. Hesabın süresi, kanal erişimi, yanlış parola, yayın kesintisi veya abonelikle ilgili konular için doğrudan kendi IPTV/yayın sağlayıcınızla iletişime geçin.</p>
      </section>
      <section>
        <h2>Destek talebinde paylaşmayın</h2>
        <ul>
          <li>IPTV kullanıcı adı veya şifresi</li>
          <li>İçinde erişim bilgisi bulunan M3U ya da yayın bağlantısı</li>
          <li>Portal MAC adresi, ödeme bilgisi veya kişisel belgeler</li>
        </ul>
        <p>Bir hata mesajında bu bilgiler görünüyorsa ekran görüntüsünü göndermeden önce ilgili alanları kapatın.</p>
      </section>
      <section>
        <h2>Önce bunları deneyin</h2>
        <ol>
          <li>İnternet bağlantınızı ve sağlayıcı adresini kontrol edin.</li>
          <li>Uygulamayı tamamen kapatıp yeniden açın.</li>
          <li>Aynı bilgilerin sağlayıcının önerdiği başka bir oynatıcıda çalışıp çalışmadığını kontrol edin.</li>
          <li>Sorun yalnızca belirli bir yayındaysa yayın sağlayıcınıza bildirin.</li>
          <li>Sorun StreamLiveX arayüzündeyse teknik destek talebi oluşturun.</li>
        </ol>
      </section>
      <section>
        <h2>Yerel verileri kaldırma</h2>
        <p>Uygulamadaki yeni oynatma listesi seçeneğini kullanarak kayıtlı kütüphaneyi kaldırabilir, Ayarlar bölümünden izleme geçmişini temizleyebilirsiniz. Tarayıcı sürümünde site verilerini temizlemek de cihazda saklanan tercihleri kaldırır.</p>
      </section>
    </LegalPage>
  );
}

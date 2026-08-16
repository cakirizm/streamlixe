import type { Metadata } from "next";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Rehberler",
  description: "M3U, Xtream Codes ve IPTV oynatıcı seçimi hakkında StreamLiveX rehberleri.",
  alternates: { canonical: "/rehber" },
};

const guides = [
  {
    href: "/rehber/m3u-nedir",
    title: "M3U Nedir? M3U Oynatma Listesi Nasıl Kullanılır?",
    summary: "M3U/M3U8 dosya biçimi ne işe yarar, bir M3U bağlantısı nasıl okunur ve StreamLiveX'e nasıl eklenir?",
  },
  {
    href: "/rehber/xtream-codes-nedir",
    title: "Xtream Codes Nedir? Nasıl Bağlanılır?",
    summary: "Xtream Codes API'nin çalışma mantığı, sunucu/kullanıcı adı/şifre bilgileriyle bir oynatıcıya nasıl bağlanılır?",
  },
  {
    href: "/rehber/iptv-oynatici-secimi",
    title: "IPTV Oynatıcı Seçerken Nelere Dikkat Edilmeli?",
    summary: "Codec desteği, çoklu profil, EPG ve platform uyumluluğu gibi kriterlerle doğru oynatıcıyı seçme rehberi.",
  },
];

export default function RehberIndexPage() {
  return (
    <LegalPage
      eyebrow="REHBERLER"
      title="Rehberler"
      summary="M3U, Xtream Codes ve IPTV oynatıcı teknolojisi hakkında kısa ve teknik rehberler."
    >
      <section>
        <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: "1.5rem" }}>
          {guides.map((g) => (
            <li key={g.href}>
              <h2 style={{ marginBottom: "0.35rem" }}>
                <a href={g.href}>{g.title}</a>
              </h2>
              <p style={{ margin: 0 }}>{g.summary}</p>
            </li>
          ))}
        </ul>
      </section>
    </LegalPage>
  );
}

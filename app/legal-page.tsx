import type { ReactNode } from "react";

type LegalPageProps = {
  eyebrow: string;
  title: string;
  summary: string;
  children: ReactNode;
};

export function LegalPage({ eyebrow, title, summary, children }: LegalPageProps) {
  return (
    <main className="legal-page">
      <header className="legal-header">
        <a className="legal-brand" href="/" aria-label="StreamLiveX ana sayfası">
          <img src="/streamlivex-logo.jpeg" alt="" />
          <span>StreamLive<b>X</b></span>
        </a>
        <a className="legal-back" href="/">Uygulamaya dön <span aria-hidden="true">→</span></a>
      </header>

      <section className="legal-shell">
        <div className="legal-hero">
          <span>{eyebrow}</span>
          <h1>{title}</h1>
          <p>{summary}</p>
          <small>Son güncelleme: 13 Ağustos 2026</small>
        </div>
        <article className="legal-content">{children}</article>
        <nav className="legal-nav" aria-label="Yasal ve destek bağlantıları">
          <a href="/privacy">Gizlilik Politikası</a>
          <a href="/terms">Kullanım Koşulları</a>
          <a href="/support">Destek</a>
        </nav>
      </section>
    </main>
  );
}

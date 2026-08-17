import type { Metadata, Viewport } from "next";
import "./globals.css";
import "./legal.css";

const siteUrl = "http://streamlivex.com";
const title = "StreamLiveX – Kendi IPTV Kütüphaneni Yönet ve İzle";
const description =
  "StreamLiveX; kendi M3U, Xtream veya portal kaynaklarınızı tek bir modern arayüzde birleştiren ücretsiz IPTV oynatıcısıdır. Canlı TV, film ve dizileri profil bazlı tercihler, EPG ve altyazı desteğiyle izleyin.";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: { default: title, template: "%s | StreamLiveX" },
  description,
  applicationName: "StreamLiveX",
  keywords: [
    "StreamLiveX",
    "IPTV oynatıcı",
    "M3U player",
    "Xtream codes player",
    "canlı TV izle",
    "IPTV web player",
    "IPTV uygulaması",
  ],
  authors: [{ name: "StreamLiveX" }],
  creator: "StreamLiveX",
  publisher: "StreamLiveX",
  alternates: { canonical: "/" },
  icons: {
    icon: [
      { url: "/favicon.svg", type: "image/svg+xml" },
      { url: "/favicon-32x32.png", sizes: "32x32", type: "image/png" },
      { url: "/favicon-16x16.png", sizes: "16x16", type: "image/png" },
      { url: "/favicon.ico", sizes: "any" },
    ],
    apple: [{ url: "/apple-touch-icon.png", sizes: "180x180", type: "image/png" }],
  },
  manifest: "/site.webmanifest",
  robots: { index: true, follow: true, googleBot: { index: true, follow: true } },
  openGraph: {
    type: "website",
    url: siteUrl,
    siteName: "StreamLiveX",
    title,
    description,
    locale: "tr_TR",
    alternateLocale: ["en_US", "ar_SA", "de_DE", "fr_FR", "es_ES"],
    images: [{ url: "/og-image.png", width: 1920, height: 1080, alt: "StreamLiveX arayüzü" }],
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
    images: ["/og-image.png"],
  },
  formatDetection: { telephone: false },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#080910",
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "StreamLiveX",
  url: siteUrl,
  description,
  applicationCategory: "MultimediaApplication",
  operatingSystem: "Web, Windows",
  offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
  inLanguage: ["tr", "en", "ar", "de", "fr", "es"],
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="tr">
      <head>
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      </head>
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}

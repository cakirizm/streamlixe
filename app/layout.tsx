import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "StreamLiveX",
  description: "Akıllı IPTV oynatıcı deneyimi.",
  icons: { icon: [{ url: "/favicon.svg", type: "image/svg+xml" }] },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="tr"><body suppressHydrationWarning>{children}</body></html>;
}

import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Web Oynatıcı",
  description: "StreamLiveX web oynatıcısını açın ve kendi IPTV kütüphanenizi yönetin.",
  alternates: { canonical: "/app" },
  robots: { index: false, follow: false },
};

export default function AppLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return children;
}

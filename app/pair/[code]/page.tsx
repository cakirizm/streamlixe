import type { Metadata } from "next";
import { PairForm } from "../../pair-form";

export const metadata: Metadata = {
  title: "Telefondan Oynatma Listesi Ekle",
  robots: { index: false, follow: false },
};

export default async function PairPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return <PairForm code={code} />;
}

const TTL_SECONDS = 600;

type PairedProvider = {
  method: "m3u" | "xtream";
  name: string;
  url?: string;
  server?: string;
  username?: string;
  password?: string;
};

// "cloudflare:workers" statik olarak import edilirse, projenin kendi build dogrulama
// script'i (validate-artifact.sh) worker paketini duz Node.js ile import ederken
// "Only URLs with a scheme in: file, data, and node are supported" hatasi veriyor --
// bu ozel semayi yalnizca gercek bir istek geldiginde (fonksiyon icinde) dinamik olarak
// yukleyerek build dogrulamasini bozmadan calisiyoruz.
async function kv() {
  try {
    const { env } = await import("cloudflare:workers");
    if (env?.PAIRING_KV) return env.PAIRING_KV;
  } catch {}
  return null;
}

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as { action?: string; code?: string; provider?: PairedProvider };
    const namespace = await kv();
    if (!namespace) return Response.json({ error: "Eşleştirme servisi yapılandırılmamış" }, { status: 503 });

    if (body.action === "create") {
      const code = crypto.randomUUID();
      await namespace.put(`pair:${code}`, JSON.stringify({ status: "pending" }), { expirationTtl: TTL_SECONDS });
      return Response.json({ code });
    }

    if (body.action === "submit") {
      const code = body.code?.trim();
      const provider = body.provider;
      if (!code) return Response.json({ error: "code gerekli" }, { status: 400 });
      if (!provider?.method || !provider?.name) return Response.json({ error: "geçersiz oynatma listesi" }, { status: 400 });
      if (provider.method === "m3u" && !provider.url) return Response.json({ error: "M3U bağlantısı gerekli" }, { status: 400 });
      if (provider.method === "xtream" && (!provider.server || !provider.username || !provider.password)) {
        return Response.json({ error: "Sunucu, kullanıcı adı ve şifre gerekli" }, { status: 400 });
      }
      const existing = await namespace.get(`pair:${code}`);
      if (!existing) return Response.json({ error: "Kod süresi dolmuş veya geçersiz" }, { status: 404 });
      await namespace.put(`pair:${code}`, JSON.stringify({ status: "ready", provider }), { expirationTtl: 120 });
      return Response.json({ ok: true });
    }

    return Response.json({ error: "geçersiz istek" }, { status: 400 });
  } catch (error) {
    console.error("Pair POST failed", error);
    return Response.json({ error: "Eşleştirme servisine şu anda ulaşılamıyor" }, { status: 502 });
  }
}

export async function GET(request: Request) {
  try {
    const code = new URL(request.url).searchParams.get("code")?.trim();
    if (!code) return Response.json({ error: "code gerekli" }, { status: 400 });
    const namespace = await kv();
    if (!namespace) return Response.json({ status: "expired" }, { status: 404 });
    const raw = await namespace.get(`pair:${code}`);
    if (!raw) return Response.json({ status: "expired" }, { status: 404 });
    const data = JSON.parse(raw) as { status: string; provider?: PairedProvider };
    if (data.status === "ready") await namespace.delete(`pair:${code}`);
    return Response.json(data, { headers: { "cache-control": "no-store" } });
  } catch (error) {
    console.error("Pair GET failed", error);
    return Response.json({ error: "Eşleştirme servisine şu anda ulaşılamıyor" }, { status: 502 });
  }
}

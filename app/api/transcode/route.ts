async function getTranscodeConfig() {
  let origin = (process.env.TRANSCODE_ORIGIN || "").trim().replace(/\/$/, "");
  let token = process.env.TRANSCODE_TOKEN || "";
  if (!origin || !token) {
    try {
      const { env } = await import("cloudflare:workers");
      const e = env as Record<string, unknown> | undefined;
      if (!origin && typeof e?.TRANSCODE_ORIGIN === "string") origin = e.TRANSCODE_ORIGIN.trim().replace(/\/$/, "");
      if (!token && typeof e?.TRANSCODE_TOKEN === "string") token = e.TRANSCODE_TOKEN;
    } catch {}
  }
  return { origin, token };
}

export async function POST(request: Request) {
  const { origin, token } = await getTranscodeConfig();
  if (!origin || !token) return Response.json({ error: "Web dönüştürücü yapılandırılmamış" }, { status: 503 });
  try {
    const body = await request.text();
    const response = await fetch(`${origin}/start`, { method: "POST", headers: { authorization: `Bearer ${token}`, "content-type": "application/json" }, body, signal: AbortSignal.timeout(60000) });
    return new Response(await response.text(), { status: response.status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
  } catch (error) { return Response.json({ error: error instanceof Error && error.name === "TimeoutError" ? "Dönüştürücü zaman aşımına uğradı" : "Dönüştürücüye ulaşılamadı" }, { status: 502 }) }
}

export async function DELETE(request: Request) {
  const { origin, token } = await getTranscodeConfig();
  if (!origin || !token) return Response.json({ ok: true });
  const id = new URL(request.url).searchParams.get("session") || "";
  if (!/^[a-f0-9]{32}$/.test(id)) return Response.json({ error: "Geçersiz oturum" }, { status: 400 });
  try { await fetch(`${origin}/session/${id}`, { method: "DELETE", headers: { authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(5000) }) } catch {}
  return Response.json({ ok: true });
}

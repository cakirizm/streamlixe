// LG/Samsung mağaza QA demo hesabı: gerçek bir IPTV aboneliği paylaşmadan test imkânı
// vermek için app/api/import/route.ts'teki sahte Xtream hesabının (server=".../demo",
// kullanıcı="demo", şifre="demo") yayın/film/dizi URL'lerini gerçek, herkese açık
// örnek videolara yönlendirir. Path şekli Xtream'in kendi konvansiyonuyla birebir aynı:
// /demo/{kind}/{username}/{password}/{id}.{ext}
const TARGETS: Record<string, Record<string, string>> = {
  live: {
    "1": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "2": "https://test-streams.mux.dev/test_001/stream.m3u8",
  },
  movie: {
    "101": "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "102": "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
  },
  series: {
    "301": "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    "302": "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
  },
};

export async function GET(_request: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  const [kind, username, password, file] = path || [];
  if (username !== "demo" || password !== "demo") return new Response("Not found", { status: 404 });
  const id = (file || "").replace(/\.[^.]+$/, "");
  const target = TARGETS[kind || ""]?.[id];
  if (!target) return new Response("Not found", { status: 404 });
  return Response.redirect(target, 302);
}

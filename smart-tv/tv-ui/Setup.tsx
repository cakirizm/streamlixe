// StreamLiveX TV — kurulum ekranı (native TvSetupScreen birebir):
// Sol bilgi paneli + sağ koyu kart. İki yöntem: QR ile Bağlan (telefon eşleştirme) ve Xtream Codes.
import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import type { TvLang } from "./i18n";
import { focusFirst } from "./dpad";
import { importXtream } from "./library";

export type Provider = {
  name: string;
  method: "xtream" | "pairing";
  server?: string; username?: string; password?: string; url?: string; mac?: string;
};

const API_BASE = (window as any).__SLX_PROXY_ORIGIN__ || "https://streamlivex.com";

// native: onBack yalnızca Playlist Manager'dan "yeni liste ekle" akışında verilir;
// ana kurulum girişinde (TvRoot'tan ilk açılış) onBack yok — geri tuşu hiçbir şey yapmaz.
export function Setup({ lang, onComplete, onBack }: { lang: TvLang; onComplete: (p: Provider) => void; onBack?: () => void }) {
  const [mode, setMode] = useState<"qr" | "xtream">("qr");
  const rtl = document.documentElement.dir === "rtl";
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 60); return () => clearTimeout(id); }, [mode]);

  useEffect(() => {
    if (!onBack) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack]);

  return (
    <main className="tv-setup2" ref={ref}>
      {onBack && (
        <button className="tv-series-back tv-focusable" style={{ position: "absolute", top: 22, insetInlineStart: 26 }} onClick={onBack}>
          ← Geri
        </button>
      )}
      <div className="tv-setup2-row">
        {/* Sol bilgi paneli */}
        <div className="tv-setup2-left">
          <h1 className="tv-setup2-brand">StreamLiveX</h1>
          <div className="tv-setup2-tv">TV Oynatıcı</div>
          <h2 className="tv-setup2-head">{mode === "qr" ? "Telefonla bağlan" : "Xtream hesabını bağla"}</h2>
          <p className="tv-setup2-desc">
            {mode === "qr"
              ? "QR kodu telefonunla okut. Telefon ve TV aynı Wi‑Fi / yerel ağda olmalı. Bilgiler doğrudan TV'ye aktarılır."
              : "Xtream Codes bilgilerini kumandayla manuel olarak gir."}
          </p>
          <div className="tv-setup2-tabs">
            <button className={`tv-setup2-tab tv-focusable${mode === "qr" ? " active" : ""}`} onClick={() => setMode("qr")}>QR ile Bağlan</button>
            <button className={`tv-setup2-tab tv-focusable${mode === "xtream" ? " active" : ""}`} onClick={() => setMode("xtream")}>Xtream Codes</button>
          </div>
        </div>

        {/* Sağ koyu kart */}
        <div className="tv-setup2-card" dir={rtl ? "rtl" : "ltr"}>
          {mode === "qr" ? <QrPanel onComplete={onComplete} /> : <XtreamForm onComplete={onComplete} />}
        </div>
      </div>
    </main>
  );
}

function QrPanel({ onComplete }: { onComplete: (p: Provider) => void }) {
  const [qr, setQr] = useState("");
  const [status, setStatus] = useState("QR kod hazırlanıyor…");
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    let poll: ReturnType<typeof setInterval> | null = null;
    setQr(""); setError(""); setStatus("QR kod hazırlanıyor…");
    (async () => {
      try {
        const res = await fetch("/api/pair", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action: "create" }) });
        if (!res.ok) throw new Error(`create ${res.status}`);
        const data = await res.json() as { code?: string; error?: string };
        if (!active) return;
        if (!data.code) throw new Error(data.error || "kod alınamadı");
        const target = `${API_BASE}/pair/${data.code}`;
        const dataUrl = await QRCode.toDataURL(target, { margin: 1, width: 240, color: { dark: "#000000", light: "#ffffff" } });
        if (!active) return;
        setQr(dataUrl);
        setStatus("Telefonunla QR kodu okut · Aynı Wi‑Fi ağına bağlı ol");
        poll = setInterval(async () => {
          try {
            const s = await fetch(`/api/pair?code=${data.code}`);
            const sd = await s.json() as { status?: string; provider?: any };
            if (sd.status === "ready" && sd.provider) {
              if (poll) clearInterval(poll);
              setStatus("Telefon eşleşti. Bağlanılıyor…");
              onComplete({ name: sd.provider.name || "Oynatma Listem", method: sd.provider.method || "xtream", ...sd.provider });
            } else if (sd.status === "expired") {
              if (poll) clearInterval(poll);
              setStatus("QR kodun süresi doldu.");
              setError("expired");
            }
          } catch { /* poll hatası yut */ }
        }, 2500);
      } catch (err) {
        if (active) setError(err instanceof Error ? err.message : "QR kod oluşturulamadı");
      }
    })();
    return () => { active = false; if (poll) clearInterval(poll); };
  }, [retry]);

  return (
    <div className="tv-qr">
      <div className="tv-qr-box">
        {error ? (
          <div className="tv-qr-err">
            <span>QR kod oluşturulamadı</span>
            <button className="tv-btn tv-focusable" onClick={() => setRetry((x) => x + 1)}>Tekrar dene</button>
          </div>
        ) : qr ? (
          <img src={qr} alt="Telefonla eşleştirme QR kodu" width={240} height={240} />
        ) : (
          <div className="tv-qr-loading">…</div>
        )}
      </div>
      <p className="tv-qr-status">{status}</p>
    </div>
  );
}

function XtreamForm({ onComplete }: { onComplete: (p: Provider) => void }) {
  const [form, setForm] = useState({ name: "Oynatma Listem", server: "", username: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function connect(e: React.FormEvent) {
    e.preventDefault();
    if (!form.server.trim()) { setError("Sunucu adresini gir."); return; }
    if (!/^https?:\/\//i.test(form.server.trim())) { setError("Sunucu adresi http:// veya https:// ile başlamalı."); return; }
    if (!form.username.trim()) { setError("Kullanıcı adını gir."); return; }
    if (!form.password) { setError("Şifreyi gir."); return; }
    setError(""); setBusy(true);
    const provider: Provider = { ...form, name: form.name || "Oynatma Listem", method: "xtream" };
    // native TvSetupScreen: XtreamClient().loadLiveLibrary(provider) ile doğrulanmadan kabul etmiyor.
    try {
      const lib = await importXtream(provider);
      if (lib.live.length === 0) {
        setError("Hesap doğrulandı ancak canlı kanal bulunamadı.");
        setBusy(false);
        return;
      }
      onComplete(provider);
    } catch {
      setError("Xtream hesabına bağlanılamadı.");
      setBusy(false);
    }
  }

  return (
    <form className="tv-xtream" onSubmit={connect}>
      <h3 className="tv-xtream-title">Xtream Codes</h3>
      <label className="tv-field"><span>Liste adı</span><input className="tv-focusable" value={form.name} onChange={set("name")} /></label>
      <label className="tv-field"><span>Sunucu adresi</span><input className="tv-focusable" placeholder="http://sunucu:port" value={form.server} onChange={set("server")} /></label>
      <label className="tv-field"><span>Kullanıcı adı</span><input className="tv-focusable" value={form.username} onChange={set("username")} /></label>
      <label className="tv-field"><span>Şifre</span><input className="tv-focusable" type="password" value={form.password} onChange={set("password")} /></label>
      {error && <p className="tv-error">{error}</p>}
      <button className="tv-btn tv-focusable" disabled={busy}>{busy ? "Xtream hesabına bağlanılıyor…" : "Bağlan"}</button>
    </form>
  );
}

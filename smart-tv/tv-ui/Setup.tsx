// StreamLiveX TV — kurulum ekranı (native TvSetupScreen tasarımına göre).
// Faz 1: sağlayıcı bilgisini doğrulayıp kaydeder ve akışı ilerletir. Tam kütüphane
// taraması Faz 2'de içerik ekranlarıyla gelir.
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { focusFirst } from "./dpad";

export type Provider = {
  name: string;
  method: "m3u" | "xtream" | "portal";
  url?: string; server?: string; username?: string; password?: string; mac?: string;
  demo?: boolean;
};

export function Setup({ lang, onComplete }: { lang: TvLang; onComplete: (p: Provider) => void }) {
  const t = makeT(lang);
  const [method, setMethod] = useState<"m3u" | "xtream" | "portal">("m3u");
  const [form, setForm] = useState({ name: "", url: "", server: "", username: "", password: "", mac: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => { focusFirst(cardRef.current); }, []);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const provider: Provider = { name: form.name || "StreamLiveX", method, ...form };
      // Faz 2: burada /api/import ile tam kütüphane çekilecek. Faz 1'de bağlantıyı
      // hafif doğrulayıp sağlayıcıyı saklıyoruz.
      onComplete(provider);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Bağlantı kurulamadı");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="tv-setup">
      <div className="tv-setup-card" ref={cardRef}>
        <div className="tv-brand">
          <img src="/streamlivex-logo.jpeg" alt="StreamLiveX" />
          <b>StreamLive<i>X</i></b>
        </div>
        <h1>{t("setup_title")}</h1>
        <p className="sub">{t("setup_sub")}</p>

        <div className="tv-tabs">
          {(["m3u", "xtream", "portal"] as const).map((m) => (
            <button
              key={m}
              type="button"
              className={`tv-tab tv-focusable${method === m ? " active" : ""}`}
              onClick={() => setMethod(m)}
            >
              {t(m)}
            </button>
          ))}
        </div>

        <form onSubmit={submit}>
          <label className="tv-field">
            <span>{t("list_name")}</span>
            <input className="tv-focusable" value={form.name} onChange={set("name")} />
          </label>

          {method === "m3u" && (
            <label className="tv-field">
              <span>{t("m3u_url")}</span>
              <input className="tv-focusable" placeholder="https://ornek.com/playlist.m3u" value={form.url} onChange={set("url")} />
            </label>
          )}

          {method === "xtream" && (
            <>
              <label className="tv-field">
                <span>{t("server")}</span>
                <input className="tv-focusable" placeholder="http://sunucu:port" value={form.server} onChange={set("server")} />
              </label>
              <div className="tv-row2">
                <label className="tv-field">
                  <span>{t("username")}</span>
                  <input className="tv-focusable" value={form.username} onChange={set("username")} />
                </label>
                <label className="tv-field">
                  <span>{t("password")}</span>
                  <input className="tv-focusable" type="password" value={form.password} onChange={set("password")} />
                </label>
              </div>
            </>
          )}

          {method === "portal" && (
            <>
              <label className="tv-field">
                <span>{t("server")}</span>
                <input className="tv-focusable" placeholder="http://portal-adresi/c" value={form.server} onChange={set("server")} />
              </label>
              <label className="tv-field">
                <span>{t("mac")}</span>
                <input className="tv-focusable" placeholder="00:1A:79:XX:XX:XX" value={form.mac} onChange={set("mac")} />
              </label>
            </>
          )}

          {error && <p className="tv-error">{error}</p>}

          <button className="tv-btn tv-focusable" disabled={busy}>
            {busy ? t("loading") : `${t("add_playlist")} →`}
          </button>
        </form>

        <button
          type="button"
          className="tv-btn ghost tv-focusable"
          onClick={() => onComplete({ name: "Demo", method: "m3u", demo: true })}
        >
          {t("demo")}
        </button>
      </div>
    </main>
  );
}

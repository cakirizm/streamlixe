"use client";

import { FormEvent, useState } from "react";

type Method = "m3u" | "xtream";

export function PairForm({ code }: { code: string }) {
  const [method, setMethod] = useState<Method>("m3u");
  const [form, setForm] = useState({ name: "Oynatma Listem", url: "", server: "", username: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    if (method === "m3u" && !form.url.trim()) {
      setError("M3U bağlantısı gerekli");
      return;
    }
    if (method === "xtream" && (!form.server.trim() || !form.username.trim() || !form.password.trim())) {
      setError("Sunucu, kullanıcı adı ve şifre gerekli");
      return;
    }
    setBusy(true);
    try {
      const provider =
        method === "m3u"
          ? { method, name: form.name || "Oynatma Listem", url: form.url.trim() }
          : { method, name: form.name || "Oynatma Listem", server: form.server.trim().replace(/\/$/, ""), username: form.username.trim(), password: form.password.trim() };
      const response = await fetch("/api/pair", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ action: "submit", code, provider }),
      });
      const data = (await response.json()) as { ok?: boolean; error?: string };
      if (!response.ok || !data.ok) throw new Error(data.error || "Bağlantı kurulamadı");
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Bağlantı kurulamadı");
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <main className="setup-page">
        <div className="setup-glow" />
        <section className="setup-card">
          <div className="setup-head">
            <span>GÖNDERİLDİ</span>
            <h1>TV'ye iletildi ✓</h1>
            <p>Oynatma listen TV ekranına gönderildi. TV birkaç saniye içinde otomatik olarak yüklemeye başlayacak — bu sayfayı kapatabilirsin.</p>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="setup-page">
      <div className="setup-glow" />
      <section className="setup-card">
        <div className="setup-head">
          <span>TELEFONDAN EKLE</span>
          <h1>Oynatma listeni gönder</h1>
          <p>Buraya girdiğin bilgi doğrudan bekleyen TV ekranına gönderilir; TV'de hiçbir şeye dokunman gerekmez.</p>
        </div>
        <div className="method-tabs">
          {(["m3u", "xtream"] as Method[]).map((id) => (
            <button key={id} type="button" className={method === id ? "active" : ""} disabled={busy} onClick={() => setMethod(id)}>
              {id === "m3u" ? "☷" : "⌘"}
              <b>{id === "m3u" ? "M3U" : "Xtream Codes"}</b>
            </button>
          ))}
        </div>
        <form onSubmit={submit}>
          <label>
            Liste adı
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          {method === "m3u" && (
            <label>
              M3U bağlantısı
              <input placeholder="https://ornek.com/playlist.m3u" value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} />
            </label>
          )}
          {method === "xtream" && (
            <>
              <label>
                Sunucu adresi
                <input placeholder="http://sunucu:port" value={form.server} onChange={(e) => setForm({ ...form, server: e.target.value })} />
              </label>
              <div className="two">
                <label>
                  Kullanıcı adı
                  <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
                </label>
                <label>
                  Şifre
                  <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
                </label>
              </div>
            </>
          )}
          {error && <p className="error">{error}</p>}
          <button className="primary" disabled={busy}>
            {busy ? "Gönderiliyor…" : "TV'ye gönder →"}
          </button>
        </form>
        <p className="privacy">🔒 Bu bilgi yalnızca bekleyen TV oturumuna iletilir, kalıcı olarak saklanmaz.</p>
      </section>
    </main>
  );
}

// StreamLiveX TV — marka ikonu (yazı-logo yerine; native sidebar'daki küçük kare play rozeti).
export function BrandIcon({ size = 34 }: { size?: number }) {
  return (
    <span className="tv-brand-icon" style={{ width: size, height: size }} aria-hidden>
      <svg viewBox="0 0 24 24" width={Math.round(size * 0.52)} height={Math.round(size * 0.52)}>
        <path d="M7 4.5 L19 12 L7 19.5 Z" fill="#fff" />
      </svg>
    </span>
  );
}

// Tam marka: ikon + "StreamLiveX" + "SMART IPTV PLAYER" alt başlık.
export function BrandFull({ size = 34, subtitle = true }: { size?: number; subtitle?: boolean }) {
  return (
    <>
      <BrandIcon size={size} />
      <span className="tv-brand-text">
        <b>StreamLive<i>X</i></b>
        {subtitle && <small>SMART IPTV PLAYER</small>}
      </span>
    </>
  );
}

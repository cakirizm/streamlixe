// StreamLiveX TV — Faz 1 çok dilli metinler (TR, EN, AR, DE, FR, ES).
export type TvLang = "tr" | "en" | "ar" | "de" | "fr" | "es";

export const TV_LANGS: { code: TvLang; label: string; rtl?: boolean }[] = [
  { code: "tr", label: "Türkçe" },
  { code: "en", label: "English" },
  { code: "ar", label: "العربية", rtl: true },
  { code: "de", label: "Deutsch" },
  { code: "fr", label: "Français" },
  { code: "es", label: "Español" },
];

type Dict = Record<string, string>;
const S: Record<TvLang, Dict> = {
  tr: {
    home: "Ana Sayfa", live: "Canlı TV", sports: "Spor", movies: "Filmler", series: "Diziler",
    search: "Ara", my_list: "Listem", history: "İzleme Geçmişi", settings: "Ayarlar",
    setup_title: "İçeriklerin. Tek ekranda.", setup_sub: "Oynatma listeni ekle; canlı TV, film ve dizilerini otomatik düzenleyelim.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "Liste adı", m3u_url: "M3U bağlantısı", server: "Sunucu adresi", username: "Kullanıcı adı",
    password: "Şifre", mac: "MAC adresi", add_playlist: "Oynatma listesini ekle", demo: "Önce demo içerikle dene",
    who_watching: "Kim izliyor?", add_profile: "Profil ekle", loading: "Yükleniyor…",
    coming_soon: "Bu bölüm yakında.",
    continue_watching: "Kaldığın Yerden Devam Et", for_you: "Sizin İçin",
    todays_sports: "Bugünün Sporları", no_matches: "Bugün için maç bulunamadı.",
    subtitle: "SMART IPTV PLAYER",
  },
  en: {
    home: "Home", live: "Live TV", sports: "Sports", movies: "Movies", series: "Series",
    search: "Search", my_list: "My List", history: "History", settings: "Settings",
    setup_title: "Your content. One screen.", setup_sub: "Add your playlist; we'll organize live TV, movies and series automatically.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "List name", m3u_url: "M3U link", server: "Server address", username: "Username",
    password: "Password", mac: "MAC address", add_playlist: "Add playlist", demo: "Try demo content first",
    who_watching: "Who's watching?", add_profile: "Add profile", loading: "Loading…",
    coming_soon: "This section is coming soon.",
    continue_watching: "Continue Watching", for_you: "For You",
    todays_sports: "Today's Sports", no_matches: "No matches today.",
    subtitle: "SMART IPTV PLAYER",
  },
  ar: {
    home: "الرئيسية", live: "التلفزيون المباشر", sports: "رياضة", movies: "أفلام", series: "مسلسلات",
    search: "بحث", my_list: "قائمتي", history: "السجل", settings: "الإعدادات",
    setup_title: "محتواك. شاشة واحدة.", setup_sub: "أضف قائمة التشغيل؛ سننظّم البث المباشر والأفلام والمسلسلات تلقائيًا.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "اسم القائمة", m3u_url: "رابط M3U", server: "عنوان الخادم", username: "اسم المستخدم",
    password: "كلمة المرور", mac: "عنوان MAC", add_playlist: "أضف قائمة التشغيل", demo: "جرّب المحتوى التجريبي أولاً",
    who_watching: "من يشاهد؟", add_profile: "أضف ملفًا", loading: "جارٍ التحميل…",
    coming_soon: "هذا القسم قريبًا.",
    continue_watching: "متابعة المشاهدة", for_you: "لك",
    todays_sports: "رياضات اليوم", no_matches: "لا مباريات اليوم.",
    subtitle: "SMART IPTV PLAYER",
  },
  de: {
    home: "Start", live: "Live-TV", sports: "Sport", movies: "Filme", series: "Serien",
    search: "Suche", my_list: "Meine Liste", history: "Verlauf", settings: "Einstellungen",
    setup_title: "Deine Inhalte. Ein Bildschirm.", setup_sub: "Füge deine Playlist hinzu; wir ordnen Live-TV, Filme und Serien automatisch.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "Listenname", m3u_url: "M3U-Link", server: "Serveradresse", username: "Benutzername",
    password: "Passwort", mac: "MAC-Adresse", add_playlist: "Playlist hinzufügen", demo: "Zuerst Demo testen",
    who_watching: "Wer schaut?", add_profile: "Profil hinzufügen", loading: "Wird geladen…",
    coming_soon: "Dieser Bereich kommt bald.",
    continue_watching: "Weiterschauen", for_you: "Für dich",
    todays_sports: "Sport heute", no_matches: "Heute keine Spiele.",
    subtitle: "SMART IPTV PLAYER",
  },
  fr: {
    home: "Accueil", live: "TV en direct", sports: "Sports", movies: "Films", series: "Séries",
    search: "Recherche", my_list: "Ma liste", history: "Historique", settings: "Paramètres",
    setup_title: "Vos contenus. Un écran.", setup_sub: "Ajoutez votre playlist ; nous organisons la TV en direct, les films et séries automatiquement.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "Nom de la liste", m3u_url: "Lien M3U", server: "Adresse du serveur", username: "Nom d'utilisateur",
    password: "Mot de passe", mac: "Adresse MAC", add_playlist: "Ajouter la playlist", demo: "Essayer la démo d'abord",
    who_watching: "Qui regarde ?", add_profile: "Ajouter un profil", loading: "Chargement…",
    coming_soon: "Cette section arrive bientôt.",
    continue_watching: "Reprendre la lecture", for_you: "Pour vous",
    todays_sports: "Sports du jour", no_matches: "Aucun match aujourd'hui.",
    subtitle: "SMART IPTV PLAYER",
  },
  es: {
    home: "Inicio", live: "TV en vivo", sports: "Deportes", movies: "Películas", series: "Series",
    search: "Buscar", my_list: "Mi lista", history: "Historial", settings: "Ajustes",
    setup_title: "Tu contenido. Una pantalla.", setup_sub: "Añade tu lista; organizaremos TV en vivo, películas y series automáticamente.",
    m3u: "M3U", xtream: "Xtream Codes", portal: "Portal",
    list_name: "Nombre de la lista", m3u_url: "Enlace M3U", server: "Dirección del servidor", username: "Usuario",
    password: "Contraseña", mac: "Dirección MAC", add_playlist: "Añadir lista", demo: "Probar demo primero",
    who_watching: "¿Quién ve?", add_profile: "Añadir perfil", loading: "Cargando…",
    coming_soon: "Esta sección llegará pronto.",
    continue_watching: "Seguir viendo", for_you: "Para ti",
    todays_sports: "Deportes de hoy", no_matches: "No hay partidos hoy.",
    subtitle: "SMART IPTV PLAYER",
  },
};

export function makeT(lang: TvLang) {
  const dict = S[lang] || S.tr;
  return (key: string) => dict[key] ?? S.tr[key] ?? key;
}
export function isRtl(lang: TvLang) {
  return TV_LANGS.find((l) => l.code === lang)?.rtl === true;
}

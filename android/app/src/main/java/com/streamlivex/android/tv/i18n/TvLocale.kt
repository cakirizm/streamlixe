package com.streamlivex.android.tv.i18n

import android.content.Context

enum class TvLocale(
    val code: String,
    val tmdbLanguage: String,
    val displayName: String,
) {
    TR("tr", "tr-TR", "Türkçe"),
    EN("en", "en-US", "English"),
    AR("ar", "ar-SA", "العربية"),
    DE("de", "de-DE", "Deutsch"),
    FR("fr", "fr-FR", "Français"),
    ES("es", "es-ES", "Español"),
    ;

    companion object {
        fun fromCode(code: String?): TvLocale =
            entries.firstOrNull { it.code == code } ?: TR
    }
}

object TvLocaleStore {
    private const val PREFS = "streamlivex_tv"
    private const val KEY = "tv_language"

    fun get(context: Context): TvLocale =
        TvLocale.fromCode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, TvLocale.TR.code),
        )

    fun set(context: Context, locale: TvLocale) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, locale.code)
            .apply()
    }
}

class TvStrings(private val locale: TvLocale) {
    private val tr = mapOf(
        "home" to "Ana Sayfa", "live" to "Canlı TV", "movies" to "Filmler", "series" to "Diziler",
        "search" to "Ara", "my_list" to "Listem", "settings" to "Ayarlar",
        "trending_movies" to "Trend Filmler", "trending_series" to "Trend Diziler",
        "continue" to "Kaldığın Yerden Devam Et", "for_you" to "Sizin İçin",
        "play" to "Oynat", "add_list" to "Listeme Ekle", "remove_list" to "Listemden Çıkar",
        "director" to "Yönetmen", "cast" to "Oyuncular", "similar_movies" to "Benzer Filmler",
        "similar_series" to "Benzer Diziler", "in_playlist" to "Oynatma listende var",
        "not_in_playlist" to "Oynatma listende yok", "seasons_episodes" to "Sezonlar ve Bölümler",
        "season" to "Sezon", "episode" to "Bölüm", "back" to "Geri", "pause" to "Durdur",
        "resume" to "Devam", "audio" to "Ses", "subtitles" to "Altyazı", "off" to "Kapalı",
        "language" to "Dil", "loading" to "Yükleniyor…",
        "no_description" to "Bu içerik için açıklama bulunamadı.",
        "library_preparing" to "Kütüphane arka planda hazırlanıyor…",
        "people_movies" to "Film ve Dizileri", "search_hint" to "Yüklenen içeriklerde ara",
        "empty" to "İçerik bulunamadı.", "remove_playlist" to "Oynatma listesini kaldır",
    )

    private val en = mapOf(
        "home" to "Home", "live" to "Live TV", "movies" to "Movies", "series" to "Series",
        "search" to "Search", "my_list" to "My List", "settings" to "Settings",
        "trending_movies" to "Trending Movies", "trending_series" to "Trending Series",
        "continue" to "Continue Watching", "for_you" to "For You", "play" to "Play",
        "add_list" to "Add to My List", "remove_list" to "Remove from My List",
        "director" to "Director", "cast" to "Cast", "similar_movies" to "Similar Movies",
        "similar_series" to "Similar Series", "in_playlist" to "In your playlist",
        "not_in_playlist" to "Not in your playlist", "seasons_episodes" to "Seasons & Episodes",
        "season" to "Season", "episode" to "Episode", "back" to "Back", "pause" to "Pause",
        "resume" to "Resume", "audio" to "Audio", "subtitles" to "Subtitles", "off" to "Off",
        "language" to "Language", "loading" to "Loading…",
        "no_description" to "No description is available for this title.",
        "library_preparing" to "Preparing your library in the background…",
        "people_movies" to "Movies & Series", "search_hint" to "Search loaded content",
        "empty" to "No content found.", "remove_playlist" to "Remove playlist",
    )

    private val ar = mapOf(
        "home" to "الرئيسية", "live" to "البث المباشر", "movies" to "الأفلام", "series" to "المسلسلات",
        "search" to "بحث", "my_list" to "قائمتي", "settings" to "الإعدادات",
        "trending_movies" to "أفلام رائجة", "trending_series" to "مسلسلات رائجة",
        "continue" to "متابعة المشاهدة", "for_you" to "لك", "play" to "تشغيل",
        "add_list" to "أضف إلى قائمتي", "remove_list" to "إزالة من قائمتي",
        "director" to "المخرج", "cast" to "طاقم التمثيل", "similar_movies" to "أفلام مشابهة",
        "similar_series" to "مسلسلات مشابهة", "in_playlist" to "موجود في قائمتك",
        "not_in_playlist" to "غير موجود في قائمتك", "seasons_episodes" to "المواسم والحلقات",
        "season" to "الموسم", "episode" to "الحلقة", "back" to "رجوع", "pause" to "إيقاف",
        "resume" to "متابعة", "audio" to "الصوت", "subtitles" to "الترجمة", "off" to "إيقاف",
        "language" to "اللغة", "loading" to "جارٍ التحميل…",
        "no_description" to "لا يوجد وصف متاح لهذا المحتوى.",
        "library_preparing" to "جارٍ تجهيز مكتبتك في الخلفية…",
        "people_movies" to "الأفلام والمسلسلات", "search_hint" to "ابحث في المحتوى المحمّل",
        "empty" to "لم يتم العثور على محتوى.", "remove_playlist" to "إزالة قائمة التشغيل",
    )

    private val de = mapOf(
        "home" to "Startseite", "live" to "Live-TV", "movies" to "Filme", "series" to "Serien",
        "search" to "Suche", "my_list" to "Meine Liste", "settings" to "Einstellungen",
        "trending_movies" to "Trend-Filme", "trending_series" to "Trend-Serien",
        "continue" to "Weiterschauen", "for_you" to "Für dich", "play" to "Abspielen",
        "add_list" to "Zu meiner Liste", "remove_list" to "Aus meiner Liste entfernen",
        "director" to "Regie", "cast" to "Besetzung", "similar_movies" to "Ähnliche Filme",
        "similar_series" to "Ähnliche Serien", "in_playlist" to "In deiner Playlist",
        "not_in_playlist" to "Nicht in deiner Playlist", "seasons_episodes" to "Staffeln & Episoden",
        "season" to "Staffel", "episode" to "Episode", "back" to "Zurück", "pause" to "Pause",
        "resume" to "Fortsetzen", "audio" to "Audio", "subtitles" to "Untertitel", "off" to "Aus",
        "language" to "Sprache", "loading" to "Laden…",
        "no_description" to "Für diesen Titel ist keine Beschreibung verfügbar.",
        "library_preparing" to "Bibliothek wird im Hintergrund vorbereitet…",
        "people_movies" to "Filme & Serien", "search_hint" to "Geladene Inhalte durchsuchen",
        "empty" to "Keine Inhalte gefunden.", "remove_playlist" to "Playlist entfernen",
    )

    private val fr = mapOf(
        "home" to "Accueil", "live" to "TV en direct", "movies" to "Films", "series" to "Séries",
        "search" to "Rechercher", "my_list" to "Ma liste", "settings" to "Paramètres",
        "trending_movies" to "Films tendance", "trending_series" to "Séries tendance",
        "continue" to "Reprendre la lecture", "for_you" to "Pour vous", "play" to "Lire",
        "add_list" to "Ajouter à ma liste", "remove_list" to "Retirer de ma liste",
        "director" to "Réalisateur", "cast" to "Acteurs", "similar_movies" to "Films similaires",
        "similar_series" to "Séries similaires", "in_playlist" to "Dans votre playlist",
        "not_in_playlist" to "Absent de votre playlist", "seasons_episodes" to "Saisons et épisodes",
        "season" to "Saison", "episode" to "Épisode", "back" to "Retour", "pause" to "Pause",
        "resume" to "Reprendre", "audio" to "Audio", "subtitles" to "Sous-titres", "off" to "Désactivé",
        "language" to "Langue", "loading" to "Chargement…",
        "no_description" to "Aucune description n’est disponible pour ce titre.",
        "library_preparing" to "Préparation de votre bibliothèque en arrière-plan…",
        "people_movies" to "Films et séries", "search_hint" to "Rechercher dans le contenu chargé",
        "empty" to "Aucun contenu trouvé.", "remove_playlist" to "Supprimer la playlist",
    )

    private val es = mapOf(
        "home" to "Inicio", "live" to "TV en directo", "movies" to "Películas", "series" to "Series",
        "search" to "Buscar", "my_list" to "Mi lista", "settings" to "Ajustes",
        "trending_movies" to "Películas en tendencia", "trending_series" to "Series en tendencia",
        "continue" to "Seguir viendo", "for_you" to "Para ti", "play" to "Reproducir",
        "add_list" to "Añadir a mi lista", "remove_list" to "Quitar de mi lista",
        "director" to "Director", "cast" to "Reparto", "similar_movies" to "Películas similares",
        "similar_series" to "Series similares", "in_playlist" to "En tu lista",
        "not_in_playlist" to "No está en tu lista", "seasons_episodes" to "Temporadas y episodios",
        "season" to "Temporada", "episode" to "Episodio", "back" to "Atrás", "pause" to "Pausa",
        "resume" to "Continuar", "audio" to "Audio", "subtitles" to "Subtítulos", "off" to "Desactivado",
        "language" to "Idioma", "loading" to "Cargando…",
        "no_description" to "No hay descripción disponible para este título.",
        "library_preparing" to "Preparando tu biblioteca en segundo plano…",
        "people_movies" to "Películas y series", "search_hint" to "Buscar en el contenido cargado",
        "empty" to "No se encontró contenido.", "remove_playlist" to "Eliminar lista",
    )

    private val table: Map<String, String> = when (locale) {
        TvLocale.TR -> tr
        TvLocale.EN -> en
        TvLocale.AR -> ar
        TvLocale.DE -> de
        TvLocale.FR -> fr
        TvLocale.ES -> es
    }

    operator fun get(key: String): String = table[key] ?: tr[key] ?: key
}

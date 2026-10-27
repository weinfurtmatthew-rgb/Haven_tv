package app.haven.launcher.data

import android.content.pm.ApplicationInfo

/** Kinds of apps for the "Movie apps", "Sports apps" and similar rows. An app can be more than one kind. */
enum class AppKind { Tv, Movies, Sports, Music, Kids, Games }

/**
 * Sorts apps into kinds from what's known about common TV apps (package names and names) plus the category each
 * app declares. Pure, so it can be unit tested; a wrong guess only puts an app in one more row.
 */
object AppKinds {
    private val PACKAGES: Map<String, Set<AppKind>> = mapOf(
        "com.netflix.ninja" to setOf(AppKind.Movies, AppKind.Tv),
        "com.amazon.amazonvideo.livingroom" to setOf(AppKind.Movies, AppKind.Tv),
        "com.disney.disneyplus" to setOf(AppKind.Movies, AppKind.Kids),
        "com.wbd.stream" to setOf(AppKind.Movies, AppKind.Tv),
        "com.hbo.hbonow" to setOf(AppKind.Movies, AppKind.Tv),
        "com.hulu.livingroomplus" to setOf(AppKind.Movies, AppKind.Tv),
        "com.apple.atve.androidtv.appletv" to setOf(AppKind.Movies, AppKind.Tv),
        "com.cbs.ott" to setOf(AppKind.Movies, AppKind.Tv, AppKind.Sports),
        "com.peacocktv.peacockandroid" to setOf(AppKind.Movies, AppKind.Tv, AppKind.Sports),
        "com.tubitv" to setOf(AppKind.Movies, AppKind.Tv),
        "tv.pluto.android" to setOf(AppKind.Tv, AppKind.Movies),
        "com.plexapp.android" to setOf(AppKind.Movies, AppKind.Tv),
        "com.google.android.youtube.tv" to setOf(AppKind.Tv),
        "com.google.android.youtube.tvunplugged" to setOf(AppKind.Tv, AppKind.Sports),
        "com.google.android.youtube.tvkids" to setOf(AppKind.Kids),
        "com.google.android.youtube.tvmusic" to setOf(AppKind.Music),
        "com.google.android.videos" to setOf(AppKind.Movies),
        "com.sling" to setOf(AppKind.Tv, AppKind.Sports),
        "com.philo.philo" to setOf(AppKind.Tv),
        "com.spotify.tv.android" to setOf(AppKind.Music),
        "com.pandora.android.atv" to setOf(AppKind.Music),
        "com.amazon.music.tv" to setOf(AppKind.Music),
        "com.apple.android.music" to setOf(AppKind.Music),
        "com.clearchannel.iheartradio.controller" to setOf(AppKind.Music),
        "com.sirius" to setOf(AppKind.Music),
        "tunein.player" to setOf(AppKind.Music),
        "org.pbskids.video" to setOf(AppKind.Kids),
        "com.espn.score_center" to setOf(AppKind.Sports),
        "com.espn.gtv" to setOf(AppKind.Sports),
        "com.foxsports.android" to setOf(AppKind.Sports),
        "com.nfl.mobile" to setOf(AppKind.Sports),
        "com.bamnetworks.mobile.android.gameday.atbat" to setOf(AppKind.Sports),
        "com.nba.app" to setOf(AppKind.Sports),
        "com.dazn" to setOf(AppKind.Sports),
        "com.fubo.firetv.screen" to setOf(AppKind.Sports, AppKind.Tv),
    )

    // Words in package names or app names. Short ones are matched as whole words so "nfl" doesn't hit "inflate".
    private val WORDS: Map<AppKind, List<String>> = mapOf(
        AppKind.Sports to listOf(
            "espn", "fox sports", "foxsports", "nfl", "nba", "mlb", "nhl", "mls", "dazn", "fubo", "sports", "f1 tv", "golf",
            "ufc", "wwe", "bally", "fanduel", "bein", "tsn", "sportsnet", "peacock", "paramount",
        ),
        AppKind.Movies to listOf(
            "netflix", "prime video", "disney", "max", "hbo", "hulu", "apple tv", "paramount", "peacock", "tubi", "pluto", "plex",
            "kanopy", "vudu", "fandango", "starz", "mgm", "showtime", "crackle", "criterion", "mubi", "shudder", "amc+", "movies",
        ),
        AppKind.Tv to listOf(
            "directv", "youtube tv", "sling", "philo", "pluto", "hulu", "pbs", "abc", "nbc", "cbs", "fox now", "the cw", "a&e",
            "hgtv", "discovery", "food network", "history", "tlc", "nat geo", "frndly", "live tv", "channels", "youtube",
        ),
        AppKind.Music to listOf(
            "spotify", "music", "pandora", "iheart", "siriusxm", "tunein", "soundcloud", "tidal", "deezer", "plexamp", "radio",
            "audible", "podcast",
        ),
        AppKind.Kids to listOf(
            "kids", "pbs kids", "nick jr", "noggin", "cartoon", "boomerang", "kidoodle", "khan academy kids", "sesame", "hopster",
            "toddler", "baby", "disney junior",
        ),
    )

    private fun hit(text: String, word: String): Boolean {
        if (word.length > 4 || word.contains(' ')) return text.contains(word)
        return Regex("(^|[^a-z])" + Regex.escape(word) + "([^a-z]|$)").containsMatchIn(text)
    }

    /** The kinds of one app from its package, its name and the category it declares (ApplicationInfo.category). */
    fun of(pkg: String, label: String, category: Int): Set<AppKind> {
        PACKAGES[pkg]?.let { return it }
        val text = (pkg.replace('.', ' ') + " " + label).lowercase()
        val out = HashSet<AppKind>()
        for ((kind, words) in WORDS) if (words.any { hit(text, it) }) out += kind
        when (category) {
            ApplicationInfo.CATEGORY_GAME -> out += AppKind.Games
            ApplicationInfo.CATEGORY_AUDIO -> out += AppKind.Music
            ApplicationInfo.CATEGORY_VIDEO -> if (out.isEmpty()) out += AppKind.Movies
        }
        // Kids' apps are kids' apps, not general movie apps.
        if (AppKind.Kids in out && pkg !in PACKAGES) out -= AppKind.Movies
        return out
    }
}

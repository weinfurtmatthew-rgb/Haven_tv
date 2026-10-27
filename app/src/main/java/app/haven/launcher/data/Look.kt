package app.haven.launcher.data

import org.json.JSONArray
import org.json.JSONObject

// Everything about how Haven looks, chosen per profile. The defaults are the Haven design language (DESIGN.md);
// the colours and fonts behind each choice live in ui/Theme.kt.

/** Colour schemes. All dark except Daylight, which is there for people who want it. */
enum class ThemePreset(val label: String) {
    Hearth("Hearth, warm dark"),
    Midnight("Midnight, blue dark"),
    Forest("Forest, green dark"),
    Plum("Plum, purple dark"),
    Graphite("Graphite, neutral dark"),
    Black("Black, for OLED TVs"),
    Contrast("High contrast"),
    Daylight("Daylight, light"),
}

/** The colour for things that are on or chosen (the "lamp"). */
enum class Accent(val label: String) {
    Lamp("Lamp amber"),
    Coral("Coral"),
    Rose("Rose"),
    Lilac("Lilac"),
    Sky("Sky blue"),
    Mint("Mint"),
    Sage("Sage green"),
    Gold("Gold"),
    Red("Red"),
    Mono("White, no colour"),
}

/** Font for clocks, greetings and titles. */
enum class HeadingFont(val label: String) {
    Fraunces("Fraunces, soft serif"),
    Literata("Literata, book serif"),
    Playfair("Playfair, elegant serif"),
    Bebas("Bebas Neue, tall capitals"),
    Fredoka("Fredoka, rounded"),
    Outfit("Outfit, modern sans"),
    SameAsText("Same as the text font"),
}

/** Font for everything you read or act on. */
enum class TextFont(val label: String) {
    Atkinson("Atkinson Hyperlegible"),
    Lexend("Lexend, easy reading"),
    Nunito("Nunito, rounded"),
    Inter("Inter, clean sans"),
    Literata("Literata, serif"),
    System("The TV's own font"),
}

enum class TextScale(val label: String, val factor: Float) {
    Standard("Standard", 1f),
    Large("Large", 1.12f),
    Larger("Extra large", 1.25f),
}

enum class TileSize(val label: String, val factor: Float) {
    Small("Small", 0.8f),
    Medium("Medium", 1f),
    Large("Large", 1.2f),
    Huge("Extra large", 1.4f),
}

enum class Spacing(val label: String, val factor: Float) {
    Compact("Compact", 0.67f),
    Comfortable("Comfortable", 1f),
    Airy("Airy", 1.4f),
}

enum class Corners(val label: String, val px: Int) {
    Square("Square", 0),
    Slight("Slightly rounded", 6),
    Rounded("Rounded", 12),
    Round("Very rounded", 20),
    Soft("Extra round", 32),
}

/** How the focused thing shows it has focus. Pictures always get a frame. */
enum class FocusLook(val label: String) {
    Fill("Fills with light"),
    Frame("Light frame"),
    ColorFrame("Frame in the accent colour"),
}

enum class Zoom(val label: String, val factor: Float) {
    None("No zoom", 0f),
    Subtle("Subtle", 0.5f),
    Standard("Standard", 1f),
    Big("Big", 1.6f),
}

enum class Motion(val label: String) {
    Smooth("Smooth"),
    Reduced("Reduced, no animation"),
}

enum class AppNames(val label: String) {
    OnFocus("Under the focused app"),
    Always("Under every app"),
    Never("Never"),
}

/** What an app tile shows when the app has no picture of its own chosen. */
enum class AppArt(val label: String) {
    Banner("The app's TV banner"),
    Icon("The app's icon on a tile"),
}

enum class ClockStyle(val label: String) {
    Large("Large"),
    Small("Small"),
    Hidden("Hidden"),
}

/** Rows on the home screen, in the order the profile chose. */
enum class HomeRow(val label: String) {
    Favorites("Favorites"),
    Recent("Recently used"),
    Apps("All apps"),
    Room("This room"),
}

data class LookConfig(
    val theme: ThemePreset = ThemePreset.Hearth,
    val accent: Accent = Accent.Lamp,
    val headingFont: HeadingFont = HeadingFont.Fraunces,
    val textFont: TextFont = TextFont.Atkinson,
    val textScale: TextScale = TextScale.Standard,
    val favSize: TileSize = TileSize.Medium,
    val appSize: TileSize = TileSize.Medium,
    val spacing: Spacing = Spacing.Comfortable,
    val corners: Corners = Corners.Rounded,
    val focus: FocusLook = FocusLook.Fill,
    val zoom: Zoom = Zoom.Standard,
    val motion: Motion = Motion.Smooth,
    val appNames: AppNames = AppNames.OnFocus,
    val appArt: AppArt = AppArt.Banner,
    val clock: ClockStyle = ClockStyle.Large,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val showRoomChip: Boolean = true,
    /** Every row, in the profile's order; [rowsShown] says which appear. */
    val rowOrder: List<HomeRow> = HomeRow.values().toList(),
    val rowsShown: Set<HomeRow> = setOf(HomeRow.Favorites, HomeRow.Apps, HomeRow.Room),
    /** Profile picture colour in the profile picker. */
    val avatar: Accent = Accent.Lamp,
) {
    val rows: List<HomeRow> get() = rowOrder.filter { it in rowsShown }

    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("accent", accent.name)
        .put("headingFont", headingFont.name)
        .put("textFont", textFont.name)
        .put("textScale", textScale.name)
        .put("favSize", favSize.name)
        .put("appSize", appSize.name)
        .put("spacing", spacing.name)
        .put("corners", corners.name)
        .put("focus", focus.name)
        .put("zoom", zoom.name)
        .put("motion", motion.name)
        .put("appNames", appNames.name)
        .put("appArt", appArt.name)
        .put("clock", clock.name)
        .put("showDate", showDate)
        .put("showWeather", showWeather)
        .put("showRoomChip", showRoomChip)
        .put("rowOrder", JSONArray(rowOrder.map { it.name }))
        .put("rowsShown", JSONArray(rowsShown.map { it.name }))
        .put("avatar", avatar.name)

    companion object {
        fun fromJson(o: JSONObject?): LookConfig {
            val d = LookConfig()
            if (o == null) return d
            return LookConfig(
                theme = enumOr(o.optString("theme"), d.theme),
                accent = enumOr(o.optString("accent"), d.accent),
                headingFont = enumOr(o.optString("headingFont"), d.headingFont),
                textFont = enumOr(o.optString("textFont"), d.textFont),
                textScale = enumOr(o.optString("textScale"), d.textScale),
                favSize = enumOr(o.optString("favSize"), d.favSize),
                appSize = enumOr(o.optString("appSize"), d.appSize),
                spacing = enumOr(o.optString("spacing"), d.spacing),
                corners = enumOr(o.optString("corners"), d.corners),
                focus = enumOr(o.optString("focus"), d.focus),
                zoom = enumOr(o.optString("zoom"), d.zoom),
                motion = enumOr(o.optString("motion"), d.motion),
                appNames = enumOr(o.optString("appNames"), d.appNames),
                appArt = enumOr(o.optString("appArt"), d.appArt),
                clock = enumOr(o.optString("clock"), d.clock),
                showDate = o.optBoolean("showDate", d.showDate),
                showWeather = o.optBoolean("showWeather", d.showWeather),
                showRoomChip = o.optBoolean("showRoomChip", d.showRoomChip),
                rowOrder = completeOrder(enumList<HomeRow>(o.optJSONArray("rowOrder"))),
                rowsShown = if (o.has("rowsShown")) enumList<HomeRow>(o.optJSONArray("rowsShown")).toSet() else d.rowsShown,
                avatar = enumOr(o.optString("avatar"), d.avatar),
            )
        }
    }
}

// ---- background ---------------------------------------------------------------------------------

enum class BgKind(val label: String) {
    Theme("The theme's colour"),
    Color("A colour"),
    Gradient("A gradient"),
    Photo("Your photos"),
    Daily("Photo of the day"),
    Art("Paintings"),
    Posters("Movie and show posters"),
    NowPlaying("What's playing"),
}

/** Gradients, dark enough for text on top (Linen is the light one, for the Daylight scheme). */
enum class GradientPreset(val label: String, val colors: List<Long>) {
    Dusk("Dusk", listOf(0xFF151A2E, 0xFF3A2442, 0xFF6E3B3B)),
    Dawn("Dawn", listOf(0xFF161827, 0xFF443650, 0xFF9C6A52)),
    Ocean("Ocean", listOf(0xFF05131D, 0xFF0E3A4E, 0xFF1D6670)),
    Forest("Forest", listOf(0xFF0A1811, 0xFF1D3A28, 0xFF3D5A38)),
    Ember("Ember", listOf(0xFF190E09, 0xFF471E12, 0xFF84391C)),
    Aurora("Aurora", listOf(0xFF07191E, 0xFF0F3A39, 0xFF392A5B)),
    Slate("Slate", listOf(0xFF0F1317, 0xFF222A32, 0xFF38434E)),
    Linen("Linen, light", listOf(0xFFF7F2EA, 0xFFEDE3D3, 0xFFE2D2BC)),
}

/** Background colours to pick from on the TV; the phone page can set any colour. */
enum class BgColor(val label: String, val argb: Long) {
    Hearth("Hearth", 0xFF14120F),
    Navy("Night blue", 0xFF0F1A2A),
    Pine("Pine", 0xFF102018),
    Wine("Wine", 0xFF2A1018),
    Aubergine("Aubergine", 0xFF22142A),
    Slate("Slate", 0xFF1C2228),
    Teal("Deep teal", 0xFF0E2626),
    Cocoa("Cocoa", 0xFF2C1810),
    Black("Black", 0xFF000000),
    Sand("Sand, light", 0xFFE9E1D3),
}

enum class BgBlur(val label: String) {
    None("Sharp"),
    Soft("Soft"),
    Strong("Very soft"),
}

enum class PosterKind(val label: String, val path: String) {
    Movies("Movies", "movie"),
    Shows("TV shows", "tv"),
    Both("Movies and TV shows", "all"),
}

enum class PosterStyle(val label: String) {
    Backdrop("One big picture"),
    Wall("A wall of posters"),
}

data class BackgroundConfig(
    val kind: BgKind = BgKind.Theme,
    val color: Long = BgColor.Navy.argb,
    val gradient: GradientPreset = GradientPreset.Dusk,
    /** Uploaded background file name; empty = take turns through all of them. */
    val photo: String = "",
    /** How much the picture is darkened under Haven's text, 0..80. */
    val dimPct: Int = 45,
    val blur: BgBlur = BgBlur.None,
    /** Minutes between pictures for photos, paintings and posters. */
    val everyMin: Int = 30,
    val posters: PosterKind = PosterKind.Movies,
    val posterStyle: PosterStyle = PosterStyle.Backdrop,
    /** The person's own free TMDB API key (v3 key or v4 read token); posters need it. */
    val tmdbKey: String = "",
) {
    val isPicture: Boolean get() = kind !in setOf(BgKind.Theme, BgKind.Color, BgKind.Gradient)

    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name)
        .put("color", color)
        .put("gradient", gradient.name)
        .put("photo", photo)
        .put("dimPct", dimPct)
        .put("blur", blur.name)
        .put("everyMin", everyMin)
        .put("posters", posters.name)
        .put("posterStyle", posterStyle.name)
        .put("tmdbKey", tmdbKey)

    companion object {
        fun fromJson(o: JSONObject?): BackgroundConfig {
            val d = BackgroundConfig()
            if (o == null) return d
            return BackgroundConfig(
                kind = enumOr(o.optString("kind"), d.kind),
                color = o.optLong("color", d.color) or 0xFF000000,
                gradient = enumOr(o.optString("gradient"), d.gradient),
                photo = o.optString("photo", d.photo),
                dimPct = o.optInt("dimPct", d.dimPct).coerceIn(0, 80),
                blur = enumOr(o.optString("blur"), d.blur),
                everyMin = o.optInt("everyMin", d.everyMin).coerceIn(1, 1440),
                posters = enumOr(o.optString("posters"), d.posters),
                posterStyle = enumOr(o.optString("posterStyle"), d.posterStyle),
                tmdbKey = o.optString("tmdbKey", d.tmdbKey).trim(),
            )
        }
    }
}

// ---- apps -----------------------------------------------------------------------------------------

enum class AppStyle(val label: String) {
    Auto("As set for all apps"),
    Banner("Its TV banner"),
    Icon("Its icon on a colour"),
    Picture("Your picture"),
}

/** Tile colours for an app shown as its icon; 0 is the theme's own tile colour. */
enum class TileColor(val label: String, val argb: Long) {
    Theme("The theme's tile colour", 0),
    Linen("Linen", 0xFFF2EDE4),
    White("White", 0xFFFFFFFF),
    Hearth("Hearth", 0xFF14120F),
    Navy("Night blue", 0xFF1B2A44),
    Pine("Pine", 0xFF1E3A2B),
    Wine("Wine", 0xFF4A1C2A),
    Plum("Plum", 0xFF3A2348),
    Slate("Slate", 0xFF2E3742),
    Teal("Deep teal", 0xFF14403F),
    Cocoa("Cocoa", 0xFF4A2E1E),
    Amber("Amber", 0xFFF2B35E),
    Coral("Coral", 0xFFF4927A),
    Rose("Rose", 0xFFF29BBA),
    Lilac("Lilac", 0xFFBFA8F5),
    Sky("Sky blue", 0xFF86C8F2),
    Mint("Mint", 0xFF82DDBF),
    Gold("Gold", 0xFFE9CB5E),
    Red("Red", 0xFFE05A4E),
}

/** One app's own name and picture on this profile's home screen. */
data class AppLook(
    val name: String = "",
    val style: AppStyle = AppStyle.Auto,
    /** Tile colour behind the icon (ARGB); 0 = the theme's tile colour. */
    val color: Long = 0,
    /** Uploaded picture's file name in the app pictures folder. */
    val image: String = "",
) {
    val isDefault: Boolean get() = this == AppLook()

    fun toJson(): JSONObject = JSONObject().put("name", name).put("style", style.name).put("color", color).put("image", image)

    companion object {
        fun fromJson(o: JSONObject?): AppLook {
            if (o == null) return AppLook()
            return AppLook(
                name = o.optString("name").trim(),
                style = enumOr(o.optString("style"), AppStyle.Auto),
                color = o.optLong("color", 0),
                image = o.optString("image"),
            )
        }
    }
}

fun appLooksToJson(m: Map<String, AppLook>): JSONObject {
    val o = JSONObject()
    for ((pkg, look) in m) if (!look.isDefault) o.put(pkg, look.toJson())
    return o
}

fun appLooksFromJson(o: JSONObject?): Map<String, AppLook> {
    if (o == null) return emptyMap()
    return o.keys().asSequence().associateWith { AppLook.fromJson(o.optJSONObject(it)) }.filterValues { !it.isDefault }
}

// ---- helpers --------------------------------------------------------------------------------------

inline fun <reified E : Enum<E>> enumList(arr: JSONArray?): List<E> =
    arr.strings().mapNotNull { n -> enumValues<E>().firstOrNull { it.name == n } }.distinct()

/** A saved order plus any values added since it was saved, so new rows or sections never go missing. */
inline fun <reified E : Enum<E>> completeOrder(saved: List<E>): List<E> = (saved + enumValues<E>()).distinct()

/** Moves [item] one place up (-1) or down (+1). */
fun <T> List<T>.moved(item: T, by: Int): List<T> {
    val i = indexOf(item)
    if (i < 0) return this
    val j = (i + by).coerceIn(0, size - 1)
    if (i == j) return this
    val m = toMutableList()
    m.removeAt(i)
    m.add(j, item)
    return m
}

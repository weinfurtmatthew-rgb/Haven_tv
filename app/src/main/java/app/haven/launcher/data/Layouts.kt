package app.haven.launcher.data

// Layouts: whole home screens to start from. A layout is a recipe of parts (how you get around, what fills the
// top of the screen, which rows, and a look); picking one sets all of them, and each part stays its own setting,
// so parts from different layouts mix. The parts live in LookConfig, so they follow the profile.

enum class HomeLayout(val label: String, val note: String) {
    Standard("Standard", "Haven as it came: the clock bar and rows of apps"),
    Spotlight("Spotlight", "A big photo with the clock tucked behind the scenery, and glass tiles"),
    BigTiles("Big tiles", "A menu down the side and big boxes, three across"),
    GameDay("Game day", "Your team's scoreboard, today's games and sports apps"),
    Cinema("Cinema", "A featured movie, posters and your movie apps"),
    Shows("Shows", "Your shows, tonight on TV and what's new on streaming"),
    SmartHome("Smart home", "The house at a glance: scenes, lights, climate, cameras, locks"),
    Music("Music", "What's playing, big, with every speaker and your music apps"),
    Kids("Kids", "Huge friendly tiles with only the apps a grown-up picked"),
}

/** How you get around the home screen. */
enum class NavStyle(val label: String) {
    Rows("One page of rows"),
    Menu("A menu down the side"),
    Tabs("Tabs along the top"),
}

/** What fills the top of the home page. */
enum class HeroKind(val label: String, val note: String) {
    ClockBar("Clock bar", "The clock, date and buttons in one line"),
    Spotlight("A big photo with the clock", "The clock tucks behind mountains, buildings or trees"),
    Scoreboard("Your team's scoreboard", "The live score, or the next game and its channel"),
    Movie("A featured movie", "What's trending, with where to watch it (TMDB key)"),
    Show("A featured show", "New tonight from the shows you follow, or what's on"),
    NowPlaying("What's playing", "Cover, song, progress and controls"),
    HomeStatus("How the house is", "Weather, lights, locks, doors and temperature"),
    Welcome("A big hello", "Name, picture and bedtime, for kids"),
}

/** Pages reached from the side menu or the tabs. Some just do something (open live TV, search, settings). */
enum class HomeSection(val label: String) {
    Home("Home"),
    LiveTv("Live TV"),
    Sports("Sports"),
    Movies("Movies"),
    Shows("Shows"),
    Music("Music"),
    SmartHome("Smart home"),
    Kids("Kids"),
    Apps("All apps"),
    Search("Search"),
    Settings("Settings"),
}

/** Favorites and app rows as a scrolling row, or as a grid of boxes. */
enum class AppGrid(val label: String, val columns: Int) {
    Rows("In a row that scrolls", 0),
    Three("A grid, 3 across", 3),
    Four("A grid, 4 across", 4),
    Five("A grid, 5 across", 5),
}

/** Tiles and cards: solid colour, or frosted glass that lets the background through. */
enum class Surface(val label: String) {
    Solid("Solid"),
    Glass("Glass, frosted and see-through"),
}

/** Where Spotlight's big photo comes from. */
enum class SpotSource(val label: String) {
    Daily("Photo of the day"),
    Photos("Your photos"),
    Paintings("Paintings"),
    Posters("Movie and show pictures (TMDB key)"),
    Background("The same picture as the background"),
}

/** The clock on Spotlight's photo. */
enum class SpotClock(val label: String) {
    Behind("Behind the scenery"),
    Front("In front of the photo"),
    Hidden("Hidden"),
}

/** Which Home Assistant devices the smart home rows show. */
enum class HomeScope(val label: String) {
    Room("This room"),
    House("The whole house"),
}

object Layouts {
    val DEFAULT_SECTIONS: Set<HomeSection> = setOf(
        HomeSection.Home, HomeSection.LiveTv, HomeSection.Sports, HomeSection.Movies, HomeSection.Shows,
        HomeSection.Music, HomeSection.SmartHome, HomeSection.Apps, HomeSection.Settings,
    )

    /** One layout's parts, its look (colours, fonts, sizes) and its background. */
    class Recipe(
        val nav: NavStyle,
        val hero: HeroKind,
        val rows: List<HomeRow>,
        val sections: List<HomeSection> = HomeSection.values().filter { it in DEFAULT_SECTIONS },
        val grid: AppGrid = AppGrid.Rows,
        val surface: Surface = Surface.Solid,
        val look: (LookConfig) -> LookConfig,
        val background: (BackgroundConfig) -> BackgroundConfig = { it },
    )

    fun recipe(l: HomeLayout): Recipe = when (l) {
        HomeLayout.Standard -> Recipe(
            NavStyle.Rows, HeroKind.ClockBar, listOf(HomeRow.Favorites, HomeRow.Apps, HomeRow.Room),
            look = { LookConfig() },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.Spotlight -> Recipe(
            NavStyle.Rows, HeroKind.Spotlight, listOf(HomeRow.Favorites, HomeRow.Apps, HomeRow.Room),
            surface = Surface.Glass,
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Graphite, accent = Accent.Sky, headingFont = HeadingFont.Outfit, textFont = TextFont.Inter,
                    corners = Corners.Round, focus = FocusLook.Lift, zoom = Zoom.Big, clock = ClockStyle.Hidden, showDate = false,
                )
            },
            background = { it.copy(kind = BgKind.Daily, blur = BgBlur.Strong, dimPct = 40) },
        )
        HomeLayout.BigTiles -> Recipe(
            NavStyle.Menu, HeroKind.ClockBar, listOf(HomeRow.Favorites, HomeRow.Apps),
            grid = AppGrid.Three,
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Plum, accent = Accent.Lilac, headingFont = HeadingFont.Outfit, textFont = TextFont.Inter,
                    corners = Corners.Slight, focus = FocusLook.Frame, zoom = Zoom.Subtle, favSize = TileSize.Medium,
                    clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.GameDay -> Recipe(
            NavStyle.Rows, HeroKind.Scoreboard,
            listOf(HomeRow.YourTeams, HomeRow.LiveScores, HomeRow.SportsApps, HomeRow.SportsNews, HomeRow.Favorites),
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Graphite, accent = Accent.Red, headingFont = HeadingFont.Bebas, textFont = TextFont.Inter,
                    corners = Corners.Slight, clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.Cinema -> Recipe(
            NavStyle.Rows, HeroKind.Movie,
            listOf(HomeRow.TrendingMovies, HomeRow.InTheaters, HomeRow.MovieApps, HomeRow.ComingSoon, HomeRow.TopMovies, HomeRow.Favorites),
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Black, accent = Accent.Gold, headingFont = HeadingFont.Playfair, textFont = TextFont.Inter,
                    corners = Corners.Slight, focus = FocusLook.Frame, clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.Shows -> Recipe(
            NavStyle.Tabs, HeroKind.Show,
            listOf(HomeRow.YourShows, HomeRow.Tonight, HomeRow.NewStreaming, HomeRow.TvApps, HomeRow.TrendingShows, HomeRow.Favorites),
            sections = listOf(HomeSection.Home, HomeSection.LiveTv, HomeSection.Movies, HomeSection.Sports, HomeSection.Music, HomeSection.Apps),
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Midnight, accent = Accent.Sky, headingFont = HeadingFont.Outfit, textFont = TextFont.Inter,
                    corners = Corners.Rounded, clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.SmartHome -> Recipe(
            NavStyle.Rows, HeroKind.HomeStatus,
            listOf(
                HomeRow.Scenes, HomeRow.Lights, HomeRow.Climate, HomeRow.Cameras, HomeRow.Locks, HomeRow.Speakers,
                HomeRow.Sensors, HomeRow.Automations, HomeRow.Favorites,
            ),
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Forest, accent = Accent.Mint, headingFont = HeadingFont.Outfit, textFont = TextFont.Inter,
                    corners = Corners.Round, clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.Theme) },
        )
        HomeLayout.Music -> Recipe(
            NavStyle.Rows, HeroKind.NowPlaying, listOf(HomeRow.Speakers, HomeRow.MusicApps, HomeRow.Favorites),
            surface = Surface.Glass,
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Plum, accent = Accent.Lilac, headingFont = HeadingFont.Outfit, textFont = TextFont.Lexend,
                    corners = Corners.Round, focus = FocusLook.Lift, zoom = Zoom.Big, clock = ClockStyle.Small,
                )
            },
            background = { it.copy(kind = BgKind.NowPlaying, blur = BgBlur.Strong, dimPct = 55) },
        )
        HomeLayout.Kids -> Recipe(
            NavStyle.Rows, HeroKind.Welcome, listOf(HomeRow.KidsApps),
            grid = AppGrid.Three,
            look = {
                LookConfig().copy(
                    theme = ThemePreset.Daylight, accent = Accent.Coral, headingFont = HeadingFont.Fredoka, textFont = TextFont.Nunito,
                    textScale = TextScale.Large, favSize = TileSize.Huge, appSize = TileSize.Huge, corners = Corners.Soft,
                    zoom = Zoom.Big, appNames = AppNames.Always, clock = ClockStyle.Hidden, showWeather = false, showRoomChip = false,
                )
            },
            background = { it.copy(kind = BgKind.Gradient, gradient = GradientPreset.Sky) },
        )
    }

    /** [look] with [r]'s parts: rows, menu, top of the screen, grid and surface. */
    private fun withParts(look: LookConfig, l: HomeLayout, r: Recipe): LookConfig = look.copy(
        layout = l,
        nav = r.nav,
        hero = r.hero,
        rowOrder = completeOrder(r.rows + look.rowOrder.filter { it !in r.rows }),
        rowsShown = r.rows.toSet(),
        sectionOrder = completeOrder(r.sections + look.sectionOrder.filter { it !in r.sections }),
        sectionsShown = r.sections.toSet(),
        appGrid = r.grid,
        surface = r.surface,
    )

    /**
     * The profile with [l] applied: its parts always; its colours, fonts, sizes and background too when the profile
     * lets layouts bring their look. The profile picture colour and its own choices for Spotlight stay.
     */
    fun apply(s: Settings, l: HomeLayout): Settings {
        val r = recipe(l)
        val cur = s.look
        if (!cur.layoutLook) return s.copy(look = withParts(cur, l, r))
        val look = r.look(cur).copy(
            avatar = cur.avatar,
            layoutLook = true,
            spotSource = cur.spotSource,
            spotClock = cur.spotClock,
            spotFollow = cur.spotFollow,
        )
        return s.copy(look = withParts(look, l, r), background = r.background(s.background))
    }

    /** True while the profile's parts are still exactly its layout's recipe. */
    fun matches(look: LookConfig): Boolean {
        val r = recipe(look.layout)
        return look.nav == r.nav && look.hero == r.hero && look.rows == r.rows && look.appGrid == r.grid &&
            look.surface == r.surface && (look.nav == NavStyle.Rows || look.sections == r.sections)
    }

    /** "Game day", or "Game day, changed" once a part differs from the recipe. */
    fun label(look: LookConfig): String = look.layout.label + if (matches(look)) "" else ", changed"

    /** The page a menu or tab section shows: Home is the profile's own; the others borrow a layout's top and rows. */
    fun page(section: HomeSection, look: LookConfig): Pair<HeroKind, List<HomeRow>> {
        fun of(l: HomeLayout) = recipe(l).let { it.hero to it.rows.filter { r -> r != HomeRow.Favorites } }
        return when (section) {
            HomeSection.Sports -> of(HomeLayout.GameDay)
            HomeSection.Movies -> of(HomeLayout.Cinema)
            HomeSection.Shows -> of(HomeLayout.Shows)
            HomeSection.Music -> of(HomeLayout.Music)
            HomeSection.SmartHome -> of(HomeLayout.SmartHome)
            HomeSection.Kids -> HeroKind.Welcome to listOf(HomeRow.KidsApps)
            HomeSection.Apps -> HeroKind.ClockBar to listOf(HomeRow.Apps)
            else -> look.hero to look.rows
        }
    }

    /** Sections that do something instead of showing a page. */
    fun isAction(section: HomeSection): Boolean =
        section == HomeSection.LiveTv || section == HomeSection.Search || section == HomeSection.Settings
}

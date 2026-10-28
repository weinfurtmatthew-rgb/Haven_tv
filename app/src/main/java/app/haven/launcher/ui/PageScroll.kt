package app.haven.launcher.ui

/**
 * How far a home page scrolls to show something [size] tall whose top is [offset] below the top of the screen
 * ([screen] tall), with the page scrolled [now] out of at most [max]. Anything in the page's first screenful shows
 * the page's very top, and anything in its last screenful its very bottom, so neither end is left half cut off. In
 * between, the page moves as little as it can while keeping [margin] clear above (the row's name) and below. Pure,
 * so it's tested.
 */
fun pageScrollBy(now: Float, offset: Float, size: Float, screen: Float, max: Float, margin: Float): Float {
    val top = offset + now
    val bottom = top + size
    val target = when {
        bottom + margin <= screen -> 0f
        top - margin >= max -> max
        else -> {
            val least = bottom + margin - screen // the least scroll that shows all of it, with room below
            val most = top - margin // the most that still shows its row's name
            if (least > most) most else now.coerceIn(least, most)
        }
    }
    return target.coerceIn(0f, max) - now
}

/**
 * The same for a Spotlight page, whose [photo] across the top is whole or gone: anything on the photo goes back to
 * the top; below it the photo stays out of sight and the page moves as [pageScrollBy] does, [title] being the
 * margin. Pure, so it's tested.
 */
fun spotlightScrollBy(now: Float, offset: Float, size: Float, screen: Float, photo: Float, title: Float, max: Float): Float {
    val top = offset + now // where it sits on the page
    if (top < photo) return -now
    if (top - title >= max) return max - now // the last screenful: all of the page's bottom
    val least = top + size + title - screen // the least scroll that shows all of it, with room below
    val most = top - title // the most that still shows its row's name
    val target = if (least > most) most else now.coerceIn(least, most)
    return maxOf(target, photo).coerceAtMost(max) - now
}

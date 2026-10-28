package app.haven.launcher.ui

/**
 * How far a Spotlight page scrolls to show something [size] tall whose top is [offset] below the top of the screen
 * ([screen] tall), with the page scrolled [now] and a photo [photo] tall across the page's top. On the photo the
 * page goes back to the top, so the photo is whole. Below it the photo stays out of sight and the page moves as
 * little as it can while showing the whole thing and the [title] space above it (its row's name). Pure, so it's
 * tested.
 */
fun spotlightScrollBy(now: Float, offset: Float, size: Float, screen: Float, photo: Float, title: Float): Float {
    val top = offset + now // where it sits on the page
    if (top < photo) return -now
    val least = top + size - screen // the least scroll that shows all of it
    val most = top - title // the most that still shows its row's name
    val target = if (least > most) most else now.coerceIn(least, most)
    return maxOf(target, photo) - now
}

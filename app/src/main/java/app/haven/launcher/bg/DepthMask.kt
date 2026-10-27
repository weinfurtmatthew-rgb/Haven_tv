package app.haven.launcher.bg

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the scenery in a photo, so Spotlight's clock can sit in the sky with mountains, buildings and trees in
 * front of it. The sky is filled in from the top edge, a pixel at a time, while the colour changes as gently as a
 * sky does; what's left and stands on the bottom edge is scenery (the sun, the moon, clouds and birds float, so
 * they stay behind). Photos without a plausible sky give null and the clock simply sits in front. Pure, so tested.
 */
object DepthMask {
    /** Colour change allowed between neighbouring sky pixels of the small copy, and from the sky around them. */
    private const val STEP = 9f
    private const val JOIN = 30f

    /** Which pixels of a [w] x [h] copy of the photo are scenery. [top] is each column's first scenery row ([h] = none). */
    class Scene(val w: Int, val h: Int, val fg: BooleanArray) {
        val top: IntArray = IntArray(w) { x -> (0 until h).firstOrNull { fg[it * w + x] } ?: h }
    }

    /** Where the clock goes: its baseline (pixels in the photo) and whether scenery should cover it. */
    data class Placement(val baseline: Int, val depth: Boolean)

    private fun r(p: Int) = (p shr 16 and 0xFF).toFloat()
    private fun g(p: Int) = (p shr 8 and 0xFF).toFloat()
    private fun b(p: Int) = (p and 0xFF).toFloat()

    fun scene(px: IntArray, w: Int, h: Int): Scene? {
        if (w < 16 || h < 16 || px.size < w * h) return null
        val n = w * h
        // The small copy is already an average of many photo pixels, so no blur: edges stay one or two pixels wide.
        val cr = FloatArray(n) { r(px[it]) }
        val cg = FloatArray(n) { g(px[it]) }
        val cb = FloatArray(n) { b(px[it]) }
        fun dist(i: Int, j: Int): Float {
            val dr = cr[i] - cr[j]
            val dg = cg[i] - cg[j]
            val db = cb[i] - cb[j]
            return sqrt(dr * dr + dg * dg + db * db)
        }
        // Seeds: the top edge where it's smooth, as a sky is. A busy top edge (leaves, a wall) means no sky.
        val sky = BooleanArray(n)
        val rr = FloatArray(n)
        val rg = FloatArray(n)
        val rb = FloatArray(n)
        val queue = IntArray(n)
        val parent = IntArray(n) { -1 }
        var head = 0
        var tail = 0
        for (x in 0 until w) {
            val i = x
            val smooth = dist(i, i + w) < STEP && (x == w - 1 || dist(i, i + 1) < STEP) && (x == 0 || dist(i, i - 1) < STEP)
            if (!smooth) continue
            sky[i] = true
            parent[i] = i
            rr[i] = cr[i]
            rg[i] = cg[i]
            rb[i] = cb[i]
            queue[tail++] = i
        }
        if (tail < w / 2) return null
        while (head < tail) {
            val p = queue[head++]
            val x = p % w
            val y = p / w
            for (d in 0 until 4) {
                val q = when (d) {
                    0 -> if (y + 1 < h) p + w else -1
                    1 -> if (x + 1 < w) p + 1 else -1
                    2 -> if (x > 0) p - 1 else -1
                    else -> if (y > 0) p - w else -1
                }
                if (q < 0 || sky[q]) continue
                // A soft edge spread over two pixels is still an edge: check against the step before too.
                if (dist(p, q) >= STEP || dist(parent[p], q) >= STEP * 1.6f) continue
                val dr = cr[q] - rr[p]
                val dg = cg[q] - rg[p]
                val db = cb[q] - rb[p]
                if (sqrt(dr * dr + dg * dg + db * db) >= JOIN) continue
                sky[q] = true
                parent[q] = p
                // The sky's colour as it was on the way here: follows a gradient, not a soft edge.
                rr[q] = rr[p] * 0.9f + cr[q] * 0.1f
                rg[q] = rg[p] * 0.9f + cg[q] * 0.1f
                rb[q] = rb[p] * 0.9f + cb[q] * 0.1f
                queue[tail++] = q
            }
        }
        // Scenery is what isn't sky and stands on the bottom edge; anything else floats in the sky.
        val fg = BooleanArray(n)
        head = 0
        tail = 0
        for (x in 0 until w) {
            val i = (h - 1) * w + x
            if (!sky[i]) {
                fg[i] = true
                queue[tail++] = i
            }
        }
        while (head < tail) {
            val p = queue[head++]
            val x = p % w
            val y = p / w
            for (d in 0 until 4) {
                val q = when (d) {
                    0 -> if (y > 0) p - w else -1
                    1 -> if (x + 1 < w) p + 1 else -1
                    2 -> if (x > 0) p - 1 else -1
                    else -> if (y + 1 < h) p + w else -1
                }
                if (q < 0 || fg[q] || sky[q]) continue
                fg[q] = true
                queue[tail++] = q
            }
        }
        val skyCount = sky.count { it }
        if (skyCount < n / 6) return null
        return Scene(w, h, fg)
    }

    /**
     * The scenery's top edge at full size ([fw] x [fh]), each column moved to the strongest colour change near
     * where the small copy put it, so the edge lines up with the real photo to the pixel.
     */
    fun edge(scene: Scene, full: IntArray, fw: Int, fh: Int): IntArray {
        val sx = scene.w.toFloat() / fw
        val sy = fh.toFloat() / scene.h
        val reach = max(4, sy.toInt() + 2)
        val out = IntArray(fw) { x ->
            val t = scene.top[min(scene.w - 1, (x * sx).toInt())]
            if (t >= scene.h) return@IntArray fh
            val c = (t * sy).toInt()
            var best = c
            var bestD = -1f
            for (y in max(1, c - reach)..min(fh - 2, c + reach)) {
                val above = full[(y - 1) * fw + x]
                val below = full[(y + 1) * fw + x]
                val d = abs(r(above) - r(below)) + abs(g(above) - g(below)) + abs(b(above) - b(below))
                if (d > bestD) {
                    bestD = d
                    best = y
                }
            }
            best
        }
        return median(out, 3)
    }

    private fun median(a: IntArray, window: Int): IntArray {
        val half = window / 2
        return IntArray(a.size) { i ->
            val part = (max(0, i - half)..min(a.size - 1, i + half)).map { a[it] }.sorted()
            part[part.size / 2]
        }
    }

    /**
     * Where to put a clock [width] x [capHeight] centred at [centerX], its baseline between [minBaseline] and
     * [maxBaseline]: low enough that scenery covers about a fifth of the digits, never so much of any digit that it
     * stops reading as a time. A flat horizon isn't scenery to hide behind, and without scenery in reach the clock
     * sits at its highest place in front of everything.
     */
    fun place(edge: IntArray?, centerX: Int, width: Int, capHeight: Int, minBaseline: Int, maxBaseline: Int): Placement {
        val front = Placement(minBaseline, false)
        if (edge == null || capHeight <= 0 || maxBaseline < minBaseline || edge.isEmpty()) return front
        val x0 = (centerX - width / 2).coerceIn(0, edge.size - 1)
        val x1 = (centerX + width / 2).coerceIn(0, edge.size - 1)
        val span = (x0..x1).map { edge[it] }.sorted()
        val relief = span[span.size * 9 / 10] - span[span.size / 10]
        if (relief < capHeight / 5) return front
        var best: Int? = null
        var bestScore = Float.MAX_VALUE
        var base = minBaseline
        while (base <= maxBaseline) {
            val covered = span.map { e -> (base - max(e, base - capHeight)).coerceIn(0, capHeight).toFloat() / capHeight }
            val mean = covered.average().toFloat()
            val p90 = covered.sorted()[covered.size * 9 / 10]
            if (mean in 0.08f..0.36f && p90 <= 0.62f) {
                val score = abs(mean - 0.2f)
                if (score < bestScore) {
                    bestScore = score
                    best = base
                }
            }
            base += 4
        }
        return if (best != null) Placement(best, true) else front
    }

    /**
     * The scenery in front of the clock: the photo's pixels inside [x], [y], [w], [h] of the full photo where the
     * scene is scenery, with a soft two-pixel top edge. Transparent everywhere else.
     */
    fun cutout(full: IntArray, fw: Int, fh: Int, scene: Scene, edge: IntArray, x: Int, y: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        val sx = scene.w.toFloat() / fw
        val sy = scene.h.toFloat() / fh
        for (j in 0 until h) {
            val fy = y + j
            if (fy < 0 || fy >= fh) continue
            for (i in 0 until w) {
                val fx = x + i
                if (fx < 0 || fx >= fw) continue
                val top = edge[fx]
                var a = ((fy - top + 1) / 2f).coerceIn(0f, 1f)
                if (a <= 0f) continue
                // Below the edge, gaps in the scenery (sky between branches) stay clear.
                val cx = min(scene.w - 1, (fx * sx).toInt())
                val cy = min(scene.h - 1, (fy * sy).toInt())
                if (fy > top + 2 && !scene.fg[cy * scene.w + cx]) a = 0f
                if (a <= 0f) continue
                val p = full[fy * fw + fx]
                out[j * w + i] = ((a * 255).toInt() shl 24) or (p and 0xFFFFFF)
            }
        }
        return out
    }
}

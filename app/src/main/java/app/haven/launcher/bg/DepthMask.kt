package app.haven.launcher.bg

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the scenery in a photo, so Spotlight's clock can sit in the sky with mountains, buildings and trees in
 * front of it. The sky is filled in from the top edge, a pixel at a time, while the colour changes as gently as a
 * sky does (or brightens smoothly, as it does round a street lamp or the sun); what's left and stands on the bottom
 * edge is scenery (the sun, the moon, clouds and birds float, so they stay behind). At full size, only a clear edge
 * counts, and lights are left out, so the clock is never cut where there's only sky. Photos without a plausible sky
 * give null and the clock simply sits in front. Pure, so tested.
 */
object DepthMask {
    /** Colour change allowed between neighbouring sky pixels of the small copy, and from the sky around them. */
    private const val STEP = 9f
    private const val JOIN = 30f

    /** The least colour step (summed over red, green and blue) that counts as scenery's edge against the sky. */
    private const val EDGE = 30f

    /** Bright bumps narrower than this are lights in front of the sky, not something the clock goes behind. */
    private const val LIGHT = 160

    /** How far either side of a light the line under it is fitted, in pixels. */
    private const val SIDE = 32

    /** Columns where the edge is unclear, filled in from their neighbours when the gap is at most [GAP] pixels. */
    private const val UNSURE = -1
    private const val GAP = 48

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
                // Far from the sky's colour stops the fill, unless it's the sky growing smoothly brighter: light in
                // the sky (a street lamp's glow, the sun, a lit cloud) is part of it, not in front of it.
                if (sqrt(dr * dr + dg * dg + db * db) >= JOIN && dr + dg + db < 0f) continue
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
        return Scene(w, h, solid(fg, w, h))
    }

    /**
     * [fg] without anything thinner than three pixels (lamp posts, masts, wires), and without what that leaves
     * floating (a street lamp's glow): scenery the clock can hide behind is solid and stands on the bottom edge.
     */
    private fun solid(fg: BooleanArray, w: Int, h: Int): BooleanArray {
        fun at(a: BooleanArray, x: Int, y: Int) = x in 0 until w && y in 0 until h && a[y * w + x]
        // Opening: shrink by a pixel all round, then grow back; whatever was thinner than that is gone.
        val core = BooleanArray(w * h) { i ->
            val x = i % w
            val y = i / w
            fg[i] && (-1..1).all { dy -> (-1..1).all { dx -> at(fg, x + dx, y + dy) || y + dy >= h } }
        }
        val thick = BooleanArray(w * h) { i ->
            val x = i % w
            val y = i / w
            fg[i] && (-1..1).any { dy -> (-1..1).any { dx -> at(core, x + dx, y + dy) } }
        }
        val out = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (x in 0 until w) {
            val i = (h - 1) * w + x
            if (thick[i]) {
                out[i] = true
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
                if (q < 0 || out[q] || !thick[q]) continue
                out[q] = true
                queue[tail++] = q
            }
        }
        return out
    }

    /**
     * The scenery's top edge at full size ([fw] x [fh]), each column moved to the strongest colour change near
     * where the small copy put it, so the edge lines up with the real photo to the pixel.
     */
    fun edge(scene: Scene, full: IntArray, fw: Int, fh: Int): IntArray {
        val sx = scene.w.toFloat() / fw
        val sy = fh.toFloat() / scene.h
        val reach = max(4, sy.toInt() + 2)
        fun step(x: Int, y: Int) = step(full, fw, x, y)
        val out = IntArray(fw) { x ->
            val t = scene.top[min(scene.w - 1, (x * sx).toInt())]
            if (t >= scene.h) return@IntArray fh
            val c = (t * sy).toInt()
            val y0 = max(3, c - reach)
            val y1 = min(fh - 4, c + reach)
            if (y1 < y0) return@IntArray fh
            val d = FloatArray(y1 - y0 + 1) { step(x, y0 + it) }
            val most = d.max()
            // A soft change (a cloud's edge, a lamp's glow) isn't something standing in front of the sky. Unsure
            // columns get their neighbours' edge below, when there are sure ones close by on both sides.
            if (most < EDGE) return@IntArray UNSURE
            // The top of the scenery is the first strong change from above, not the strongest (a lit window).
            y0 + d.indexOfFirst { it >= most * 0.6f }
        }
        return median(lightsGone(filled(out, fh, GAP), full, fw, fh), 5)
    }

    /** The colour change across row [y] of column [x], three rows above against three below: a skyline is a step, grain isn't. */
    private fun step(full: IntArray, fw: Int, x: Int, y: Int): Float {
        var d = 0f
        for (k in 1..3) {
            val up = full[(y - k) * fw + x]
            val dn = full[(y + k) * fw + x]
            d += abs(r(up) - r(dn)) + abs(g(up) - g(dn)) + abs(b(up) - b(dn))
        }
        return d / 3
    }

    /** Unsure columns: in a gap narrower than [gap] between sure ones, the edge runs straight across; else no scenery. */
    private fun filled(edge: IntArray, fh: Int, gap: Int): IntArray {
        val res = edge.copyOf()
        var i = 0
        while (i < res.size) {
            if (res[i] != UNSURE) {
                i++
                continue
            }
            var j = i
            while (j < res.size && res[j] == UNSURE) j++
            val left = if (i > 0) res[i - 1] else fh
            val right = if (j < res.size) res[j] else fh
            for (k in i until j) {
                res[k] = if (j - i <= gap && left < fh && right < fh) left + (right - left) * (k - i + 1) / (j - i + 1) else fh
            }
            i = j
        }
        return res
    }

    /**
     * [edge] without lights: a bump narrower than [LIGHT] pixels with something glaring in it, or brighter than the
     * sky above it (a street lamp and its glow), goes; under it the edge follows what it stands on. Dark bumps (a
     * peak, a chimney) stay.
     */
    private fun lightsGone(edge: IntArray, full: IntArray, fw: Int, fh: Int): IntArray {
        val flat = narrowGone(edge, fh, LIGHT)
        fun lum(x: Int, y0: Int, y1: Int): Float {
            var sum = 0f
            var n = 0
            for (y in max(0, y0) until min(fh, y1)) {
                val p = full[y * fw + x]
                sum += r(p) * 0.3f + g(p) * 0.59f + b(p) * 0.11f
                n++
            }
            return if (n == 0) 0f else sum / n
        }
        val out = edge.copyOf()
        var i = 0
        while (i < edge.size) {
            if (flat[i] <= edge[i]) {
                i++
                continue
            }
            var j = i
            var below = 0f
            var above = 0f
            var brightest = 0f
            while (j < edge.size && flat[j] > edge[j]) {
                below += lum(j, edge[j], edge[j] + 12)
                above += lum(j, edge[j] - 60, edge[j] - 40)
                for (y in max(0, edge[j]) until min(fh, flat[j])) brightest = max(brightest, lum(j, y, y + 1))
                j++
            }
            // A light: something glaring in it (a lamp's bulb), or clearly brighter than the sky around it.
            if (brightest >= 235f || below - above > 20f * (j - i)) {
                // What the light stands on (a bridge's railing) runs on under it: a straight line fitted to the edge
                // on either side.
                val before = ((i - SIDE) until i).filter { it in edge.indices && edge[it] < fh }
                val after = (j until j + SIDE).filter { it in edge.indices && edge[it] < fh }
                val pts = before + after
                if (pts.size >= 8) {
                    val mx = pts.average()
                    val my = pts.map { edge[it] }.average()
                    val sxx = pts.sumOf { (it - mx) * (it - mx) }
                    // Level when there's only one side to go by (a light at the photo's edge).
                    val slope = if (sxx > 0 && before.isNotEmpty() && after.isNotEmpty()) pts.sumOf { (it - mx) * (edge[it] - my) } / sxx else 0.0
                    for (k in i until j) out[k] = (my + slope * (k - mx)).toInt().coerceIn(0, fh)
                } else {
                    for (k in i until j) out[k] = flat[k]
                }
            }
            i = j
        }
        return out
    }

    /** [edge] with anything rising less than [width] pixels across taken down to what's beside it. */
    private fun narrowGone(edge: IntArray, fh: Int, width: Int): IntArray {
        val half = width / 2
        val n = edge.size
        // Scenery height, then an opening: the lowest height across [width], then the highest of those.
        val height = IntArray(n) { fh - edge[it] }
        val low = IntArray(n) { i -> (max(0, i - half)..min(n - 1, i + half)).minOf { height[it] } }
        return IntArray(n) { i -> fh - (max(0, i - half)..min(n - 1, i + half)).maxOf { low[it] } }
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
     * The scenery in front of the clock: the photo's pixels inside [x], [y], [w], [h] of the full photo below [edge],
     * all of it (no holes, so nothing blocky shows through), with a soft edge. Transparent everywhere else.
     */
    fun cutout(full: IntArray, fw: Int, fh: Int, edge: IntArray, x: Int, y: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (j in 0 until h) {
            val fy = y + j
            if (fy < 0 || fy >= fh) continue
            for (i in 0 until w) {
                val fx = x + i
                if (fx < 0 || fx >= fw) continue
                // Soft by a pixel and a half, across the edge and from column to column, so it never looks cut out.
                var a = 0f
                for (k in -1..1) a += ((fy - edge[(fx + k).coerceIn(0, fw - 1)] + 1) / 2f).coerceIn(0f, 1f)
                a /= 3f
                if (a <= 0f) continue
                val p = full[fy * fw + fx]
                out[j * w + i] = ((a * 255).toInt() shl 24) or (p and 0xFFFFFF)
            }
        }
        return out
    }
}

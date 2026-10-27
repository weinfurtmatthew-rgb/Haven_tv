package app.haven.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Haven's mark: an arched doorway with the light on (DESIGN.md, "Brand mark"). Same geometry as
 * res/drawable/banner.xml, drawn from a 100-unit box whose visible part is x 12..88, y 8..100.
 */
@Composable
fun HavenMark(modifier: Modifier = Modifier, frame: Color = C.Linen, light: Color = C.Lamp) {
    Canvas(modifier) {
        val s = minOf(size.width / 76f, size.height / 92f)
        val ox = (size.width - 76f * s) / 2 - 12f * s
        val oy = (size.height - 92f * s) / 2 - 8f * s
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val arch = Path().apply {
            moveTo(p(20f, 92f).x, p(20f, 92f).y)
            lineTo(p(20f, 46f).x, p(20f, 46f).y)
            arcTo(Rect(p(20f, 16f), p(80f, 76f)), 180f, 180f, false)
            lineTo(p(80f, 92f).x, p(80f, 92f).y)
        }
        drawPath(arch, frame, style = Stroke(width = 7f * s, cap = StrokeCap.Round))
        val door = Path().apply {
            moveTo(p(38f, 92f).x, p(38f, 92f).y)
            lineTo(p(38f, 64f).x, p(38f, 64f).y)
            arcTo(Rect(p(38f, 52f), p(62f, 76f)), 180f, 180f, false)
            lineTo(p(62f, 92f).x, p(62f, 92f).y)
            close()
        }
        drawPath(door, light)
    }
}

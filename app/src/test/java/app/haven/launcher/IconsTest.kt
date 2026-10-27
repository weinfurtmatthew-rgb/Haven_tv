package app.haven.launcher

import androidx.compose.ui.graphics.vector.addPathNodes
import app.haven.launcher.ui.Icons
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconsTest {
    /** Every icon path must parse; a bad one crashed Haven at launch (build 1). */
    @Test
    fun everyIconPathParses() {
        val source = listOf("src/main/java/app/haven/launcher/ui/Icons.kt", "app/src/main/java/app/haven/launcher/ui/Icons.kt")
            .map(::File).first { it.exists() }.readText()
        val paths = Regex("^\\s+\"([^\"]+)\",$", RegexOption.MULTILINE).findAll(source).map { it.groupValues[1] }.toList()
        assertTrue("expected icon paths in Icons.kt", paths.size > 50)
        for (d in paths) {
            assertTrue("bad characters in icon path: $d", d.none { it.isLetter() && it !in "MmLlHhVvCcSsQqTtAaZzEe" })
            assertTrue("icon path parses to nothing: $d", addPathNodes(d).isNotEmpty())
        }
    }

    @Test
    fun iconSetBuilds() {
        assertTrue(Icons.House.root.size > 0)
        assertTrue(Icons.SlidersHorizontal.root.size > 0)
    }
}

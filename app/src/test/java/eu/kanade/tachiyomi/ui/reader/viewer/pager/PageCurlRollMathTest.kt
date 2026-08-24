package eu.kanade.tachiyomi.ui.reader.viewer.pager

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

/**
 * Unit tests for the orthographic cylinder roll behind the page curl.
 *
 * The canonical frame curls from the right edge. All expected values derive
 * from [PageCurlRollMath] constants, never from hard-coded column indices.
 */
class PageCurlRollMathTest {

    private val width = 1080f
    private val height = 1920f

    private val cols = PageCurlRollMath.MESH_COLS
    private val rows = PageCurlRollMath.MESH_ROWS

    private fun buildVerts(progress: Float, curlFromRight: Boolean = true): FloatArray {
        val verts = PageCurlRollMath.newVerts()
        PageCurlRollMath.buildVerts(width, height, progress, curlFromRight, verts)
        return verts
    }

    private fun index(row: Int, col: Int) = 2 * (row * (cols + 1) + col)

    private fun xAt(verts: FloatArray, row: Int, col: Int) = verts[index(row, col)]

    private fun originalX(col: Int) = width * col / cols

    private fun originalY(row: Int) = height * row / rows

    /** Counts direction changes along one row, ignoring exact plateaus. */
    private fun directionChanges(xs: List<Float>): Int {
        var changes = 0
        var previous = 0
        for (i in 1 until xs.size) {
            val d = xs[i] - xs[i - 1]
            if (abs(d) < PLATEAU_EPSILON) continue
            val sign = if (d > 0f) 1 else -1
            if (previous != 0 && sign != previous) changes++
            previous = sign
        }
        return changes
    }

    private fun rowXs(verts: FloatArray, row: Int): List<Float> =
        (0..cols).map { col -> xAt(verts, row, col) }

    @Test
    fun `buffer size matches the mesh density`() {
        val expected = (cols + 1) * (rows + 1) * 2
        PageCurlRollMath.vertCount() shouldBe expected
        PageCurlRollMath.newVerts().size shouldBe expected
    }

    @Test
    fun `radius shrinks strictly with progress`() {
        var progress = 0f
        while (progress < 1f) {
            val next = progress + 0.05f
            ;(PageCurlRollMath.radius(next) < PageCurlRollMath.radius(progress)) shouldBe true
            progress = next
        }
        PageCurlRollMath.radius(0f) shouldBe PageCurlRollMath.ROLL_RADIUS_START
        PageCurlRollMath.radius(1f) shouldBe PageCurlRollMath.ROLL_RADIUS_END
    }

    @Test
    fun `tangent line moves left strictly with progress`() {
        var progress = 0f
        while (progress < 1f) {
            val next = progress + 0.05f
            ;(
                PageCurlRollMath.tangentX(width, next) <
                    PageCurlRollMath.tangentX(width, progress)
                ) shouldBe true
            progress = next
        }
        PageCurlRollMath.tangentX(width, 0f) shouldBe width
        PageCurlRollMath.tangentX(width, 1f) shouldBe -PageCurlRollMath.ROLL_CLEARANCE * width
    }

    // A1: the wrap reverses horizontal order across the quarter turn.
    @Test
    fun `column order flips across the quarter turn at half progress`() {
        val progress = 0.5f
        val r = PageCurlRollMath.radius(progress) * width
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val spacing = width / cols
        val quarterS = (PI.toFloat() * r / 2f)

        // Wrapped columns sit at s = j * spacing past the tangent line.
        val quarterIndex = floor(quarterS / spacing).toInt()
        (quarterIndex >= 2) shouldBe true
        ((quarterIndex + 2) * spacing < width - tangent) shouldBe true

        // Below the quarter turn, later columns map further left.
        val belowA = PageCurlRollMath.rollX(tangent + (quarterIndex - 2) * spacing, width, progress)
        val belowB = PageCurlRollMath.rollX(tangent + (quarterIndex - 1) * spacing, width, progress)
        ;(belowA > belowB) shouldBe true

        // Above the quarter turn, later columns map further right again.
        val aboveA = PageCurlRollMath.rollX(tangent + (quarterIndex + 1) * spacing, width, progress)
        val aboveB = PageCurlRollMath.rollX(tangent + (quarterIndex + 2) * spacing, width, progress)
        ;(aboveA < aboveB) shouldBe true
    }

    // A2: the rolled surface compresses adjacent columns.
    @Test
    fun `wrapped gaps are strictly narrower than flat gaps`() {
        val progress = 0.5f
        val verts = buildVerts(progress)
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val spacing = width / cols

        for (row in 0..rows) {
            for (col in 0 until cols) {
                if (originalX(col) > tangent) {
                    val gap = abs(xAt(verts, row, col + 1) - xAt(verts, row, col))
                    ;(gap < spacing) shouldBe true
                } else {
                    val gap = abs(xAt(verts, row, col + 1) - xAt(verts, row, col))
                    gap shouldBe spacing
                }
            }
        }
    }

    // Audit blocker pin: the flat part of the page never moves early.
    @Test
    fun `flat columns keep their position before the tangent reaches them`() {
        for (progress in listOf(0f, 0.05f, 0.25f, 0.5f, 0.75f)) {
            val verts = buildVerts(progress)
            val tangent = PageCurlRollMath.tangentX(width, progress)
            for (row in 0..rows) {
                for (col in 0..cols) {
                    val x0 = originalX(col)
                    if (x0 <= tangent) {
                        xAt(verts, row, col) shouldBe x0
                    }
                }
            }
        }
    }

    @Test
    fun `mesh is identity at zero progress`() {
        val verts = buildVerts(0f)
        for (row in 0..rows) {
            for (col in 0..cols) {
                xAt(verts, row, col) shouldBe originalX(col)
                verts[index(row, col) + 1] shouldBe originalY(row)
            }
        }
    }

    // Discriminator against the old translate-and-lift model, which moved y.
    @Test
    fun `y coordinates never move at any progress`() {
        val reference = buildVerts(0f)
        for (progress in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1f)) {
            val verts = buildVerts(progress)
            for (i in reference.indices step 2) {
                verts[i + 1] shouldBe reference[i + 1]
            }
        }
    }

    // A3: the sweep produces visible motion throughout.
    @Test
    fun `every sampled mid progress differs from the rest mesh`() {
        val rest = buildVerts(0f)
        var progress = 0.05f
        while (progress <= 0.85f) {
            var maxDelta = 0f
            for (i in rest.indices) {
                val delta = abs(buildVerts(progress)[i] - rest[i])
                if (delta > maxDelta) maxDelta = delta
            }
            ;(maxDelta > MOTION_EPSILON) shouldBe true
            progress += 0.05f
        }
    }

    // A4: motion spans more than half a page, measured on the curling edge.
    @Test
    fun `sweep carries the curling edge more than half a page`() {
        val rest = buildVerts(0f)
        val end = buildVerts(1f)

        val edgeTravel = abs(xAt(end, 0, cols) - xAt(rest, 0, cols))
        ;(edgeTravel > 0.5f * width) shouldBe true

        // The bounding box right edge follows the same span. The left edge
        // legitimately travels less because the terminal roll keeps the sheet
        // within clearance plus radius of the origin.
        val boxRightRest = rowXs(rest, 0).max()
        val boxRightEnd = rowXs(end, 0).max()
        ;(boxRightRest - boxRightEnd > 0.5f * width) shouldBe true
    }

    // A5: one expression of the geometry, mirrored at the direction boundary.
    @Test
    fun `left curl mirrors canonical x and keeps y`() {
        val canonical = buildVerts(0.6f, curlFromRight = true)
        val mirrored = buildVerts(0.6f, curlFromRight = false)
        for (i in canonical.indices step 2) {
            mirrored[i] shouldBe width - canonical[i]
            mirrored[i + 1] shouldBe canonical[i + 1]
        }
    }

    // A6: the finished roll clears the screen on both directions.
    @Test
    fun `finished roll sits fully off screen`() {
        val tangentEnd = PageCurlRollMath.tangentX(width, 1f)
        ;(tangentEnd < 0f) shouldBe true

        val rightEnd = buildVerts(1f, curlFromRight = true)
        for (i in rightEnd.indices step 2) {
            ;(rightEnd[i] <= tangentEnd) shouldBe true
        }

        val leftEnd = buildVerts(1f, curlFromRight = false)
        for (i in leftEnd.indices step 2) {
            ;(leftEnd[i] >= width - tangentEnd) shouldBe true
        }
    }

    // A7: single reversal per row, well formed under the theta clamp.
    // At p = 1 the whole row lies past the quarter turn, so it is monotone;
    // exactly one reversal happens while the quarter turn is on screen.
    @Test
    fun `each row reverses direction at most once`() {
        for (progress in listOf(0.2f, 0.5f, 0.8f, 1f)) {
            val verts = buildVerts(progress)
            for (row in 0..rows) {
                val changes = directionChanges(rowXs(verts, row))
                ;(changes <= 1) shouldBe true
                if (progress == 0.5f) changes shouldBe 1
                if (progress == 1f) changes shouldBe 0
            }
        }
    }

    companion object {
        private const val PLATEAU_EPSILON = 1e-3f
        private const val MOTION_EPSILON = 1e-3f
    }
}

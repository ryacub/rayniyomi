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

    private fun buildVerts(progress: Float, direction: CurlDirection = CurlDirection.FROM_RIGHT): FloatArray {
        val verts = PageCurlRollMath.newVerts()
        PageCurlRollMath.buildVerts(width, height, progress, direction, verts)
        return verts
    }

    private fun index(row: Int, col: Int) = 2 * (row * (cols + 1) + col)

    private fun xAt(verts: FloatArray, row: Int, col: Int) = verts[index(row, col)]

    private fun originalX(col: Int) = width * col / cols

    private fun originalY(row: Int) = height * row / rows

    private fun colorIndex(row: Int, col: Int) = row * (cols + 1) + col

    private fun buildColors(progress: Float): IntArray {
        val colors = PageCurlRollMath.newColors()
        PageCurlRollMath.buildColors(width, progress, colors)
        return colors
    }

    private fun brightnessByte(color: Int) = color and 0xFF

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
        ;(
            abs(PageCurlRollMath.radius(1f) - PageCurlRollMath.ROLL_RADIUS_END) <
                RADIUS_EPSILON
            ) shouldBe true

        PageCurlRollMath.radius(0f) shouldBe PageCurlRollMath.ROLL_RADIUS_START
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
                val gap = abs(xAt(verts, row, col + 1) - xAt(verts, row, col))
                if (originalX(col + 1) <= tangent) {
                    gap shouldBe spacing
                } else {
                    ;(gap < spacing) shouldBe true
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
        val canonical = buildVerts(0.6f, direction = CurlDirection.FROM_RIGHT)
        val mirrored = buildVerts(0.6f, direction = CurlDirection.FROM_LEFT)
        for (i in canonical.indices step 2) {
            mirrored[i] shouldBe width - canonical[i]
            mirrored[i + 1] shouldBe canonical[i + 1]
        }
    }

    @Test
    fun `mirror x returns x unchanged for a right curl`() {
        CurlDirection.FROM_RIGHT.mirrorX(0f, width) shouldBe 0f
        CurlDirection.FROM_RIGHT.mirrorX(432f, width) shouldBe 432f
        CurlDirection.FROM_RIGHT.mirrorX(width, width) shouldBe width
    }

    @Test
    fun `mirror x reflects about the page center for a left curl`() {
        CurlDirection.FROM_LEFT.mirrorX(400f, width) shouldBe width - 400f
        CurlDirection.FROM_LEFT.mirrorX(0f, width) shouldBe width
        CurlDirection.FROM_LEFT.mirrorX(width, width) shouldBe 0f
    }

    // A6: the finished roll clears the screen on both directions.
    @Test
    fun `finished roll sits fully off screen`() {
        val tangentEnd = PageCurlRollMath.tangentX(width, 1f)
        ;(tangentEnd < 0f) shouldBe true

        val rightEnd = buildVerts(1f, direction = CurlDirection.FROM_RIGHT)
        for (i in rightEnd.indices step 2) {
            ;(rightEnd[i] <= tangentEnd) shouldBe true
        }

        val leftEnd = buildVerts(1f, direction = CurlDirection.FROM_LEFT)
        for (i in leftEnd.indices step 2) {
            ;(leftEnd[i] >= width - tangentEnd) shouldBe true
        }
    }

    @Test
    fun `fold back span is null before any sheet point rolls past vertical`() {
        PageCurlRollMath.foldBackSpan(width, 0f) shouldBe null
    }

    // A8: the strip spans the projected cylinder, tangent line down to
    // tangent minus radius.
    @Test
    fun `fold back span bounds match the projected cylinder at half progress`() {
        val progress = 0.5f
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val r = PageCurlRollMath.radius(progress) * width

        val span = PageCurlRollMath.foldBackSpan(width, progress)
        ;(abs(span!!.start - (tangent - r)) < SPAN_EPSILON) shouldBe true
        ;(abs(span.endInclusive - tangent) < SPAN_EPSILON) shouldBe true
    }

    // A9: the strip narrows strictly as the roll tightens.
    @Test
    fun `fold back span shrinks strictly with progress past onset`() {
        var previous = PageCurlRollMath.foldBackSpan(width, 0.35f)!!
        var progress = 0.4f
        while (progress <= 0.95f) {
            val current = PageCurlRollMath.foldBackSpan(width, progress)!!
            ;(current.endInclusive < previous.endInclusive) shouldBe true
            ;(current.start < previous.start) shouldBe true
            ;((current.endInclusive - current.start) < (previous.endInclusive - previous.start)) shouldBe true
            previous = current
            progress += 0.05f
        }
    }

    // A10: the onset sits where the page edge crosses vertical, derived
    // from the roll constants.
    @Test
    fun `fold back span turns on exactly at the quarter turn of the page edge`() {
        val onset = ((PI.toFloat() / 2f) * PageCurlRollMath.ROLL_RADIUS_START) /
            (
                (1f + PageCurlRollMath.ROLL_CLEARANCE) +
                    (PI.toFloat() / 2f) *
                    (PageCurlRollMath.ROLL_RADIUS_START - PageCurlRollMath.ROLL_RADIUS_END)
                )

        PageCurlRollMath.foldBackSpan(width, onset - ONSET_EPSILON) shouldBe null
        ;(PageCurlRollMath.foldBackSpan(width, onset + ONSET_EPSILON) != null) shouldBe true
    }

    // A11: by full progress the whole strip has rolled off screen.
    @Test
    fun `fold back span sits fully off screen at full progress`() {
        val span = PageCurlRollMath.foldBackSpan(width, 1f)
        ;(span != null) shouldBe true
        ;(span!!.endInclusive <= 0f) shouldBe true
    }

    // A12: pin for the fold-back sampler in PageCurlOverlayView. Both curl
    // directions sample the canonical range [tangent, tangent + radius]:
    // the right curl reflects about the crease, the left curl composes
    // that reflection with the mesh display mirror into a translation.
    // Wherever the strip is on screen, the sampled range stays inside
    // the bitmap.
    @Test
    fun `fold back sampler domain stays inside the bitmap wherever the strip is visible`() {
        var progress = 0.01f
        while (progress <= 1f) {
            val span = PageCurlRollMath.foldBackSpan(width, progress)
            if (span != null && span.endInclusive > 0f && span.start < width) {
                val r = PageCurlRollMath.radius(progress) * width
                ;(span.endInclusive + r <= width) shouldBe true
            }
            progress += 0.01f
        }
    }

    // A7: the roll stays single valued under the theta clamp. The flat run
    // folds back once at the tangent line; the wrapped segment then reverses
    // direction once across the quarter turn. At p = 1 the whole sheet lies
    // past the quarter turn, so the wrapped segment is monotone.
    @Test
    fun `wrapped segment reverses direction once at most`() {
        for (progress in listOf(0.2f, 0.5f, 0.8f, 1f)) {
            val verts = buildVerts(progress)
            val tangent = PageCurlRollMath.tangentX(width, progress)
            val firstWrapped = (0..cols).first { originalX(it) > tangent }
            for (row in 0..rows) {
                val wrapped = rowXs(verts, row).drop(firstWrapped)
                val changes = directionChanges(wrapped)
                ;(changes <= 1) shouldBe true
                if (progress == 0.5f) changes shouldBe 1
                if (progress == 1f) changes shouldBe 0
            }
        }
    }

    // Shading ------------------------------------------------------------------

    @Test
    fun `color buffer size matches the mesh density`() {
        val expected = (cols + 1) * (rows + 1)
        PageCurlRollMath.colorCount() shouldBe expected
        PageCurlRollMath.newColors().size shouldBe expected
    }

    @Test
    fun `mesh colors are unshaded at zero progress`() {
        val colors = buildColors(0f)
        for (row in 0..rows) {
            for (col in 0..cols) {
                colors[colorIndex(row, col)] shouldBe UNSHADED
            }
        }
    }

    @Test
    fun `flat columns stay unshaded at sampled progress`() {
        for (progress in listOf(0.05f, 0.25f, 0.5f, 0.75f)) {
            val colors = buildColors(progress)
            val tangent = PageCurlRollMath.tangentX(width, progress)
            for (row in 0..rows) {
                for (col in 0..cols) {
                    if (originalX(col) <= tangent) {
                        colors[colorIndex(row, col)] shouldBe UNSHADED
                    }
                }
            }
        }
    }

    @Test
    fun `wrapped brightness decreases up to the floor`() {
        val progress = 0.5f
        val colors = buildColors(progress)
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val r = PageCurlRollMath.radius(progress) * width
        val quarterTheta = PI.toFloat() / 2f

        val wrappedCols = (0..cols).filter { originalX(it) > tangent }
        val firstFloorCol =
            wrappedCols.first { (originalX(it) - tangent) / r >= quarterTheta }
        val floorColor =
            PageCurlRollMath.shadedColor(tangent + 100f * r, width, progress)

        var previous = brightnessByte(colors[colorIndex(0, wrappedCols.first())])
        for (col in wrappedCols.drop(1)) {
            if (col >= firstFloorCol) continue
            val current = brightnessByte(colors[colorIndex(0, col)])
            ;(current < previous) shouldBe true
            previous = current
        }
        for (col in wrappedCols.filter { it >= firstFloorCol }) {
            colors[colorIndex(0, col)] shouldBe floorColor
            brightnessByte(colors[colorIndex(0, col)]) shouldBe FLOOR_BRIGHTNESS
        }
    }

    @Test
    fun `shading is brightest near the fold and darkest where the surface turns away`() {
        val progress = 0.5f
        val colors = buildColors(progress)
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val wrappedCols = (0..cols).filter { originalX(it) > tangent }

        val first = brightnessByte(colors[colorIndex(0, wrappedCols.first())])
        val mid = brightnessByte(colors[colorIndex(0, wrappedCols[wrappedCols.size / 2])])
        val last = colors[colorIndex(0, wrappedCols.last())]
        ;(first > mid) shouldBe true
        last shouldBe
            PageCurlRollMath.shadedColor(tangent + 100f * PageCurlRollMath.radius(progress) * width, width, progress)
    }

    @Test
    fun `vertex colors stay opaque`() {
        for (progress in listOf(0.25f, 0.5f, 1f)) {
            val colors = buildColors(progress)
            for (color in colors) {
                ((color ushr 24) and 0xFF) shouldBe 0xFF
            }
        }
    }

    @Test
    fun `roll theta is zero before the tangent and clamps at max roll`() {
        val progress = 0.4f
        val tangent = PageCurlRollMath.tangentX(width, progress)
        val r = PageCurlRollMath.radius(progress) * width
        PageCurlRollMath.rollTheta(tangent - 10f, width, progress) shouldBe 0f
        PageCurlRollMath.rollTheta(tangent, width, progress) shouldBe 0f
        PageCurlRollMath.rollTheta(tangent + r, width, progress) shouldBe 1f
        PageCurlRollMath.rollTheta(tangent + 10f * r, width, progress) shouldBe
            PageCurlRollMath.MAX_ROLL_ANGLE
    }

    @Test
    fun `color rows follow the original column layout`() {
        val progress = 0.5f
        val colors = buildColors(progress)
        val row = rows / 2
        for (col in 0..cols) {
            colors[colorIndex(row, col)] shouldBe
                PageCurlRollMath.shadedColor(originalX(col), width, progress)
        }
    }

    // Cast shadow --------------------------------------------------------------

    @Test
    fun `cast shadow fades as the fold lifts`() {
        PageCurlRollMath.castShadowAlpha(0f) shouldBe PageCurlRollMath.CAST_SHADOW_MAX_ALPHA
        var previous = PageCurlRollMath.castShadowAlpha(0f)
        var progress = 0.05f
        while (progress < 1f) {
            val alpha = PageCurlRollMath.castShadowAlpha(progress)
            ;(alpha <= previous) shouldBe true
            previous = alpha
            progress += 0.05f
        }
        PageCurlRollMath.castShadowAlpha(1f) shouldBe 0
    }
    companion object {
        private const val PLATEAU_EPSILON = 1e-3f
        private const val MOTION_EPSILON = 1e-3f
        private const val RADIUS_EPSILON = 1e-6f
        private const val SPAN_EPSILON = 1e-3f
        private const val ONSET_EPSILON = 1e-4f

        private const val UNSHADED = 0xFFFFFFFF.toInt()

        // round(SHADING_MIN_BRIGHTNESS * 255).
        private const val FLOOR_BRIGHTNESS = 64
    }
}

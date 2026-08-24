package eu.kanade.tachiyomi.ui.reader.viewer.pager

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure vertex math for the orthographic cylinder roll behind the page curl.
 *
 * The page wraps around a cylinder whose axis is parallel to the page's
 * vertical edge. The cylinder rolls off the curling edge. The canonical
 * frame curls from the right edge; [buildVerts] mirrors the result for a
 * left curl. The projection is orthographic, so the roll depth never
 * affects the output: only x moves, and y stays on its row line.
 */
internal object PageCurlRollMath {

    const val MESH_COLS = 48
    const val MESH_ROWS = 8

    // The cylinder radius as a fraction of the page width. It starts wide for
    // a gentle lift-off and ends tight for a visible terminal roll.
    const val ROLL_RADIUS_START = 0.35f
    const val ROLL_RADIUS_END = 0.10f

    const val MAX_ROLL_ANGLE: Float = PI.toFloat()

    // How far the tangent line travels past the far edge by full progress,
    // as a fraction of the page width. It exceeds the terminal radius, so
    // the whole mesh sits strictly off screen at the end.
    const val ROLL_CLEARANCE = 0.25f

    fun vertCount(): Int = (MESH_COLS + 1) * (MESH_ROWS + 1) * 2

    fun newVerts(): FloatArray = FloatArray(vertCount())

    /** Cylinder radius as a fraction of the page width at [progress]. */
    fun radius(progress: Float): Float =
        ROLL_RADIUS_START + (ROLL_RADIUS_END - ROLL_RADIUS_START) * progress

    /**
     * X position of the tangent line at [progress]. Columns at or left of it
     * stay flat; columns right of it wrap around the cylinder.
     */
    fun tangentX(pageWidth: Float, progress: Float): Float =
        pageWidth - (1f + ROLL_CLEARANCE) * pageWidth * progress

    /** Maps one original x position to its rolled position. */
    fun rollX(x0: Float, pageWidth: Float, progress: Float): Float {
        val tangent = tangentX(pageWidth, progress)

        // Flat branch: the sheet never moves before the tangent crosses it.
        if (x0 <= tangent) return x0

        val r = radius(progress) * pageWidth
        val theta = min((x0 - tangent) / r, MAX_ROLL_ANGLE)
        return tangent - r * sin(theta)
    }

    /**
     * Fills [verts] with the mesh positions at [progress] for a page of the
     * given size. The layout matches drawBitmapMesh for [MESH_COLS] by
     * [MESH_ROWS]: row-major, x then y per vertex.
     */
    fun buildVerts(
        pageWidth: Float,
        pageHeight: Float,
        progress: Float,
        curlFromRight: Boolean,
        verts: FloatArray,
    ) {
        var index = 0
        for (row in 0..MESH_ROWS) {
            val y = pageHeight * row / MESH_ROWS
            for (col in 0..MESH_COLS) {
                val canonicalX = rollX(pageWidth * col / MESH_COLS, pageWidth, progress)
                verts[index] = if (curlFromRight) canonicalX else pageWidth - canonicalX
                verts[index + 1] = y
                index += 2
            }
        }
    }
}

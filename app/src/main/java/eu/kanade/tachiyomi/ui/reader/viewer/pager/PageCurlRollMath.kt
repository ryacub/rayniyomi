package eu.kanade.tachiyomi.ui.reader.viewer.pager

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure vertex math for the orthographic cylinder roll behind the page curl.
 *
 * The page wraps around a cylinder whose axis is parallel to the page's
 * vertical edge. The cylinder rolls off the curling edge. The canonical
 * frame curls from the right edge; [buildVerts] mirrors the source and result
 * for a left curl. The projection is orthographic, so the roll depth never
 * affects the output: only x moves, and y stays on its row line.
 */
internal enum class FoldBackTransform {
    MIRROR,
    TRANSLATE,
}

internal data class FoldBackDrawing(
    val clipSpan: ClosedFloatingPointRange<Float>,
    val creaseX: Float,
    val transform: FoldBackTransform,
)

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

    fun colorCount(): Int = (MESH_COLS + 1) * (MESH_ROWS + 1)

    fun newColors(): IntArray = IntArray(colorCount())

    // Unit vector from the surface toward the light. Frontal key light by
    // default; normals lie in the x-z plane, so only x and z contribute.
    const val LIGHT_DIRECTION_X = 0f
    const val LIGHT_DIRECTION_Y = 0f
    const val LIGHT_DIRECTION_Z = -1f

    // Darkest allowed vertex tint as a fraction of full brightness.
    const val SHADING_MIN_BRIGHTNESS = 0.25f

    // Cast shadow on the incoming page: band width as a fraction of the page
    // width and peak black alpha at lift-off.
    const val CAST_SHADOW_WIDTH_FRACTION = 0.06f
    const val CAST_SHADOW_MAX_ALPHA = 51

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

    /** Roll angle for original [x0]: 0 while flat, clamped to [MAX_ROLL_ANGLE]. */
    fun rollTheta(x0: Float, pageWidth: Float, progress: Float): Float {
        val tangent = tangentX(pageWidth, progress)
        if (x0 <= tangent) return 0f
        return min((x0 - tangent) / (radius(progress) * pageWidth), MAX_ROLL_ANGLE)
    }

    /**
     * Lambert brightness of the outer surface at [theta], remapped into
     * [SHADING_MIN_BRIGHTNESS, 1].
     */
    fun shadeFraction(theta: Float): Float {
        val nx = -sin(theta)
        val nz = -cos(theta)
        val lambert = (nx * LIGHT_DIRECTION_X + nz * LIGHT_DIRECTION_Z).coerceIn(0f, 1f)
        return SHADING_MIN_BRIGHTNESS + (1f - SHADING_MIN_BRIGHTNESS) * lambert
    }

    /** Opaque gray modulation color for original [x0] at [progress]. */
    fun shadedColor(x0: Float, pageWidth: Float, progress: Float): Int {
        val b = (shadeFraction(rollTheta(x0, pageWidth, progress)) * 255f)
            .roundToInt()
            .coerceIn(0, 255)
        return (255 shl 24) or (b shl 16) or (b shl 8) or b
    }

    /** Fills [colors] row-major, matching buildVerts vertex order. */
    fun buildColors(
        pageWidth: Float,
        progress: Float,
        direction: CurlDirection,
        colors: IntArray,
    ) {
        var i = 0
        for (row in 0..MESH_ROWS) {
            for (col in 0..MESH_COLS) {
                val sourceX = pageWidth * col / MESH_COLS
                colors[i++] = shadedColor(
                    direction.mirrorX(sourceX, pageWidth),
                    pageWidth,
                    progress,
                )
            }
        }
    }

    /** Black alpha (0..255) of the cast shadow at [progress]. */
    fun castShadowAlpha(progress: Float): Int =
        (CAST_SHADOW_MAX_ALPHA * (1f - progress)).roundToInt().coerceIn(0, CAST_SHADOW_MAX_ALPHA)

    /**
     * Canonical right-curl span of the folded-back strip at [progress], or
     * null while no sheet point has rolled past vertical. [progress] must
     * lie in [0, 1].
     *
     * Sheet points beyond a quarter turn face away from the viewer and
     * project, orthographically, onto `[tangent - r, tangent]`. The back of
     * the page is drawn there. Use [start] and [endInclusive] on the result.
     */
    fun foldBackSpan(pageWidth: Float, progress: Float): ClosedFloatingPointRange<Float>? {
        val tangent = tangentX(pageWidth, progress)
        val r = radius(progress) * pageWidth
        if (tangent + (MAX_ROLL_ANGLE / 2f) * r >= pageWidth) return null
        return (tangent - r)..tangent
    }

    /**
     * Screen-space span of the folded-back strip at [progress] for
     * [direction], or null while no sheet point has rolled past vertical.
     *
     * The two-argument form returns the canonical right-curl span. This
     * form mirrors it for a left curl and sorts the endpoints. Every
     * caller that draws on screen must use this form.
     */
    fun foldBackSpan(
        pageWidth: Float,
        progress: Float,
        direction: CurlDirection,
    ): ClosedFloatingPointRange<Float>? {
        val canonical = foldBackSpan(pageWidth, progress) ?: return null
        return direction.mirrorSpan(canonical, pageWidth)
    }

    /** Returns the visible clip and transform decision for the folded page back. */
    fun foldBackDrawing(
        pageWidth: Float,
        progress: Float,
        direction: CurlDirection,
    ): FoldBackDrawing? {
        val span = foldBackSpan(pageWidth, progress, direction) ?: return null
        if (span.endInclusive <= 0f || span.start >= pageWidth) return null

        return when (direction) {
            CurlDirection.FROM_RIGHT -> FoldBackDrawing(
                clipSpan = span,
                creaseX = span.endInclusive,
                transform = FoldBackTransform.MIRROR,
            )
            CurlDirection.FROM_LEFT -> FoldBackDrawing(
                clipSpan = span,
                creaseX = span.start,
                transform = FoldBackTransform.MIRROR,
            )
        }
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
        direction: CurlDirection,
        verts: FloatArray,
    ) {
        var index = 0
        for (row in 0..MESH_ROWS) {
            val y = pageHeight * row / MESH_ROWS
            for (col in 0..MESH_COLS) {
                val sourceX = pageWidth * col / MESH_COLS
                val canonicalX = rollX(
                    direction.mirrorX(sourceX, pageWidth),
                    pageWidth,
                    progress,
                )
                verts[index] = direction.mirrorX(canonicalX, pageWidth)
                verts[index + 1] = y
                index += 2
            }
        }
    }
}

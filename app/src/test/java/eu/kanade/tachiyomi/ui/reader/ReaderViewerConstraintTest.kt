package eu.kanade.tachiyomi.ui.reader

import eu.kanade.presentation.util.FoldBounds
import eu.kanade.presentation.util.FoldOcclusionType
import eu.kanade.presentation.util.FoldOrientation
import eu.kanade.presentation.util.FoldState
import eu.kanade.presentation.util.ReaderFoldState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderViewerConstraintTest {

    @Test
    fun `is in tabletop posture when horizontal half-open fully occluding`() {
        isInTabletopPosture(tabletopFold()) shouldBe true
    }

    @Test
    fun `is not in tabletop posture when the fold is vertical`() {
        val fold = tabletopFold().copy(orientation = FoldOrientation.Vertical)

        isInTabletopPosture(fold) shouldBe false
    }

    @Test
    fun `is not in tabletop posture when the device is flat`() {
        val fold = tabletopFold().copy(state = FoldState.Flat)

        isInTabletopPosture(fold) shouldBe false
    }

    @Test
    fun `is not in tabletop posture when the fold does not occlude`() {
        val fold = tabletopFold().copy(occlusionType = FoldOcclusionType.None)

        isInTabletopPosture(fold) shouldBe false
    }

    @Test
    fun `is not in tabletop posture when there is no fold`() {
        isInTabletopPosture(null) shouldBe false
    }

    @Test
    fun `heights to the upper region above the fold minus the status bar`() {
        val fold = tabletopFold(top = 720)

        tabletopViewerHeight(fold, statusBarInset = 120) shouldBe 600
    }

    @Test
    fun `returns null when not in tabletop posture`() {
        tabletopViewerHeight(tabletopFold().copy(state = FoldState.Flat), statusBarInset = 120)
            .shouldBeNull()
    }

    @Test
    fun `coerces a negative height to zero when the status bar is larger than the fold`() {
        val fold = tabletopFold(top = 50)

        tabletopViewerHeight(fold, statusBarInset = 120) shouldBe 0
    }

    @Test
    fun `is in book posture with occluding hinge when vertical half-open fully occluding`() {
        isInBookPostureWithOccludingHinge(verticalHingeFold()) shouldBe true
    }

    @Test
    fun `is not in book posture with occluding hinge when the fold is horizontal`() {
        val fold = verticalHingeFold().copy(orientation = FoldOrientation.Horizontal)

        isInBookPostureWithOccludingHinge(fold) shouldBe false
    }

    @Test
    fun `is not in book posture with occluding hinge when the device is flat`() {
        val fold = verticalHingeFold().copy(state = FoldState.Flat)

        isInBookPostureWithOccludingHinge(fold) shouldBe false
    }

    @Test
    fun `is not in book posture with occluding hinge when the fold does not occlude`() {
        val fold = verticalHingeFold().copy(occlusionType = FoldOcclusionType.None)

        isInBookPostureWithOccludingHinge(fold) shouldBe false
    }

    @Test
    fun `is not in book posture with occluding hinge when there is no fold`() {
        isInBookPostureWithOccludingHinge(null) shouldBe false
    }

    @Test
    fun `insets to the regions on both sides of a mid-screen vertical hinge`() {
        val fold = verticalHingeFold(left = 700, right = 740)

        verticalHingeInsets(fold, windowWidth = 1440) shouldBe HingeInsets(left = 700, right = 700)
    }

    @Test
    fun `insets to the regions when the hinge is off-center`() {
        val fold = verticalHingeFold(left = 400, right = 440)

        verticalHingeInsets(fold, windowWidth = 1440) shouldBe HingeInsets(left = 400, right = 1000)
    }

    @Test
    fun `returns null when not in book posture with occluding hinge`() {
        verticalHingeInsets(
            verticalHingeFold().copy(occlusionType = FoldOcclusionType.None),
            windowWidth = 1440,
        ).shouldBeNull()
    }

    @Test
    fun `returns null for a flat vertical fold so no blank space is added`() {
        verticalHingeInsets(
            verticalHingeFold().copy(state = FoldState.Flat),
            windowWidth = 1440,
        ).shouldBeNull()
    }

    @Test
    fun `returns null when there is no fold`() {
        verticalHingeInsets(null, windowWidth = 1440).shouldBeNull()
    }

    @Test
    fun `coerces negative raw values to zero at both window edges`() {
        val fold = verticalHingeFold(left = -50, right = 1490)

        verticalHingeInsets(fold, windowWidth = 1440) shouldBe HingeInsets(left = 0, right = 0)
    }

    @Test
    fun `places the page in the left region when both regions are equal`() {
        val fold = verticalHingeFold(left = 700, right = 740)

        verticalViewerMargins(fold, windowWidth = 1440) shouldBe ViewerMargins(left = 0, right = 740)
    }

    @Test
    fun `places the page in the right region when it is the larger one`() {
        val fold = verticalHingeFold(left = 400, right = 440)

        verticalViewerMargins(fold, windowWidth = 1440) shouldBe ViewerMargins(left = 440, right = 0)
    }

    @Test
    fun `places the page in the left region when it is the larger one`() {
        val fold = verticalHingeFold(left = 1000, right = 1040)

        verticalViewerMargins(fold, windowWidth = 1440) shouldBe ViewerMargins(left = 0, right = 440)
    }

    @Test
    fun `returns null for the viewer margins when the fold does not occlude`() {
        verticalViewerMargins(
            verticalHingeFold().copy(occlusionType = FoldOcclusionType.None),
            windowWidth = 1440,
        ).shouldBeNull()
    }

    @Test
    fun `returns null for the viewer margins when there is no fold`() {
        verticalViewerMargins(null, windowWidth = 1440).shouldBeNull()
    }

    private fun tabletopFold(top: Int = 720): ReaderFoldState = ReaderFoldState(
        orientation = FoldOrientation.Horizontal,
        state = FoldState.HalfOpen,
        occlusionType = FoldOcclusionType.Full,
        bounds = FoldBounds(left = 0, top = top, right = 1440, bottom = top + 10),
    )

    private fun verticalHingeFold(
        left: Int = 700,
        right: Int = 740,
        state: FoldState = FoldState.HalfOpen,
    ): ReaderFoldState = ReaderFoldState(
        orientation = FoldOrientation.Vertical,
        state = state,
        occlusionType = FoldOcclusionType.Full,
        bounds = FoldBounds(left = left, top = 0, right = right, bottom = 1440),
    )
}

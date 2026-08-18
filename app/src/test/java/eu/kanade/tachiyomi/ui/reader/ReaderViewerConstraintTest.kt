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

    private fun tabletopFold(top: Int = 720): ReaderFoldState = ReaderFoldState(
        orientation = FoldOrientation.Horizontal,
        state = FoldState.HalfOpen,
        occlusionType = FoldOcclusionType.Full,
        bounds = FoldBounds(left = 0, top = top, right = 1440, bottom = top + 10),
    )
}

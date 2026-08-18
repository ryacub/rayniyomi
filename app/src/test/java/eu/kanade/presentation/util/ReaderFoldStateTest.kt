package eu.kanade.presentation.util

import android.graphics.Rect
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ReaderFoldStateTest {

    @Test
    fun `maps a vertical flat fold that does not occlude`() {
        val fold = fakeFold(
            orientation = FoldingFeature.Orientation.VERTICAL,
            state = FoldingFeature.State.FLAT,
            occlusionType = FoldingFeature.OcclusionType.NONE,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(fold)))

        result?.orientation shouldBe FoldOrientation.Vertical
        result?.state shouldBe FoldState.Flat
        result?.occlusionType shouldBe FoldOcclusionType.None
    }

    @Test
    fun `maps a horizontal flat fold that does not occlude`() {
        val fold = fakeFold(
            orientation = FoldingFeature.Orientation.HORIZONTAL,
            state = FoldingFeature.State.FLAT,
            occlusionType = FoldingFeature.OcclusionType.NONE,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(fold)))

        result?.orientation shouldBe FoldOrientation.Horizontal
    }

    @Test
    fun `maps a vertical half-open fold that occludes fully`() {
        val fold = fakeFold(
            orientation = FoldingFeature.Orientation.VERTICAL,
            state = FoldingFeature.State.HALF_OPENED,
            occlusionType = FoldingFeature.OcclusionType.FULL,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(fold)))

        result?.state shouldBe FoldState.HalfOpen
        result?.occlusionType shouldBe FoldOcclusionType.Full
    }

    @Test
    fun `maps a horizontal half-open fold that occludes fully`() {
        val fold = fakeFold(
            orientation = FoldingFeature.Orientation.HORIZONTAL,
            state = FoldingFeature.State.HALF_OPENED,
            occlusionType = FoldingFeature.OcclusionType.FULL,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(fold)))

        result?.orientation shouldBe FoldOrientation.Horizontal
        result?.state shouldBe FoldState.HalfOpen
    }

    @Test
    fun `maps the fold bounds`() {
        val fold = fakeFold(
            orientation = FoldingFeature.Orientation.VERTICAL,
            state = FoldingFeature.State.FLAT,
            occlusionType = FoldingFeature.OcclusionType.NONE,
            left = 100,
            top = 200,
            right = 500,
            bottom = 260,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(fold)))

        result?.bounds?.left shouldBe 100
        result?.bounds?.top shouldBe 200
        result?.bounds?.right shouldBe 500
        result?.bounds?.bottom shouldBe 260
        result?.bounds?.width shouldBe 400
        result?.bounds?.height shouldBe 60
    }

    @Test
    fun `returns null when the window reports no display feature`() {
        val result = readerFoldStateFrom(WindowLayoutInfo(emptyList()))

        result shouldBe null
    }

    @Test
    fun `returns null when the window reports no fold`() {
        val nonFoldFeature = mockk<DisplayFeature>()

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(nonFoldFeature)))

        result shouldBe null
    }

    @Test
    fun `maps the first fold when the window reports several`() {
        val firstFold = fakeFold(
            orientation = FoldingFeature.Orientation.VERTICAL,
            state = FoldingFeature.State.FLAT,
            occlusionType = FoldingFeature.OcclusionType.NONE,
        )
        val secondFold = fakeFold(
            orientation = FoldingFeature.Orientation.HORIZONTAL,
            state = FoldingFeature.State.HALF_OPENED,
            occlusionType = FoldingFeature.OcclusionType.FULL,
        )

        val result = readerFoldStateFrom(WindowLayoutInfo(listOf(firstFold, secondFold)))

        result?.orientation shouldBe FoldOrientation.Vertical
        result?.state shouldBe FoldState.Flat
        result?.occlusionType shouldBe FoldOcclusionType.None
        result?.bounds?.left shouldBe 0
        result?.bounds?.top shouldBe 0
        result?.bounds?.right shouldBe 1440
        result?.bounds?.bottom shouldBe 10
    }

    private fun fakeFold(
        orientation: FoldingFeature.Orientation,
        state: FoldingFeature.State,
        occlusionType: FoldingFeature.OcclusionType,
        left: Int = 0,
        top: Int = 0,
        right: Int = 1440,
        bottom: Int = 10,
    ): FoldingFeature {
        val rect = Rect()
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
        return mockk<FoldingFeature> {
            every { this@mockk.orientation } returns orientation
            every { this@mockk.state } returns state
            every { this@mockk.occlusionType } returns occlusionType
            every { bounds } returns rect
        }
    }
}

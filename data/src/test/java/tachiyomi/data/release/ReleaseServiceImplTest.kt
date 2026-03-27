package tachiyomi.data.release

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class ReleaseServiceImplTest {

    @Test
    fun `parsePublishedAt returns null when value is null`() {
        ReleaseServiceImpl.parsePublishedAt(null).shouldBeNull()
    }

    @Test
    fun `parsePublishedAt returns null when value is malformed`() {
        ReleaseServiceImpl.parsePublishedAt("not-a-date").shouldBeNull()
    }

    @Test
    fun `parsePublishedAt returns epoch millis when value is valid ISO instant`() {
        val instant = "2026-03-27T05:20:04Z"

        ReleaseServiceImpl.parsePublishedAt(instant) shouldBe Instant.parse(instant).toEpochMilli()
    }
}

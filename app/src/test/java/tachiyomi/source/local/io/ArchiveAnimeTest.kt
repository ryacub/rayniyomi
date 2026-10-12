package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ArchiveAnimeTest {

    @ParameterizedTest
    @ValueSource(strings = ["Episode 01.MKV", "ep.Mp4", "clip.WebM"])
    fun `video extensions match without regard to case`(name: String) {
        val file = mockk<UniFile> { every { this@mockk.name } returns name }

        assertTrue(ArchiveAnime.isSupported(file), "$name must be a supported episode file")
    }
}

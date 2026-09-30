package tachiyomi.domain.entries.manga.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Voyager saves a screen that holds a Manga, for example the migration search
 * screen, with Java serialization when the app goes to the background.
 * JsonObject is not java.io.Serializable, so the memo must not break that
 * save (Mihon #3523, R1079).
 */
class MangaJavaSerializationTest {

    @Test
    fun `a manga with a memo survives Java serialization`() {
        val manga = Manga.create().copy(
            id = 7,
            url = "/series/1",
            title = "Series",
            genre = listOf("Action", "Drama"),
            memo = JsonObject(mapOf("slug" to JsonPrimitive("series-1a2b"))),
        )

        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(manga) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }

        assertEquals(manga, restored)
    }
}

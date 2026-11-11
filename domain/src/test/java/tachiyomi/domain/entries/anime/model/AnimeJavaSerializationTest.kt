package tachiyomi.domain.entries.anime.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class AnimeJavaSerializationTest {

    @Test
    fun `an anime with source memo survives screen restoration`() {
        val anime = Anime.create().copy(
            title = "Anime",
            memo = JsonObject(mapOf("cursor" to JsonPrimitive(7))),
        )
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(anime) }

        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }

        assertEquals(anime, restored)
    }
}

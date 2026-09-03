package eu.kanade.tachiyomi.crash

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GlobalExceptionHandlerTest {

    @Test
    fun `deserializeThrowable returns null when the serialized extra is absent`() {
        assertNull(GlobalExceptionHandler.deserializeThrowable(null))
    }

    @Test
    fun `deserializeThrowable returns null when the serialized extra is malformed`() {
        assertNull(GlobalExceptionHandler.deserializeThrowable("not-valid-json"))
    }

    @Test
    fun `deserializeThrowable returns a throwable for a serialized throwable`() {
        val serialized = Json.encodeToString(
            GlobalExceptionHandler.ThrowableSerializer,
            RuntimeException("boom"),
        )
        assertNotNull(GlobalExceptionHandler.deserializeThrowable(serialized))
    }
}

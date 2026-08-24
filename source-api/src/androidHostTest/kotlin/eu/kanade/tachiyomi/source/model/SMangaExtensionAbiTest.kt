package eu.kanade.tachiyomi.source.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

/**
 * An extension built against a newer SManga links against members this app
 * version did not ship. Issue #979: the extension called getMemo(), Android
 * raised NoSuchMethodError, and the containment guard could only report the
 * failure -- the details fetch stayed broken. This test resolves the member
 * the way extension bytecode does at run time.
 */
class SMangaExtensionAbiTest {

    @Test
    fun `an extension compiled against a newer SManga resolves getMemo`() {
        // Same lookup invoke-interface performs: fails before the fix.
        val method = SManga::class.java.getMethod("getMemo")

        method.returnType shouldBe JsonObject::class.java

        // The resolved member answers on instances the app hands to sources,
        // and the value is usable without a null check because newer
        // extensions compile against a non-null return.
        val memo = method.invoke(SManga.create())
        memo shouldBe JsonObject(emptyMap())
    }
}

package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class HosterExtensionAbiTest {

    private val defaultConstructorMarker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    @Test
    fun `constructor with memo and defaults resolves for extension bytecode`() {
        val expected = listOf(
            String::class.java,
            String::class.java,
            List::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            JsonObject::class.java,
            Int::class.javaPrimitiveType,
            defaultConstructorMarker,
        )

        Hoster::class.java.declaredConstructors
            .any { it.parameterTypes.toList() == expected } shouldBe true
    }

    @Test
    fun `memo property getter exists and returns JsonObject`() {
        Hoster::class.java.declaredMethods
            .any { it.name == "getMemo" && it.returnType == JsonObject::class.java } shouldBe true
    }

    @Test
    fun `copy with memo exists`() {
        val expected = listOf(
            String::class.java,
            String::class.java,
            List::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            JsonObject::class.java,
        )

        Hoster::class.java.declaredMethods
            .any { it.name == "copy" && it.parameterTypes.toList() == expected } shouldBe true
    }
}

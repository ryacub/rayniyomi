package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class VideoExtensionAbiTest {

    private val defaultConstructorMarker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    @Test
    fun `constructor with memo and defaults resolves for extension bytecode`() {
        Video::class.java.declaredConstructors
            .any { ctor ->
                ctor.parameterTypes.size == 17 &&
                    ctor.parameterTypes[14] == JsonObject::class.java &&
                    ctor.parameterTypes.last() == defaultConstructorMarker
            } shouldBe true
    }

    @Test
    fun `memo property getter exists and returns JsonObject`() {
        Video::class.java.declaredMethods
            .any { it.name == "getMemo" && it.returnType == JsonObject::class.java } shouldBe true
    }
}

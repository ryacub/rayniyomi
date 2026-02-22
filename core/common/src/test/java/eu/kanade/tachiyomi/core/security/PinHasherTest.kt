package eu.kanade.tachiyomi.core.security

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class PinHasherTest {

    @Test
    fun `hash produces different output for different salts`() {
        val pin = "1234"
        val salt1 = PinHasher.generateSalt()
        val salt2 = PinHasher.generateSalt()

        val hash1 = PinHasher.hash(pin, salt1)
        val hash2 = PinHasher.hash(pin, salt2)

        hash1 shouldNotBe hash2
    }

    @Test
    fun `hash produces consistent output for same inputs`() {
        val pin = "1234"
        val salt = PinHasher.generateSalt()

        val hash1 = PinHasher.hash(pin, salt)
        val hash2 = PinHasher.hash(pin, salt)

        hash1 shouldBe hash2
    }

    @Test
    fun `verify returns true for correct PIN`() {
        val pin = "1234"
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)

        val result = PinHasher.verify(pin, hash, salt)

        result shouldBe true
    }

    @Test
    fun `verify returns false for incorrect PIN`() {
        val correctPin = "1234"
        val incorrectPin = "5678"
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(correctPin, salt)

        val result = PinHasher.verify(incorrectPin, hash, salt)

        result shouldBe false
    }

    @Test
    fun `hash handles empty PIN`() {
        val pin = ""
        val salt = PinHasher.generateSalt()

        val hash = PinHasher.hash(pin, salt)
        val verified = PinHasher.verify(pin, hash, salt)

        verified shouldBe true
    }

    @Test
    fun `generateSalt produces 32-byte array`() {
        val salt = PinHasher.generateSalt()

        salt.size shouldBe 32
    }

    @Test
    fun `generateSalt produces different salts`() {
        val salt1 = PinHasher.generateSalt()
        val salt2 = PinHasher.generateSalt()

        salt1.contentEquals(salt2) shouldBe false
    }
}

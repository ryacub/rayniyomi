package eu.kanade.tachiyomi.core.security

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class LockoutPolicyTest {

    @Test
    fun `attempts 1 to 3 return Allowed`() {
        for (attempt in 1..3) {
            val result = LockoutPolicy.calculateLockout(attempt)
            result shouldBe LockoutState.Allowed
        }
    }

    @Test
    fun `attempt 4 returns LockedOut with 30 seconds`() {
        val result = LockoutPolicy.calculateLockout(4)
        result.shouldBeInstanceOf<LockoutState.LockedOut>()
        (result as LockoutState.LockedOut).durationMillis shouldBe 30_000L
    }

    @Test
    fun `attempts 5 to 6 return LockedOut with 30 seconds`() {
        for (attempt in 5..6) {
            val result = LockoutPolicy.calculateLockout(attempt)
            result.shouldBeInstanceOf<LockoutState.LockedOut>()
            (result as LockoutState.LockedOut).durationMillis shouldBe 30_000L
        }
    }

    @Test
    fun `attempt 7 returns LockedOut with 5 minutes`() {
        val result = LockoutPolicy.calculateLockout(7)
        result.shouldBeInstanceOf<LockoutState.LockedOut>()
        (result as LockoutState.LockedOut).durationMillis shouldBe 300_000L
    }

    @Test
    fun `attempts 8 to 9 return LockedOut with 5 minutes`() {
        for (attempt in 8..9) {
            val result = LockoutPolicy.calculateLockout(attempt)
            result.shouldBeInstanceOf<LockoutState.LockedOut>()
            (result as LockoutState.LockedOut).durationMillis shouldBe 300_000L
        }
    }

    @Test
    fun `attempt 10 and above return CloseApp`() {
        for (attempt in 10..15) {
            val result = LockoutPolicy.calculateLockout(attempt)
            result shouldBe LockoutState.CloseApp
        }
    }

    @Test
    fun `calculateRemainingSeconds returns correct value for future lockout`() {
        val lockoutUntil = System.currentTimeMillis() + 10_000 // 10 seconds in future
        val remaining = LockoutPolicy.calculateRemainingSeconds(lockoutUntil)
        (remaining in 9..10) shouldBe true
    }

    @Test
    fun `calculateRemainingSeconds returns 0 for past lockout`() {
        val lockoutUntil = System.currentTimeMillis() - 1000 // 1 second in past
        val remaining = LockoutPolicy.calculateRemainingSeconds(lockoutUntil)
        remaining shouldBe 0
    }

    @Test
    fun `isLockedOut returns true when lockout is in future`() {
        val lockoutUntil = System.currentTimeMillis() + 5_000
        LockoutPolicy.isLockedOut(lockoutUntil) shouldBe true
    }

    @Test
    fun `isLockedOut returns false when lockout is in past`() {
        val lockoutUntil = System.currentTimeMillis() - 1_000
        LockoutPolicy.isLockedOut(lockoutUntil) shouldBe false
    }

    @Test
    fun `isLockedOut returns false when lockoutUntil is 0`() {
        LockoutPolicy.isLockedOut(0L) shouldBe false
    }
}

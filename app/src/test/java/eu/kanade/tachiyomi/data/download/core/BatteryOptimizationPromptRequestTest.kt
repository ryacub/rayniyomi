package eu.kanade.tachiyomi.data.download.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatteryOptimizationPromptRequestTest {

    @Test
    fun `can instantiate BatteryOptimizationPromptRequest`() {
        val request = BatteryOptimizationPromptRequest()
        assertEquals(BatteryOptimizationPromptRequest(), request)
    }

    @Test
    fun `two instances are equal`() {
        val request1 = BatteryOptimizationPromptRequest()
        val request2 = BatteryOptimizationPromptRequest()
        request1 shouldBe request2
    }

    @Test
    fun `instance equals itself`() {
        val request = BatteryOptimizationPromptRequest()
        request shouldBe request
    }

    @Test
    fun `hashCode is consistent for equal instances`() {
        val request1 = BatteryOptimizationPromptRequest()
        val request2 = BatteryOptimizationPromptRequest()
        request1.hashCode() shouldBe request2.hashCode()
    }

    @Test
    fun `can be used as Flow emission type`() {
        val request1 = BatteryOptimizationPromptRequest()
        val request2 = BatteryOptimizationPromptRequest()
        val flowValue: BatteryOptimizationPromptRequest = request1

        flowValue shouldBe request2
    }

    @Test
    fun `can be used as nullable Flow item type`() {
        val request = BatteryOptimizationPromptRequest()
        val nullableFlowValue: BatteryOptimizationPromptRequest? = request

        nullableFlowValue shouldBe request
    }

    @Test
    fun `nullable reference can be null`() {
        val nullableFlowValue: BatteryOptimizationPromptRequest? = null

        nullableFlowValue shouldBe null
    }

    @Test
    fun `can be used in collections`() {
        val requests: List<BatteryOptimizationPromptRequest> = listOf(
            BatteryOptimizationPromptRequest(),
            BatteryOptimizationPromptRequest(),
        )

        requests[0] shouldBe requests[1]
        requests.size shouldBe 2
    }

    @Test
    fun `toString is consistent`() {
        val request1 = BatteryOptimizationPromptRequest()
        val request2 = BatteryOptimizationPromptRequest()

        request1.toString() shouldBe request2.toString()
    }
}

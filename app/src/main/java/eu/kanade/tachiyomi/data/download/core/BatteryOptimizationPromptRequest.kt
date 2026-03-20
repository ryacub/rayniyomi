package eu.kanade.tachiyomi.data.download.core

class BatteryOptimizationPromptRequest {
    override fun equals(other: Any?) = other is BatteryOptimizationPromptRequest

    override fun hashCode() = this::class.hashCode()

    override fun toString() = "BatteryOptimizationPromptRequest()"
}
